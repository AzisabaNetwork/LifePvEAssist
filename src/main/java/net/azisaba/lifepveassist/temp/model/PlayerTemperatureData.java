package net.azisaba.lifepveassist.temp.model;

import java.util.UUID;

/**
 * オンライン・キャッシュ中プレイヤーごとの温度ランタイム状態。
 */
public class PlayerTemperatureData {

    private final UUID uuid;
    private double temperature;
    private long lastExternalChangeMillis;
    private long lastWarningMillis;
    private long lastDamageMillis;
    private boolean visualFireApplied;
    private boolean freezeOverlayApplied;

    public PlayerTemperatureData(UUID uuid, double initialTemperature, long nowMillis) {
        this.uuid = uuid;
        this.temperature = initialTemperature;
        this.lastExternalChangeMillis = nowMillis;
        this.lastWarningMillis = 0L;
        this.lastDamageMillis = 0L;
        this.visualFireApplied = false;
        this.freezeOverlayApplied = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public long getLastExternalChangeMillis() {
        return lastExternalChangeMillis;
    }

    public void setLastExternalChangeMillis(long lastExternalChangeMillis) {
        this.lastExternalChangeMillis = lastExternalChangeMillis;
    }

    public long getLastWarningMillis() {
        return lastWarningMillis;
    }

    public void setLastWarningMillis(long lastWarningMillis) {
        this.lastWarningMillis = lastWarningMillis;
    }

    public long getLastDamageMillis() {
        return lastDamageMillis;
    }

    public void setLastDamageMillis(long lastDamageMillis) {
        this.lastDamageMillis = lastDamageMillis;
    }

    public boolean isVisualFireApplied() {
        return visualFireApplied;
    }

    public void setVisualFireApplied(boolean visualFireApplied) {
        this.visualFireApplied = visualFireApplied;
    }

    public boolean isFreezeOverlayApplied() {
        return freezeOverlayApplied;
    }

    public void setFreezeOverlayApplied(boolean freezeOverlayApplied) {
        this.freezeOverlayApplied = freezeOverlayApplied;
    }
}
