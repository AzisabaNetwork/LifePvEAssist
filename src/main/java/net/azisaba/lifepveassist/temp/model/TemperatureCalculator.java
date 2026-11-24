package net.azisaba.lifepveassist.temp.model;

/**
 * 温度評価・自然回復計算を行う純粋ロジッククラス。
 * Paper API に依存せず単体テスト可能です。
 */
public final class TemperatureCalculator {

    private TemperatureCalculator() {
    }

    /**
     * 現在温度と設定・防具耐性から、適用すべきデメリット・エフェクト・警告・即死を評価します。
     * しきい値は「即死ラインからの逆算（A案）」で算出されます。
     */
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
        double maxLimit = resistance.effectiveMaxLimit(baseMaxLimit);
        double minLimit = resistance.effectiveMinLimit(baseMinLimit);

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
                // 4部位(フルセット)でも継続ダメージのみ無効化、炎上視界エフェクトは維持
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
                // 4部位(フルセット)でも継続ダメージのみ無効化、粉雪視界エフェクトは維持
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
     * 1秒分の自然回復を適用した新しい温度を返します。
     * デフォルト温度を超えて逆側へ進まないようにクランプします。
     */
    public static double stepRecovery(double currentTemp, double defaultTemp, double ratePerSecond) {
        if (ratePerSecond <= 0.0 || Math.abs(currentTemp - defaultTemp) < 1e-9) {
            return defaultTemp;
        }
        if (currentTemp > defaultTemp) {
            return Math.max(defaultTemp, currentTemp - ratePerSecond);
        } else {
            return Math.min(defaultTemp, currentTemp + ratePerSecond);
        }
    }

    /**
     * BossBar の進捗バー（0.0f〜1.0f）を算出します。
     */
    public static float computeBossBarProgress(double currentTemp, double minLimit, double maxLimit) {
        double span = maxLimit - minLimit;
        if (span <= 0.0) {
            return 0.5f;
        }
        double ratio = (currentTemp - minLimit) / span;
        return (float) Math.max(0.0, Math.min(1.0, ratio));
    }
}
