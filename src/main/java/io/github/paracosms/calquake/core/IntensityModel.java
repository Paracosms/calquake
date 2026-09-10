package io.github.paracosms.calquake.core;

/** Reference-isolated prediction preparation contract. */
public interface IntensityModel {
    MmiMode mode();
    PreparedIntensityResult prepare(ScenarioInputs inputs);
}
