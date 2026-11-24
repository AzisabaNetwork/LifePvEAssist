package net.azisaba.lifepveassist.temp.model;

/**
 * プレイヤーの現在温度と防具耐性から導き出された1ティック（1秒）分の評価結果。
 */
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
