package net.azisaba.lifepveassist.temperature.model;

/**
 * 温度クランプ・評価・自然回復・オフライン回復を行う純粋計算ロジック。
 */
public final class TemperatureCalculator {

    public static final double MIN_SAFE_TEMP = -1000.0;
    public static final double MAX_SAFE_TEMP = 1000.0;

    private TemperatureCalculator() {
    }

    /**
     * 異常値 (NaN / Infinite) を防ぎ、内部温度を [-1000℃, +1000℃] にクランプします。
     */
    public static double clampTemperature(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(MIN_SAFE_TEMP, Math.min(MAX_SAFE_TEMP, value));
    }

    public static TemperatureEvaluation evaluate(
            double temp,
            double defaultTemp,
            double baseMinLimit,
            double baseMaxLimit,
            ArmorResistance resistance,
            double stage1Offset,
            double stage2Offset,
            double warningOffset,
            int stage1ImmunityPieces,
            int stage2DamageImmunityPieces
    ) {
        double maxLimit = Math.min(MAX_SAFE_TEMP, resistance.effectiveMaxLimit(baseMaxLimit));
        double minLimit = Math.max(MIN_SAFE_TEMP, resistance.effectiveMinLimit(baseMinLimit));

        double hotStage1Threshold = maxLimit - stage1Offset;
        double hotStage2Threshold = maxLimit - stage2Offset;
        double hotWarningThreshold = maxLimit - warningOffset;

        double coldStage1Threshold = minLimit + stage1Offset;
        double coldStage2Threshold = minLimit + stage2Offset;
        double coldWarningThreshold = minLimit + warningOffset;

        boolean isHotSide = temp > defaultTemp;
        boolean isColdSide = temp < defaultTemp;

        TemperatureSeverity severity = TemperatureSeverity.NORMAL;
        boolean triggerLethal = false;
        boolean applyStage1 = false;
        boolean applyStage2Damage = false;
        boolean applyHeatVisual = false;
        boolean applyColdVisual = false;

        if (isHotSide) {
            if (temp >= maxLimit) {
                severity = TemperatureSeverity.HOT_LETHAL;
                triggerLethal = true;
                applyHeatVisual = true;
            } else if (temp >= hotStage2Threshold) {
                severity = TemperatureSeverity.HOT_STAGE2;
                applyStage1 = resistance.heatPieces() < stage1ImmunityPieces;
                applyStage2Damage = resistance.heatPieces() < stage2DamageImmunityPieces;
                applyHeatVisual = true;
            } else if (temp >= hotStage1Threshold) {
                severity = TemperatureSeverity.HOT_STAGE1;
                applyStage1 = resistance.heatPieces() < stage1ImmunityPieces;
            }
        } else if (isColdSide) {
            if (temp <= minLimit) {
                severity = TemperatureSeverity.COLD_LETHAL;
                triggerLethal = true;
                applyColdVisual = true;
            } else if (temp <= coldStage2Threshold) {
                severity = TemperatureSeverity.COLD_STAGE2;
                applyStage1 = resistance.coldPieces() < stage1ImmunityPieces;
                applyStage2Damage = resistance.coldPieces() < stage2DamageImmunityPieces;
                applyColdVisual = true;
            } else if (temp <= coldStage1Threshold) {
                severity = TemperatureSeverity.COLD_STAGE1;
                applyStage1 = resistance.coldPieces() < stage1ImmunityPieces;
            }
        }

        boolean hotWarn = isHotSide && temp >= hotWarningThreshold && !triggerLethal;
        boolean coldWarn = isColdSide && temp <= coldWarningThreshold && !triggerLethal;
        boolean inWarningZone = hotWarn || coldWarn;

        return new TemperatureEvaluation(
                temp,
                minLimit,
                maxLimit,
                severity,
                applyStage1,
                applyStage2Damage,
                applyHeatVisual,
                applyColdVisual,
                inWarningZone,
                hotWarn,
                triggerLethal
        );
    }

    /**
     * 1秒分の自然回復を適用します（デフォルト温度を超えて逆方向へ進まない）。
     */
    public static double stepRecovery(double currentTemp, double defaultTemp, double ratePerSecond) {
        if (ratePerSecond <= 0.0) {
            return currentTemp;
        }
        if (Math.abs(currentTemp - defaultTemp) < 1e-9) {
            return defaultTemp;
        }
        if (currentTemp > defaultTemp) {
            return Math.max(defaultTemp, currentTemp - ratePerSecond);
        }
        return Math.min(defaultTemp, currentTemp + ratePerSecond);
    }

    /**
     * オフライン中の経過秒数に応じた自然回復を適用します。
     */
    public static double applyOfflineRecovery(
            double savedTemp,
            double defaultTemp,
            long elapsedSeconds,
            int delaySeconds,
            double ratePerSecond
    ) {
        double clamped = clampTemperature(savedTemp, defaultTemp);
        if (elapsedSeconds <= delaySeconds || ratePerSecond <= 0.0 || Math.abs(clamped - defaultTemp) < 1e-9) {
            return clamped;
        }
        long recoverySeconds = elapsedSeconds - delaySeconds;
        double totalDelta = recoverySeconds * ratePerSecond;
        if (!Double.isFinite(totalDelta)) {
            return defaultTemp;
        }
        if (clamped > defaultTemp) {
            return Math.max(defaultTemp, clamped - totalDelta);
        }
        return Math.min(defaultTemp, clamped + totalDelta);
    }

    public static float computeBossBarProgress(double currentTemp, double minLimit, double maxLimit) {
        double span = maxLimit - minLimit;
        if (span <= 0.0) {
            return 0.5f;
        }
        double ratio = (currentTemp - minLimit) / span;
        return (float) Math.max(0.0, Math.min(1.0, ratio));
    }
}
