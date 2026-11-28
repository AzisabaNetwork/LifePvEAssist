package net.azisaba.lifepveassist.temperature.model;

public record TemperatureEvaluation(
        double temperature,
        double minLimit,
        double maxLimit,
        TemperatureSeverity severity,
        boolean applySlownessBlindness,
        boolean applyContinuousDamage,
        boolean applyHeatVisual,
        boolean applyColdVisual,
        boolean inWarningZone,
        boolean hotWarning,
        boolean triggerLethal
) {
}
