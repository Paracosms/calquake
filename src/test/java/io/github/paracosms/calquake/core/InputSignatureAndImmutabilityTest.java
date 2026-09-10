package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputSignatureAndImmutabilityTest {
    @Test
    void simulatedSiteNeedsNoHistoricalReference() {
        assertDoesNotThrow(() -> new SimulationSite(
                "future-site", "Future site", new GeoPoint(35.0, -118.0), SiteCondition.defaultRock()));
    }

    @Test
    void everyScientificInputDimensionChangesTheSignature() {
        ScenarioInputs base = new ScenarioLoader().loadScenarioInputs("Ridgecrest");
        String signature = InputSignature.compute(base, MmiMode.SIMULATED);

        assertChanged(signature, withEvent(base, event(base, 7.2, base.event().depthKm(),
                base.event().epicenter(), base.event().ruptureGeometry(), base.event().mechanism())));
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), 8.5,
                base.event().epicenter(), base.event().ruptureGeometry(), base.event().mechanism())));
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), base.event().depthKm(),
                new GeoPoint(35.8, -117.7), base.event().ruptureGeometry(), base.event().mechanism())));

        RuptureGeometry rupture = base.event().ruptureGeometry().orElseThrow();
        RuptureGeometry changedRupture = new RuptureGeometry(
                rupture.surfaceProjectionParts(), rupture.topDepthKm(), rupture.bottomDepthKm(),
                rupture.strikeDegrees(), rupture.dipDegrees(), rupture.sourceId(),
                rupture.sourceSha256() + "-changed", rupture.generated());
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), base.event().depthKm(),
                base.event().epicenter(), Optional.of(changedRupture), base.event().mechanism())));

        Mechanism mechanism = base.event().mechanism().orElseThrow();
        Mechanism changedMechanism = new Mechanism(
                mechanism.rakeDegrees() + 1.0, mechanism.strikeDegrees(), mechanism.dipDegrees(),
                mechanism.style(), mechanism.provenance());
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), base.event().depthKm(),
                base.event().epicenter(), base.event().ruptureGeometry(), Optional.of(changedMechanism))));

        List<SimulationSite> sites = new ArrayList<>(base.sites());
        SimulationSite first = sites.getFirst();
        SiteCondition condition = first.siteCondition().orElseThrow();
        sites.set(0, new SimulationSite(first.id(), first.displayName(), first.coordinates(),
                new SiteCondition(condition.vs30MetersPerSecond() + 1.0,
                        condition.provenance(), condition.sourceId())));
        assertChanged(signature, new ScenarioInputs(base.event(), sites,
                base.travelTimeConfiguration(), base.scientificConfiguration()));

        LinkedHashMap<String, String> versions = new LinkedHashMap<>(
                base.scientificConfiguration().versionIds());
        versions.put("groundMotion", "deliberately-different-model-version");
        assertChanged(signature, new ScenarioInputs(base.event(), base.sites(),
                base.travelTimeConfiguration(), new ScientificConfiguration(
                versions, base.scientificConfiguration().numericParameters())));
    }

    @Test
    void preparedReplayTimelinesAndFramesAreImmutable() {
        ScenarioLoader.ScenarioBundle bundle = new ScenarioLoader().loadScenarioBundle("Ridgecrest");
        PreparedReplay replay = new ReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(bundle.inputs(), bundle.references(), MmiMode.SIMULATED);
        ReplayEngine engine = ReplayEngine.createPrepared(
                bundle.scenario(), new HadleyKanamoriTauPModel(), replay);

        assertThrows(UnsupportedOperationException.class,
                () -> replay.timelinesBySiteId().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> replay.timelinesBySiteId().values().iterator().next().samples().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> engine.frameAt(1.0).locationIntensities().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> replay.inputs().sites().clear());
    }

    private static void assertChanged(String baseSignature, ScenarioInputs changed) {
        assertNotEquals(baseSignature, InputSignature.compute(changed, MmiMode.SIMULATED));
    }

    private static ScenarioInputs withEvent(ScenarioInputs base, EventSource event) {
        return new ScenarioInputs(event, base.sites(), base.travelTimeConfiguration(),
                base.scientificConfiguration());
    }

    private static EventSource event(
            ScenarioInputs base, double magnitude, double depth, GeoPoint epicenter,
            Optional<RuptureGeometry> rupture, Optional<Mechanism> mechanism) {
        EventSource event = base.event();
        return new EventSource(event.id(), event.network(), event.title(), event.originUtc(), magnitude,
                event.magnitudeType(), epicenter, depth, rupture, mechanism, event.metadata());
    }
}
