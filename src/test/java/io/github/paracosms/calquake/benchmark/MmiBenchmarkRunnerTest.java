package io.github.paracosms.calquake.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MmiBenchmarkRunnerTest {
    @Test
    void exportsRequiredReproducibleArtifactsWithSeparatedCohorts() throws Exception {
        Path output = Path.of("target", "mmi-benchmark");
        var results = new MmiBenchmarkRunner().run(output);
        assertEquals(20, results.size());
        assertTrue(Files.readString(output.resolve("site-results.csv")).contains("INSTRUMENTAL_STATION"));
        assertTrue(Files.readString(output.resolve("site-results.csv")).contains("SHAKEMAP_CITY_GRID_SAMPLE"));
        assertTrue(Files.readString(output.resolve("summary.json"))
                .contains("\"prediction_target_isolation\": true"));
        assertTrue(Files.readString(output.resolve("report.md")).contains("explicitly censored"));
    }
}
