package io.github.paracosms.calquake.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/** Stable SHA-256 replay cache signature. */
public final class InputSignature {
    private InputSignature() {}

    public static String compute(ScenarioInputs inputs, MmiMode mode) {
        return compute(inputs, mode, Optional.empty());
    }

    public static String compute(ScenarioInputs inputs, MmiMode mode, Optional<ScenarioReferences> references) {
        StringBuilder text = new StringBuilder("calquake-prepared-replay-v1|").append(mode).append('|');
        EventSource event = inputs.event();
        text.append(event.id()).append('|').append(event.originUtc()).append('|')
                .append(hex(event.magnitude())).append('|').append(event.magnitudeType()).append('|')
                .append(hex(event.epicenter().latitude())).append(',').append(hex(event.epicenter().longitude()))
                .append('|').append(hex(event.depthKm())).append('|');
        event.mechanism().ifPresent(m -> text.append(hex(m.rakeDegrees())).append('|')
                .append(hex(m.strikeDegrees())).append('|').append(hex(m.dipDegrees())).append('|')
                .append(m.style()).append('|').append(m.provenance()).append('|'));
        event.ruptureGeometry().ifPresent(g -> text.append(g.canonicalForm()).append('|'));
        for (SimulationSite site : inputs.sites()) {
            text.append(site.id()).append('|').append(site.displayName()).append('|')
                    .append(hex(site.coordinates().latitude())).append(',')
                    .append(hex(site.coordinates().longitude())).append('|');
            site.siteCondition().ifPresent(c -> text.append(hex(c.vs30MetersPerSecond())).append('|')
                    .append(c.provenance()).append('|').append(c.sourceId()).append('|'));
        }
        appendSorted(text, inputs.travelTimeConfiguration().parameters());
        text.append(inputs.travelTimeConfiguration().modelId()).append('|')
                .append(inputs.travelTimeConfiguration().version()).append('|');
        appendSorted(text, inputs.scientificConfiguration().versionIds());
        inputs.scientificConfiguration().numericParameters().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> text.append(e.getKey()).append('=').append(hex(e.getValue())).append('|'));
        if (mode == MmiMode.RECORDED) {
            references.ifPresent(refs -> refs.bySiteId().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).forEach(e -> {
                        text.append(e.getKey()).append('|');
                        e.getValue().shakeMapPeakMmi().ifPresent(v -> text.append(hex(v)));
                        text.append('|').append(e.getValue().provenance()).append('|');
                    }));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every Java runtime must provide SHA-256", impossible);
        }
    }

    private static void appendSorted(StringBuilder text, Map<String, String> values) {
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> text.append(e.getKey()).append('=').append(e.getValue()).append('|'));
    }

    private static String hex(double value) { return Double.toHexString(value); }
}
