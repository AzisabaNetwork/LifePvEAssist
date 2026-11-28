package net.azisaba.lifepveassist.temperature.model;

import org.bukkit.WorldBorder;

import java.util.UUID;

/**
 * オンラインプレイヤーごとの温度ランタイム状態。
 */
public class PlayerTemperatureState {

    public enum DeathCause {
        NONE,
        HEAT,
        COLD
    }

    private final UUID uuid;
    private double temperature;
    private boolean loaded;
    private boolean dirty;
    private boolean bypassEnabled;

    private boolean lethalPending;
    private DeathCause pendingDeathCause = DeathCause.NONE;
    private double pendingDeathTemperature;

    private long lastExternalChangeMillis;
    private long lastWarningMillis;
    private long lastDamageMillis;

    private String currentZoneKey;
    private int zoneStaySeconds;

    private boolean visualFireApplied;
    private WorldBorder previousWorldBorder;
    private boolean freezeOverlayApplied;

    public PlayerTemperatureState(UUID uuid, double initialTemperature, long nowMillis) {
        this.uuid = uuid;
        this.temperature = initialTemperature;
        this.loaded = false;
        this.dirty = false;
        this.bypassEnabled = false;
        this.lastExternalChangeMillis = nowMillis;
    }

    public UUID getUuid() { return uuid; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }

    public boolean isBypassEnabled() { return bypassEnabled; }
    public void setBypassEnabled(boolean bypassEnabled) { this.bypassEnabled = bypassEnabled; }

    public boolean isLethalPending() { return lethalPending; }
    public void setLethalPending(boolean lethalPending) { this.lethalPending = lethalPending; }

    public DeathCause getPendingDeathCause() { return pendingDeathCause; }
    public void setPendingDeathCause(DeathCause pendingDeathCause) { this.pendingDeathCause = pendingDeathCause; }

    public double getPendingDeathTemperature() { return pendingDeathTemperature; }
    public void setPendingDeathTemperature(double pendingDeathTemperature) { this.pendingDeathTemperature = pendingDeathTemperature; }

    public void clearPendingDeath() {
        this.lethalPending = false;
        this.pendingDeathCause = DeathCause.NONE;
        this.pendingDeathTemperature = 0.0;
    }

    public long getLastExternalChangeMillis() { return lastExternalChangeMillis; }
    public void setLastExternalChangeMillis(long lastExternalChangeMillis) { this.lastExternalChangeMillis = lastExternalChangeMillis; }

    public long getLastWarningMillis() { return lastWarningMillis; }
    public void setLastWarningMillis(long lastWarningMillis) { this.lastWarningMillis = lastWarningMillis; }

    public long getLastDamageMillis() { return lastDamageMillis; }
    public void setLastDamageMillis(long lastDamageMillis) { this.lastDamageMillis = lastDamageMillis; }

    public String getCurrentZoneKey() { return currentZoneKey; }

    public int tickZoneStay(String zoneKey) {
        if (zoneKey == null) {
            this.currentZoneKey = null;
            this.zoneStaySeconds = 0;
            return 0;
        }
        if (zoneKey.equals(this.currentZoneKey)) {
            this.zoneStaySeconds++;
        } else {
            this.currentZoneKey = zoneKey;
            this.zoneStaySeconds = 1;
        }
        return this.zoneStaySeconds;
    }

    public void resetZoneStay() {
        this.currentZoneKey = null;
        this.zoneStaySeconds = 0;
    }

    public boolean isVisualFireApplied() { return visualFireApplied; }
    public void setVisualFireApplied(boolean visualFireApplied) { this.visualFireApplied = visualFireApplied; }

    public WorldBorder getPreviousWorldBorder() { return previousWorldBorder; }
    public void setPreviousWorldBorder(WorldBorder previousWorldBorder) { this.previousWorldBorder = previousWorldBorder; }

    public boolean isFreezeOverlayApplied() { return freezeOverlayApplied; }
    public void setFreezeOverlayApplied(boolean freezeOverlayApplied) { this.freezeOverlayApplied = freezeOverlayApplied; }
}
