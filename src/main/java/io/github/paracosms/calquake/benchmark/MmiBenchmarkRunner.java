package io.github.paracosms.calquake.benchmark;

import io.github.paracosms.calquake.core.Bssa14GroundMotion;
import io.github.paracosms.calquake.core.DomainStatus;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.IntensityTimeline;
import io.github.paracosms.calquake.core.MmiMode;
import io.github.paracosms.calquake.core.PreparedReplay;
import io.github.paracosms.calquake.core.ReferenceIntensity;
import io.github.paracosms.calquake.core.ReplayPreparer;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.core.ScenarioReferences;
import io.github.paracosms.calquake.core.Worden2012Gmice;
import io.github.paracosms.calquake.data.ScenarioLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Deterministic offline benchmark exporter. It never supplies targets to a predictor. */
public final class MmiBenchmarkRunner {
    public static final String STATION_FIXTURE = "/fixtures/mmi_benchmark_stations.json";
    private final ScenarioLoader loader = new ScenarioLoader();
    private final Bssa14GroundMotion bssa14 = new Bssa14GroundMotion();
    private final Worden2012Gmice gmice = new Worden2012Gmice();

    public List<Result> run(Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory cannot be null");
        Files.createDirectories(outputDirectory);
        Map<String, ScenarioLoader.ScenarioBundle> bundles = Map.of(
                "ci38457511", loader.loadScenarioBundle("Ridgecrest"),
                "ci3144585", loader.loadScenarioBundle("Northridge"));
        ReplayPreparer preparer = new ReplayPreparer(new HadleyKanamoriTauPModel());
        Map<String, PreparedReplay> simulated = new LinkedHashMap<>();
        for (Map.Entry<String, ScenarioLoader.ScenarioBundle> entry : bundles.entrySet()) {
            simulated.put(entry.getKey(), preparer.prepare(
                    entry.getValue().inputs(), entry.getValue().references(), MmiMode.SIMULATED));
        }

        List<Result> results = new ArrayList<>();
        addInstrumentalStations(results, bundles);
        addShakeMapCitySamples(results, bundles, simulated);
        Files.writeString(outputDirectory.resolve("site-results.csv"), toCsv(results), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("summary.json"), toSummaryJson(results), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("report.md"), toReport(results), StandardCharsets.UTF_8);
        return List.copyOf(results);
    }

    private void addInstrumentalStations(
            List<Result> results, Map<String, ScenarioLoader.ScenarioBundle> bundles) throws IOException {
        JsonNode root;
        try (InputStream stream = MmiBenchmarkRunner.class.getResourceAsStream(STATION_FIXTURE)) {
            if (stream == null) throw new IOException("Missing benchmark fixture " + STATION_FIXTURE);
            root = JsonMapper.builder().build().readTree(stream);
        }
        for (JsonNode node : root.get("stations")) {
            String eventId = node.get("event_id").asString();
            ScenarioInputs inputs = bundles.get(eventId).inputs();
            double rjb = node.get("rjb").asDouble();
            double vs30 = node.get("vs30").asDouble();
            double targetPgv = node.get("observed_pgv").asDouble();
            double targetMmi = node.get("instrumental_mmi").asDouble();
            double rake = inputs.event().mechanism().orElseThrow().rakeDegrees();
            Bssa14GroundMotion.Prediction prediction = bssa14.predictPgv(
                    inputs.event().magnitude(), rake, rjb, vs30);
            double predictedMmi = gmice.fromPgvCmPerSecond(prediction.pgvCmPerSecond());
            String siteId = node.get("site_id").asString();
            String signature = sha256(eventId + '|' + siteId + '|'
                    + Double.toHexString(node.get("latitude").asDouble()) + '|'
                    + Double.toHexString(node.get("longitude").asDouble()) + '|'
                    + Double.toHexString(vs30) + '|' + Double.toHexString(rjb) + '|'
                    + Bssa14GroundMotion.COEFFICIENT_SHA256 + '|'
                    + Worden2012Gmice.COEFFICIENT_SHA256);
            results.add(new Result(eventId, siteId, node.get("name").asString(),
                    "INSTRUMENTAL_STATION", Bssa14GroundMotion.MODEL_ID, signature,
                    "AVAILABLE", prediction.domainStatus(), vs30, rjb,
                    prediction.pgvCmPerSecond(), targetPgv, predictedMmi, targetMmi,
                    Math.log(prediction.pgvCmPerSecond() / targetPgv), predictedMmi - targetMmi,
                    ThresholdResult.censored(), ThresholdResult.censored(), ThresholdResult.censored()));
        }
    }

    private void addShakeMapCitySamples(
            List<Result> results,
            Map<String, ScenarioLoader.ScenarioBundle> bundles,
            Map<String, PreparedReplay> simulated) {
        for (Map.Entry<String, ScenarioLoader.ScenarioBundle> entry : bundles.entrySet()) {
            String eventId = entry.getKey();
            ScenarioReferences references = entry.getValue().references();
            PreparedReplay replay = simulated.get(eventId);
            for (IntensityTimeline timeline : replay.timelinesBySiteId().values()) {
                ReferenceIntensity reference = references.require(timeline.site().id());
                OptionalDouble targetPgv = reference.groundMotionTargets()
                        .flatMap(target -> target.pgvCentimetersPerSecond().isPresent()
                                ? java.util.Optional.of(target.pgvCentimetersPerSecond())
                                : java.util.Optional.empty())
                        .orElse(OptionalDouble.empty());
                double predictedPgv = timeline.predictedPeakPgv().orElseThrow();
                double predictedMmi = timeline.finalMmi().orElseThrow();
                double targetMmi = reference.shakeMapPeakMmi().orElseThrow();
                results.add(new Result(eventId, timeline.site().id(), timeline.site().displayName(),
                        "SHAKEMAP_CITY_GRID_SAMPLE", replay.modelMetadata().modelId(), replay.inputSignature(),
                        "AVAILABLE", timeline.domainStatus(),
                        timeline.site().siteCondition().orElseThrow().vs30MetersPerSecond(),
                        timeline.rjbKm().orElseThrow(), predictedPgv,
                        targetPgv.orElse(Double.NaN), predictedMmi, targetMmi,
                        targetPgv.isPresent() ? Math.log(predictedPgv / targetPgv.getAsDouble()) : Double.NaN,
                        predictedMmi - targetMmi, threshold(timeline, 3.5),
                        threshold(timeline, 4.5), threshold(timeline, 5.5)));
            }
        }
    }

    private static ThresholdResult threshold(IntensityTimeline timeline, double threshold) {
        OptionalDouble predicted = timeline.samples().stream()
                .filter(sample -> sample.mmi().isPresent() && sample.mmi().getAsDouble() >= threshold)
                .mapToDouble(IntensityTimeline.Sample::elapsedSeconds)
                .findFirst();
        return new ThresholdResult(predicted.orElse(Double.NaN), Double.NaN, Double.NaN, Double.NaN,
                predicted.isPresent()
                        ? "CENSORED_TARGET_UNAVAILABLE:PREDICTED_CROSSING"
                        : "CENSORED_TARGET_UNAVAILABLE:PREDICTED_NO_CROSSING");
    }

    private static String toCsv(List<Result> results) {
        StringBuilder csv = new StringBuilder("event_id,site_id,site_name,target_class,model_id,input_signature,bssa14_coefficient_sha256,worden_coefficient_sha256,status,domain_status,vs30_m_s,rjb_km,predicted_pgv_cm_s,target_pgv_cm_s,predicted_mmi,target_mmi,pgv_log_residual,mmi_residual,t3_5_predicted_s,t3_5_target_s,t3_5_signed_error_s,t3_5_absolute_error_s,t3_5_outcome,t4_5_predicted_s,t4_5_target_s,t4_5_signed_error_s,t4_5_absolute_error_s,t4_5_outcome,t5_5_predicted_s,t5_5_target_s,t5_5_signed_error_s,t5_5_absolute_error_s,t5_5_outcome\n");
        for (Result r : results) {
            csv.append(quote(r.eventId())).append(',').append(quote(r.siteId())).append(',')
                    .append(quote(r.siteName())).append(',').append(quote(r.targetClass())).append(',')
                    .append(quote(r.modelId())).append(',').append(quote(r.inputSignature())).append(',')
                    .append(Bssa14GroundMotion.COEFFICIENT_SHA256).append(',')
                    .append(Worden2012Gmice.COEFFICIENT_SHA256).append(',')
                    .append(r.status()).append(',').append(r.domainStatus()).append(',')
                    .append(number(r.vs30())).append(',').append(number(r.rjb())).append(',')
                    .append(number(r.predictedPgv())).append(',').append(number(r.targetPgv())).append(',')
                    .append(number(r.predictedMmi())).append(',').append(number(r.targetMmi())).append(',')
                    .append(number(r.pgvLogResidual())).append(',').append(number(r.mmiResidual())).append(',')
                    .append(thresholdCsv(r.threshold35())).append(',')
                    .append(thresholdCsv(r.threshold45())).append(',')
                    .append(thresholdCsv(r.threshold55())).append('\n');
        }
        return csv.toString();
    }

    private static String toSummaryJson(List<Result> results) {
        Map<String, List<Result>> cohorts = new LinkedHashMap<>();
        for (Result result : results) {
            cohorts.computeIfAbsent(result.eventId() + "/" + result.targetClass(), ignored -> new ArrayList<>())
                    .add(result);
        }
        StringBuilder json = new StringBuilder("{\n  \"benchmark_version\": \"1.0\",\n")
                .append("  \"model_id\": \"").append(Bssa14GroundMotion.MODEL_ID).append("\",\n")
                .append("  \"coefficient_hash\": \"").append(Bssa14GroundMotion.COEFFICIENT_SHA256).append("\",\n")
                .append("  \"gmice_id\": \"").append(Worden2012Gmice.MODEL_ID).append("\",\n")
                .append("  \"gmice_coefficient_hash\": \"").append(Worden2012Gmice.COEFFICIENT_SHA256).append("\",\n")
                .append("  \"prediction_target_isolation\": true,\n  \"cohorts\": [\n");
        int index = 0;
        for (Map.Entry<String, List<Result>> entry : cohorts.entrySet()) {
            Metrics metrics = metrics(entry.getValue());
            if (index++ > 0) json.append(",\n");
            json.append("    {\"id\":\"").append(entry.getKey()).append("\",\"count\":")
                    .append(entry.getValue().size()).append(",\"mmi_mae\":").append(number(metrics.mae()))
                    .append(",\"mmi_rmse\":").append(number(metrics.rmse()))
                    .append(",\"mmi_signed_bias\":").append(number(metrics.bias()))
                    .append(",\"fraction_within_one_mmi\":").append(number(metrics.withinOne()))
                    .append(",\"in_domain_count\":").append(metrics.inDomain()).append('}');
        }
        return json.append("\n  ]\n}\n").toString();
    }

    private static String toReport(List<Result> results) {
        long extrapolated = results.stream().filter(r -> r.domainStatus() == DomainStatus.OUT_OF_DOMAIN).count();
        return "# CalQuake simulated-MMI benchmark\n\n"
                + "Model: `" + Bssa14GroundMotion.MODEL_ID + "` → `" + Worden2012Gmice.MODEL_ID + "`\n\n"
                + "Coefficient hashes: `" + Bssa14GroundMotion.COEFFICIENT_SHA256 + "`, `"
                + Worden2012Gmice.COEFFICIENT_SHA256 + "`. Per-row input hashes are in `site-results.csv`.\n\n"
                + "This reproducible offline report evaluates " + results.size() + " event/site targets across Ridgecrest and Northridge. "
                + "Prediction inputs and historical evaluation targets are loaded through separate domain types.\n\n"
                + "- Instrumental-station PGV and station-derived MMI remain a distinct target class.\n"
                + "- City ShakeMap samples remain a separate, non-independent comparison class.\n"
                + "- " + extrapolated + " rows are marked outside a model calibration domain; live display intentionally does not warn.\n"
                + "- Waveform-derived threshold timing is unavailable in the compact fixture and is explicitly censored, never fabricated.\n"
                + "- Station component metadata is not asserted to be RotD50; interpret PGV residuals with that compatibility limitation.\n";
    }

    private static Metrics metrics(List<Result> rows) {
        double abs = 0.0;
        double squared = 0.0;
        double signed = 0.0;
        int within = 0;
        int inDomain = 0;
        for (Result row : rows) {
            double residual = row.mmiResidual();
            abs += Math.abs(residual);
            squared += residual * residual;
            signed += residual;
            if (Math.abs(residual) <= 1.0) within++;
            if (row.domainStatus() == DomainStatus.IN_DOMAIN) inDomain++;
        }
        return new Metrics(abs / rows.size(), Math.sqrt(squared / rows.size()),
                signed / rows.size(), (double) within / rows.size(), inDomain);
    }

    private static String quote(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
    private static String thresholdCsv(ThresholdResult result) {
        return number(result.predictedSeconds()) + ',' + number(result.targetSeconds()) + ','
                + number(result.signedErrorSeconds()) + ',' + number(result.absoluteErrorSeconds()) + ','
                + quote(result.outcome());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every Java runtime must provide SHA-256", impossible);
        }
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.9f", value) : "null";
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? Path.of("target", "mmi-benchmark") : Path.of(args[0]);
        new MmiBenchmarkRunner().run(output);
    }

    public record Result(String eventId, String siteId, String siteName, String targetClass,
                         String modelId, String inputSignature, String status, DomainStatus domainStatus,
                         double vs30, double rjb, double predictedPgv, double targetPgv,
                         double predictedMmi, double targetMmi, double pgvLogResidual,
                         double mmiResidual, ThresholdResult threshold35,
                         ThresholdResult threshold45, ThresholdResult threshold55) {}
    public record ThresholdResult(double predictedSeconds, double targetSeconds,
                                  double signedErrorSeconds, double absoluteErrorSeconds,
                                  String outcome) {
        private static ThresholdResult censored() {
            return new ThresholdResult(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    "CENSORED_NO_WAVEFORM_REFERENCE");
        }
    }
    private record Metrics(double mae, double rmse, double bias, double withinOne, int inDomain) {}
}
