package net.azisaba.lifepveassist.temperature.model;

/**
 * プレイヤーの装備防具（4部位）から算出される温度耐性の集計結果。
 * 耐熱（高温側）と耐冷（低温側）は独立して集計されます。
 */
public record ArmorResistance(
        int heatPieces,
        double heatLimitBonus,
        int coldPieces,
        double coldLimitBonus
) {
    public static final ArmorResistance NONE = new ArmorResistance(0, 0.0, 0, 0.0);

    public ArmorResistance capped(double maxBonus) {
        if (!Double.isFinite(maxBonus) || maxBonus <= 0.0) {
            return this;
        }
        return new ArmorResistance(
                heatPieces,
                Math.min(heatLimitBonus, maxBonus),
                coldPieces,
                Math.min(coldLimitBonus, maxBonus)
        );
    }

    public double effectiveMaxLimit(double baseMaxLimit) {
        return baseMaxLimit + heatLimitBonus;
    }

    public double effectiveMinLimit(double baseMinLimit) {
        return baseMinLimit - coldLimitBonus;
    }
}
