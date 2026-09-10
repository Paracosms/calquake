package io.github.paracosms.calquake.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

/** Immutable, reference-free inputs shared by Recorded and Simulated preparation. */
public record ScenarioInputs(
        EventSource eventSource,
        List<SimulationSite> sites,
        TravelTimeConfiguration travelTimeConfiguration,
        ScientificConfiguration scientificConfiguration
) {
    public ScenarioInputs {
        Objects.requireNonNull(eventSource, "eventSource cannot be null");
        Objects.requireNonNull(sites, "sites cannot be null");
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("sites cannot be empty");
        }
        sites = List.copyOf(sites);
        Set<String> ids = new HashSet<>();
        for (SimulationSite site : sites) {
            Objects.requireNonNull(site, "sites cannot contain null");
            if (!ids.add(site.id())) {
                throw new IllegalArgumentException("Duplicate simulation site id: " + site.id());
            }
        }
        Objects.requireNonNull(travelTimeConfiguration, "travelTimeConfiguration cannot be null");
        scientificConfiguration = scientificConfiguration == null
                ? ScientificConfiguration.empty() : scientificConfiguration;
    }

    public ScenarioInputs(
            EventSource eventSource,
            List<SimulationSite> sites,
            TravelTimeConfiguration travelTimeConfiguration
    ) {
        this(eventSource, sites, travelTimeConfiguration, ScientificConfiguration.empty());
    }

    public Optional<SimulationSite> findSite(String siteId) {
        if (siteId == null) {
            return Optional.empty();
        }
        return sites.stream().filter(site -> site.id().equals(siteId)).findFirst();
    }

    public SimulationSite requireSite(String siteId) {
        return findSite(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation site id: " + siteId));
    }

    /** Alias that keeps event-centric call sites concise. */
    public EventSource event() {
        return eventSource;
    }

    public static ScenarioInputs fromLegacyScenario(Scenario scenario, TravelTimeModel model) {
        Objects.requireNonNull(scenario, "scenario cannot be null");
        List<SimulationSite> sites = scenario.locations().stream()
                .map(SimulationSite::fromReferenceLocation)
                .toList();
        return new ScenarioInputs(
                EventSource.from(scenario.event()),
                sites,
                TravelTimeConfiguration.forModel(model),
                ScientificConfiguration.empty()
        );
    }

    /**
     * Future custom-scenario domain entry point. Missing geometry, mechanism and
     * site conditions receive explicit documented defaults with provenance.
     */
    public static ScenarioInputs forCustomScenario(
            EventSource source, List<SimulationSite> requestedSites, TravelTimeModel model) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(requestedSites, "requestedSites cannot be null");
        Mechanism mechanism = source.mechanism().orElse(new Mechanism(
                0.0, 0.0, 90.0, "STRIKE_SLIP", "CalQuake custom-scenario default"));
        EventSource sourceWithMechanism = new EventSource(
                source.id(), source.network(), source.title(), source.originUtc(), source.magnitude(),
                source.magnitudeType(), source.epicenter(), source.depthKm(), source.ruptureGeometry(),
                Optional.of(mechanism), source.metadata());
        RuptureGeometry rupture = sourceWithMechanism.ruptureGeometry()
                .orElseGet(() -> RuptureGeometryProvider.generate(sourceWithMechanism));
        EventSource completedSource = new EventSource(
                source.id(), source.network(), source.title(), source.originUtc(), source.magnitude(),
                source.magnitudeType(), source.epicenter(), source.depthKm(), Optional.of(rupture),
                Optional.of(mechanism), source.metadata());
        List<SimulationSite> completedSites = requestedSites.stream()
                .map(site -> site.siteCondition().isPresent() ? site : new SimulationSite(
                        site.id(), site.displayName(), site.coordinates(), SiteCondition.defaultRock()))
                .toList();
        return new ScenarioInputs(completedSource, completedSites,
                TravelTimeConfiguration.forModel(model),
                new ScientificConfiguration(
                        Map.of("geometryScaling", RuptureGeometryProvider.MODEL_ID,
                                "defaultVs30", "calquake-default-vs30-760"),
                        Map.of("timelineStepSeconds", SimulatedMmiModel.DEFAULT_TIMELINE_STEP_SECONDS,
                                "convergenceStepSeconds", SimulatedMmiModel.CONVERGENCE_STEP_SECONDS)));
    }
}
