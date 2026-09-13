package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable domain representation of a mapped fault trace from the authoritative
 * USGS Quaternary Fault and Fold Database.
 * <p>
 * This is a visual/geographic reference layer and does not represent an earthquake
 * rupture or alter GMPE/MMI predictions.
 */
public record MappedFault(
        int objectId,
        String faultId,
        String sectionId,
        String faultName,
        String sectionName,
        String age,
        String lineType,
        String mappedCertainty,
        String strike,
        String slipSense,
        String dipDirection,
        String faultUrl,
        List<List<GeoPoint>> polylines
) {
    public MappedFault {
        faultId = faultId == null ? "" : faultId.trim();
        sectionId = sectionId == null ? "" : sectionId.trim();
        faultName = faultName == null ? "" : faultName.trim();
        sectionName = sectionName == null ? "" : sectionName.trim();
        age = age == null ? "" : age.trim();
        lineType = lineType == null ? "" : lineType.trim();
        mappedCertainty = mappedCertainty == null ? "" : mappedCertainty.trim();
        strike = strike == null ? "" : strike.trim();
        slipSense = slipSense == null ? "" : slipSense.trim();
        dipDirection = dipDirection == null ? "" : dipDirection.trim();
        faultUrl = faultUrl == null ? "" : faultUrl.trim();
        Objects.requireNonNull(polylines, "polylines cannot be null");
        polylines = polylines.stream()
                .filter(p -> p != null && p.size() >= 2)
                .map(List::copyOf)
                .toList();
    }

    /**
     * Display label combining fault name and section name.
     */
    public String displayName() {
        if (!sectionName.isBlank() && !sectionName.equalsIgnoreCase(faultName)) {
            return faultName.isBlank() ? sectionName : faultName + " (" + sectionName + ")";
        }
        return faultName.isBlank() ? "Unnamed Fault" : faultName;
    }

    /**
     * True if the mapping is well-constrained (solid stroke vs dashed stroke).
     */
    public boolean isWellConstrained() {
        return lineType.equalsIgnoreCase("Well Constrained")
                || mappedCertainty.equalsIgnoreCase("Good");
    }

    /**
     * Projects all polylines using the specified Mercator projection.
     */
    public List<List<MercatorProjection.ProjectedPoint>> project(MercatorProjection projection) {
        Objects.requireNonNull(projection, "projection cannot be null");
        return polylines.stream()
                .map(line -> line.stream().map(projection::project).toList())
                .toList();
    }
}
