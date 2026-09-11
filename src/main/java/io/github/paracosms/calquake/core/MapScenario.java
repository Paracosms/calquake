package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reference-free presentation model for map rendering.
 * Decouples map visualization from historical scenario models and reference locations.
 *
 * @param source     the earthquake event source
 * @param sites      the list of simulation sites to render
 * @param references optional historical references (e.g. for Replay mode overlay)
 */
public record MapScenario(
        EventSource source,
        List<SimulationSite> sites,
        Optional<ScenarioReferences> references
) {
    public MapScenario {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(sites, "sites cannot be null");
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("sites cannot be empty");
        }
        sites = List.copyOf(sites);
        references = references == null ? Optional.empty() : references;
    }

    public MapScenario(EventSource source, List<SimulationSite> sites) {
        this(source, sites, Optional.empty());
    }

    public MapScenario(EventSource source, List<SimulationSite> sites, ScenarioReferences references) {
        this(source, sites, Optional.ofNullable(references));
    }

    /**
     * Alias for {@link #source()} to provide intuitive event geometry access.
     */
    public EventSource event() {
        return source;
    }

    /**
     * Creates a MapScenario directly from ScenarioInputs without references.
     */
    public static MapScenario fromInputs(ScenarioInputs inputs) {
        return fromInputs(inputs, Optional.empty());
    }

    /**
     * Creates a MapScenario from ScenarioInputs and optional references.
     */
    public static MapScenario fromInputs(ScenarioInputs inputs, Optional<ScenarioReferences> references) {
        Objects.requireNonNull(inputs, "inputs cannot be null");
        return new MapScenario(inputs.eventSource(), inputs.sites(), references);
    }

    /**
     * Compatibility adapter converting a legacy Scenario into a reference-free MapScenario.
     */
    public static MapScenario fromLegacyScenario(Scenario scenario) {
        return fromLegacyScenario(scenario, Optional.empty());
    }

    /**
     * Compatibility adapter converting a legacy Scenario with references into a MapScenario.
     */
    public static MapScenario fromLegacyScenario(Scenario scenario, ScenarioReferences references) {
        return fromLegacyScenario(scenario, Optional.ofNullable(references));
    }

    /**
     * Compatibility adapter converting a legacy Scenario with optional references into a MapScenario.
     */
    public static MapScenario fromLegacyScenario(Scenario scenario, Optional<ScenarioReferences> references) {
        Objects.requireNonNull(scenario, "scenario cannot be null");
        List<SimulationSite> sites = scenario.locations().stream()
                .map(SimulationSite::fromReferenceLocation)
                .toList();
        return new MapScenario(EventSource.from(scenario.event()), sites, references);
    }
}
