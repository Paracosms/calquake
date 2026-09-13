package io.github.paracosms.calquake.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of fault resolution containing resolved mechanism, rupture geometry, and diagnostic summary.
 */
public record FaultResolution(
        Mode mode,
        Optional<Long> candidateSectionId,
        Optional<String> candidateSectionName,
        double nearestDistanceKm,
        boolean isAmbiguous,
        String reason,
        Mechanism mechanism,
        RuptureGeometry rupture
) {
    public enum Mode {
        SUPPLIED,
        FAULT_INFORMED,
        GENERIC
    }

    public FaultResolution {
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(candidateSectionId, "candidateSectionId cannot be null");
        Objects.requireNonNull(candidateSectionName, "candidateSectionName cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(mechanism, "mechanism cannot be null");
        Objects.requireNonNull(rupture, "rupture cannot be null");
    }

    public static FaultResolution supplied(Mechanism mechanism, RuptureGeometry rupture, String reason) {
        return new FaultResolution(
                Mode.SUPPLIED,
                Optional.empty(),
                Optional.empty(),
                Double.NaN,
                false,
                reason != null ? reason : "Explicitly supplied inputs",
                mechanism,
                rupture
        );
    }

    public static FaultResolution faultInformed(
            long sectionId,
            String sectionName,
            double distanceKm,
            boolean isAmbiguous,
            Mechanism mechanism,
            RuptureGeometry rupture
    ) {
        return new FaultResolution(
                Mode.FAULT_INFORMED,
                Optional.of(sectionId),
                Optional.of(sectionName),
                distanceKm,
                isAmbiguous,
                "Associated with nearby fault section",
                mechanism,
                rupture
        );
    }

    public static FaultResolution generic(Mechanism mechanism, RuptureGeometry rupture, String reason) {
        return new FaultResolution(
                Mode.GENERIC,
                Optional.empty(),
                Optional.empty(),
                Double.NaN,
                false,
                reason != null ? reason : "No suitable nearby fault",
                mechanism,
                rupture
        );
    }

    /**
     * Compact user-facing summary line matching specification.
     */
    public String summary() {
        return switch (mode) {
            case SUPPLIED -> "Rupture: supplied geometry";
            case FAULT_INFORMED -> "Automatic rupture: based on nearby " + candidateSectionName.orElse("fault");
            case GENERIC -> "Automatic rupture: generic assumptions; no suitable nearby fault";
        };
    }

    /**
     * Namespaced metadata for inclusion in EventSource metadata.
     */
    public Map<String, String> toMetadata() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("resolver.mode", mode.name());
        map.put("resolver.summary", summary());
        candidateSectionId.ifPresent(id -> map.put("resolver.sectionId", String.valueOf(id)));
        candidateSectionName.ifPresent(name -> map.put("resolver.sectionName", name));
        if (Double.isFinite(nearestDistanceKm)) {
            map.put("resolver.distanceKm", String.format(Locale.US, "%.1f", nearestDistanceKm));
        }
        map.put("resolver.isAmbiguous", String.valueOf(isAmbiguous));
        map.put("resolver.reason", reason);
        map.put("resolver.strikeDegrees", String.format(Locale.US, "%.1f", mechanism.strikeDegrees()));
        map.put("resolver.dipDegrees", String.format(Locale.US, "%.1f", mechanism.dipDegrees()));
        map.put("resolver.rakeDegrees", String.format(Locale.US, "%.1f", mechanism.rakeDegrees()));
        map.put("resolver.note", "Simplified rectangle; may extend beyond the mapped fault section.");
        return Map.copyOf(map);
    }
}
