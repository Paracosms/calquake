package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpiricalEnvelopeTest {
    private final EmpiricalEnvelopeModel model = new EmpiricalEnvelopeModel();

    @Test
    void envelopeHasOnsetRisePeakDecayAndNonnegativeCombination() {
        EmpiricalEnvelopeModel.Parameters parameters = model.parameters(6.5, 50.0, 760.0);
        double p = 10.0;
        double s = 18.0;
        assertEquals(0.0, model.rawEnvelope(p, p, s, parameters), 0.0);
        double pRise = model.rawEnvelope(p + parameters.p().riseSeconds(), p, s, parameters);
        assertTrue(pRise > 0.0);
        double atS = model.rawEnvelope(s, p, s, parameters);
        double afterSRise = model.rawEnvelope(s + parameters.s().riseSeconds(), p, s, parameters);
        assertTrue(afterSRise > atS);
        assertTrue(model.rawEnvelope(model.supportEnd(p, s, parameters), p, s, parameters) >= 0.0);
    }

    @Test
    void retainsOutOfCalibrationDomainMetadataWithoutSuppressingValues() {
        EmpiricalEnvelopeModel.Parameters parameters = model.parameters(7.1, 250.0, 300.0);
        assertEquals(DomainStatus.OUT_OF_DOMAIN, parameters.domainStatus());
        assertTrue(model.rawEnvelope(50.0, 10.0, 40.0, parameters) > 0.0);
    }
}
