package net.azisaba.lifepveassist.temperature.model;

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
}
