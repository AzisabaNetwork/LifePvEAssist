package net.azisaba.lifepveassist.temp.model;

/**
 * 現在の温度が属する危険度ステージ。
 */
public enum TemperatureSeverity {
    NORMAL,
    HOT_STAGE1,
    HOT_STAGE2,
    HOT_LETHAL,
    COLD_STAGE1,
    COLD_STAGE2,
    COLD_LETHAL;

    public boolean isHot() {
        return this == HOT_STAGE1 || this == HOT_STAGE2 || this == HOT_LETHAL;
    }

    public boolean isCold() {
        return this == COLD_STAGE1 || this == COLD_STAGE2 || this == COLD_LETHAL;
    }
}
