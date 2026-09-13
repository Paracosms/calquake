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
        return completeCustomScenario(source, requestedSites, model, Optional.empty());
    }

    /**
     * Resolves versioned simulation assumptions and builds reference-free ScenarioInputs
     * from custom scenario settings.
     */
    public static ScenarioInputs forCustomScenario(
            SimulationScenarioSettings settings, List<SimulationSite> requestedSites, TravelTimeModel model) {
        Objects.requireNonNull(settings, "settings cannot be null");
        SimulationAssumptionSet assumptions = SimulationAssumptionSet.resolve(settings.assumptionSetId());

        EventSource source = new EventSource(
                settings.scenarioId(), "calquake", settings.displayName(),
                settings.createdUtc(), settings.magnitude(), "mw",
                settings.epicenter(), settings.depthKm(), Optional.empty(),
                Optional.empty(),
                Map.of("assumptionSet", assumptions.id(),
                        "intensityDisplayMode", settings.intensityDisplayMode().name()));

        return completeCustomScenario(source, requestedSites, model, Optional.of(assumptions));
    }

    private static ScenarioInputs completeCustomScenario(
            EventSource source,
            List<SimulationSite> requestedSites,
            TravelTimeModel model,
            Optional<SimulationAssumptionSet> assumptions
    ) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(requestedSites, "requestedSites cannot be null");
        Objects.requireNonNull(model, "model cannot be null");

        boolean hasMechanism = source.mechanism().isPresent();
        boolean hasGeometry = source.ruptureGeometry().isPresent();

        FaultResolution resolution;
        boolean usedAutomaticCatalog = false;
        String resolverId = null;
        String catalogId = null;
        String catalogChecksum = null;

        if (hasMechanism && hasGeometry) {
            resolution = FaultResolution.supplied(
                    source.mechanism().get(), source.ruptureGeometry().get(), "Supplied geometry and mechanism");
        } else if (hasMechanism) {
            Mechanism mech = source.mechanism().get();
            RuptureGeometry rupture = RuptureGeometryProvider.generatePlanar(
                    source.epicenter(), source.depthKm(), source.magnitude(), mech);
            resolution = FaultResolution.supplied(
                    mech, rupture, "Supplied mechanism; generated planar geometry");
        } else if (hasGeometry) {
            RuptureGeometry rup = source.ruptureGeometry().get();
            Mechanism mech = new Mechanism(0.0, rup.strikeDegrees(), rup.dipDegrees(), "STRIKE_SLIP", "Derived from supplied geometry");
            resolution = FaultResolution.supplied(
                    mech, rup, "Supplied geometry; derived strike-slip mechanism");
        } else {
            AutomaticFaultResolver faultResolver = AutomaticFaultResolver.defaultResolver();
            resolution = faultResolver.resolve(source.epicenter(), source.depthKm(), source.magnitude());
            usedAutomaticCatalog = true;
            resolverId = AutomaticFaultResolver.RESOLVER_ID;
            catalogId = faultResolver.catalog().datasetId();
            catalogChecksum = faultResolver.catalog().sha256();
        }

        Map<String, String> mergedMetadata = new java.util.LinkedHashMap<>(source.metadata());
        mergedMetadata.keySet().removeIf(k -> k.startsWith("resolver."));
        mergedMetadata.putAll(resolution.toMetadata());
        assumptions.ifPresent(a -> mergedMetadata.put("assumptionSet", a.id()));

        EventSource completedSource = new EventSource(
                source.id(), source.network(), source.title(), source.originUtc(),
                source.magnitude(), source.magnitudeType(), source.epicenter(), source.depthKm(),
                Optional.of(resolution.rupture()),
                Optional.of(resolution.mechanism()),
                mergedMetadata);

        SiteConditionResolver siteResolver = SiteConditionResolver.defaultResolver();
        List<SimulationSite> completedSites = requestedSites.stream()
                .map(site -> new SimulationSite(
                        site.id(), site.displayName(), site.coordinates(), siteResolver.resolve(site)))
                .toList();

        Map<String, String> versionIds = new java.util.LinkedHashMap<>();
        if (assumptions.isPresent()) {
            versionIds.put("assumptionSet", assumptions.get().id());
            versionIds.put("geometryScaling", assumptions.get().ruptureScalingModel());
        } else {
            versionIds.put("geometryScaling", RuptureGeometryProvider.MODEL_ID);
        }
        versionIds.put("defaultVs30", "calquake-default-vs30-760");
        versionIds.put("vs30DatasetId", siteResolver.grid().datasetId());
        versionIds.put("vs30Checksum", siteResolver.grid().sha256());

        if (usedAutomaticCatalog) {
            versionIds.put("resolverId", resolverId);
            versionIds.put("faultCatalogId", catalogId);
            versionIds.put("faultCatalogChecksum", catalogChecksum);
        }

        return new ScenarioInputs(
                completedSource,
                completedSites,
                TravelTimeConfiguration.forModel(model),
                new ScientificConfiguration(
                        versionIds,
                        Map.of("timelineStepSeconds", SimulatedMmiModel.DEFAULT_TIMELINE_STEP_SECONDS,
                                "convergenceStepSeconds", SimulatedMmiModel.CONVERGENCE_STEP_SECONDS)));
    }
}
