package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.SimulationSite;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Modular catalog providing simulation sites.
 * Decoupled from historical ground-motion and intensity observations.
 */
public interface SimulationSiteCatalog {

    String DEFAULT_SIMULATION_SITES_RESOURCE = "/data/simulation_sites.json";

    /**
     * Returns the immutable list of simulation sites.
     */
    List<SimulationSite> sites();

    /**
     * Find a simulation site by its unique ID (case-insensitive).
     */
    default Optional<SimulationSite> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String trimmed = id.trim();
        return sites().stream()
                .filter(s -> s.id().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Find a simulation site by its display name (case-insensitive).
     */
    default Optional<SimulationSite> findByDisplayName(String displayName) {
        if (displayName == null) {
            return Optional.empty();
        }
        String trimmed = displayName.trim();
        return sites().stream()
                .filter(s -> s.displayName().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Look up a site by ID or throw {@link IllegalArgumentException}.
     */
    default SimulationSite requireById(String id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation site id: " + id));
    }

    /**
     * Look up a site by display name or throw {@link IllegalArgumentException}.
     */
    default SimulationSite requireByDisplayName(String displayName) {
        return findByDisplayName(displayName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation site display name: " + displayName));
    }

    /**
     * Creates an in-memory catalog from a list of sites.
     */
    static SimulationSiteCatalog of(List<SimulationSite> sites) {
        Objects.requireNonNull(sites, "sites cannot be null");
        List<SimulationSite> copy = List.copyOf(sites);
        return () -> copy;
    }

    /**
     * Loads the default simulation site catalog from the classpath.
     */
    static SimulationSiteCatalog loadDefault() {
        return loadFromResource(DEFAULT_SIMULATION_SITES_RESOURCE);
    }

    /**
     * Loads a simulation site catalog from a specified classpath resource.
     */
    static SimulationSiteCatalog loadFromResource(String resourcePath) {
        return JsonSimulationSiteCatalog.loadFromResource(resourcePath);
    }

    /**
     * Loads a simulation site catalog from a JSON input stream.
     */
    static SimulationSiteCatalog loadFromJsonStream(InputStream stream) {
        return JsonSimulationSiteCatalog.loadFromJsonStream(stream);
    }

    /**
     * Loads a simulation site catalog from a JSON string.
     */
    static SimulationSiteCatalog loadFromJsonString(String json) {
        return JsonSimulationSiteCatalog.loadFromJsonString(json);
    }
}
