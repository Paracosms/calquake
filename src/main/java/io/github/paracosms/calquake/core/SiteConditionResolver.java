package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.CaliforniaVs30Grid;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves shallow-site Vs30 conditions using deterministic precedence:
 * <ol>
 *   <li>Explicit {@link SiteConditionProvenance#MEASURED} or imported {@link SiteConditionProvenance#MAPPED_PROXY} condition if present</li>
 *   <li>Authoritative USGS raster sample ({@link SiteConditionProvenance#MAPPED_PROXY})</li>
 *   <li>Documented reference-rock fallback 760.0 m/s ({@link SiteConditionProvenance#DEFAULT})</li>
 * </ol>
 */
public final class SiteConditionResolver {

    private final CaliforniaVs30Grid grid;

    public SiteConditionResolver(CaliforniaVs30Grid grid) {
        this.grid = Objects.requireNonNull(grid, "grid cannot be null");
    }

    public static SiteConditionResolver defaultResolver() {
        return new SiteConditionResolver(CaliforniaVs30Grid.loadDefault());
    }

    public CaliforniaVs30Grid grid() {
        return grid;
    }

    /**
     * Resolves the effective SiteCondition for a site.
     *
     * @param site simulation site with coordinates and optional existing condition
     * @return resolved site condition
     */
    public SiteCondition resolve(SimulationSite site) {
        Objects.requireNonNull(site, "site cannot be null");
        return resolve(site.coordinates(), site.siteCondition());
    }

    /**
     * Resolves the effective SiteCondition for a coordinate and optional existing condition.
     *
     * @param coordinates geographic coordinate
     * @param explicitCondition optional existing condition
     * @return resolved site condition
     */
    public SiteCondition resolve(GeoPoint coordinates, Optional<SiteCondition> explicitCondition) {
        Objects.requireNonNull(coordinates, "coordinates cannot be null");
        if (explicitCondition != null && explicitCondition.isPresent()) {
            SiteCondition condition = explicitCondition.get();
            // Preserve explicit MEASURED or non-default MAPPED_PROXY conditions
            if (condition.provenance() == SiteConditionProvenance.MEASURED
                    || condition.provenance() == SiteConditionProvenance.MAPPED_PROXY) {
                return condition;
            }
        }

        Optional<Vs30Sample> sampleOpt = grid.sample(coordinates);
        if (sampleOpt.isPresent()) {
            Vs30Sample sample = sampleOpt.get();
            return new SiteCondition(
                    sample.vs30MetersPerSecond(),
                    SiteConditionProvenance.MAPPED_PROXY,
                    sample.sourceId()
            );
        }

        return SiteCondition.defaultRock();
    }
}
