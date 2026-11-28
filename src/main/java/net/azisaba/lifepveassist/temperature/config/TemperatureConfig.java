package net.azisaba.lifepveassist.temperature.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * config.yml の {@code temperature:} セクションを不変 (immutable) スナップショットとして保持する設定クラス。
 * リロード時は新しいインスタンスを生成して {@code volatile} 参照をアトミックに差し替えます。
 */
public record TemperatureConfig(
        String serverName,
        Set<String> enabledWorlds,
        double defaultTemp,
        double baseMaxLimit,
        double baseMinLimit,
        int recoveryDelaySeconds,
        double recoveryRatePerSecond,
        int saveIntervalSeconds,
        long joinLoadMaxWaitMillis,
        long lockTimeoutMillis,
        int stage1ImmunityPieces,
        int stage2DamageImmunityPieces,
        double stage1Offset,
        boolean slownessEnabled,
        int slownessAmplifier,
        boolean blindnessEnabled,
        int blindnessAmplifier,
        int effectDurationTicks,
        double stage2Offset,
        boolean damageEnabled,
        double damageAmount,
        int damageIntervalSeconds,
        boolean damageBypassArmor,
        boolean heatFireOverlay,
        boolean coldFreezeOverlay,
        int coldFreezeTicks,
        String heatDeathMessage,
        String coldDeathMessage,
        boolean warningEnabled,
        double warningOffset,
        int warningIntervalSeconds,
        int warningFadeInTicks,
        int warningStayTicks,
        int warningFadeOutTicks,
        String heatWarningTitle,
        String heatWarningSubtitle,
        String heatWarningSound,
        float heatWarningSoundVolume,
        float heatWarningSoundPitch,
        String coldWarningTitle,
        String coldWarningSubtitle,
        String coldWarningSound,
        float coldWarningSoundVolume,
        float coldWarningSoundPitch,
        boolean bossBarEnabled,
        boolean bossBarHideWhenNormal,
        BossBar.Overlay bossBarOverlay,
        String bossBarTitleFormat,
        String statusNormal,
        String statusHotStage1,
        String statusHotStage2,
        String statusColdStage1,
        String statusColdStage2,
        int previewDurationSeconds
) {

    public static TemperatureConfig fromSection(ConfigurationSection cfg, Logger logger) {
        if (cfg == null) {
            return defaults();
        }

        String serverName = cfg.getString("server-name", "default");
        if (serverName == null || serverName.isBlank()) {
            serverName = "default";
        } else {
            serverName = serverName.trim();
        }

        Set<String> worlds = new HashSet<>();
        for (String w : cfg.getStringList("enabled-worlds")) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }

        double defaultTemp = cfg.getDouble("default", 20.0);
        double baseMaxLimit = cfg.getDouble("base-max-limit", 100.0);
        double baseMinLimit = cfg.getDouble("base-min-limit", -80.0);
        if (!Double.isFinite(defaultTemp) || !Double.isFinite(baseMaxLimit) || !Double.isFinite(baseMinLimit)
                || baseMinLimit >= baseMaxLimit || defaultTemp <= baseMinLimit || defaultTemp >= baseMaxLimit) {
            warn(logger, "temperature の基本温度範囲 (default/base-min-limit/base-max-limit) が不正なため既定値 (20 / -80 / 100) を使用します。");
            defaultTemp = 20.0;
            baseMinLimit = -80.0;
            baseMaxLimit = 100.0;
        }

        int recoveryDelay = cfg.getInt("recovery.delay-seconds", 10);
        if (recoveryDelay < 0) {
            warn(logger, "recovery.delay-seconds は 0 以上を指定してください。既定値 (10) を使用します。");
            recoveryDelay = 10;
        }
        double recoveryRate = cfg.getDouble("recovery.rate-per-second", 20.0);
        if (!Double.isFinite(recoveryRate) || recoveryRate < 0.0) {
            warn(logger, "recovery.rate-per-second が不正なため既定値 (20.0) を使用します。");
            recoveryRate = 20.0;
        }

        int saveInterval = Math.max(10, cfg.getInt("storage.save-interval-seconds", 300));
        long joinMaxWait = Math.max(0L, cfg.getLong("storage.join-load-max-wait-millis", 2000L));
        long lockTimeoutSec = Math.max(3L, cfg.getLong("storage.lock-timeout-seconds", 10L));

        int stage1Immunity = Math.max(1, cfg.getInt("armor.stage1-immunity-pieces", 2));
        int stage2Immunity = Math.max(1, cfg.getInt("armor.stage2-damage-immunity-pieces", 4));

        double stage1Offset = positiveOrDefault(cfg.getDouble("penalties.stage1.offset-from-lethal", 40.0), 40.0, logger, "penalties.stage1.offset-from-lethal");
        boolean slownessEnabled = cfg.getBoolean("penalties.stage1.slowness.enabled", true);
        int slownessAmp = Math.max(0, cfg.getInt("penalties.stage1.slowness.amplifier", 1));
        boolean blindnessEnabled = cfg.getBoolean("penalties.stage1.blindness.enabled", true);
        int blindnessAmp = Math.max(0, cfg.getInt("penalties.stage1.blindness.amplifier", 0));
        int effectDuration = Math.max(20, cfg.getInt("penalties.stage1.effect-duration-ticks", 60));

        double stage2Offset = positiveOrDefault(cfg.getDouble("penalties.stage2.offset-from-lethal", 20.0), 20.0, logger, "penalties.stage2.offset-from-lethal");
        boolean damageEnabled = cfg.getBoolean("penalties.stage2.damage.enabled", true);
        double damageAmount = positiveOrDefault(cfg.getDouble("penalties.stage2.damage.amount", 2.0), 2.0, logger, "penalties.stage2.damage.amount");
        int damageInterval = Math.max(1, cfg.getInt("penalties.stage2.damage.interval-seconds", 1));
        boolean damageBypassArmor = cfg.getBoolean("penalties.stage2.damage.bypass-armor", true);
        boolean heatFireOverlay = cfg.contains("penalties.stage2.visual-effects.heat-red-overlay")
                ? cfg.getBoolean("penalties.stage2.visual-effects.heat-red-overlay", true)
                : cfg.getBoolean("penalties.stage2.visual-effects.heat-fire-overlay", true);
        boolean coldFreezeOverlay = cfg.getBoolean("penalties.stage2.visual-effects.cold-freeze-overlay", true);
        int coldFreezeTicks = Math.max(0, cfg.getInt("penalties.stage2.visual-effects.cold-freeze-ticks", 140));

        String heatDeathMsg = cfg.getString("penalties.lethal.heat-death-message",
                "<red><bold><player> は灼熱の高温に耐えられず力尽きた... (<temp>℃)");
        String coldDeathMsg = cfg.getString("penalties.lethal.cold-death-message",
                "<aqua><bold><player> は極寒の低温に耐えられず凍え死んだ... (<temp>℃)");

        boolean warningEnabled = cfg.getBoolean("warning.enabled", true);
        double warningOffset = positiveOrDefault(cfg.getDouble("warning.offset-from-lethal", 20.0), 20.0, logger, "warning.offset-from-lethal");
        int warningInterval = Math.max(1, cfg.getInt("warning.interval-seconds", 5));
        int warnFadeIn = Math.max(0, cfg.getInt("warning.fade-in-ticks", 5));
        int warnStay = Math.max(1, cfg.getInt("warning.stay-ticks", 30));
        int warnFadeOut = Math.max(0, cfg.getInt("warning.fade-out-ticks", 10));
        String heatWarnTitle = cfg.getString("warning.heat.title", "<red><bold>⚠ 高温警告 ⚠");
        String heatWarnSub = cfg.getString("warning.heat.subtitle", "<gold>現在 <temp>℃ <gray>(即死ライン: <max>℃)");
        String heatWarnSound = cfg.getString("warning.heat.sound", "minecraft:block.note_block.pling");
        float heatWarnVol = (float) cfg.getDouble("warning.heat.sound-volume", 1.0);
        float heatWarnPitch = (float) cfg.getDouble("warning.heat.sound-pitch", 0.6);
        String coldWarnTitle = cfg.getString("warning.cold.title", "<aqua><bold>⚠ 低温警告 ⚠");
        String coldWarnSub = cfg.getString("warning.cold.subtitle", "<white>現在 <temp>℃ <gray>(即死ライン: <min>℃)");
        String coldWarnSound = cfg.getString("warning.cold.sound", "minecraft:block.note_block.pling");
        float coldWarnVol = (float) cfg.getDouble("warning.cold.sound-volume", 1.0);
        float coldWarnPitch = (float) cfg.getDouble("warning.cold.sound-pitch", 0.5);

        boolean bossBarEnabled = cfg.getBoolean("bossbar.enabled", true);
        boolean bossBarHideWhenNormal = cfg.getBoolean("bossbar.hide-when-normal", false);
        BossBar.Overlay overlay;
        try {
            overlay = BossBar.Overlay.valueOf(cfg.getString("bossbar.overlay", "NOTCHED_10").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            overlay = BossBar.Overlay.NOTCHED_10;
        }
        String bossBarFormat = cfg.getString("bossbar.title-format",
                "<status> <white>温度: <temp_color><temp>℃ <gray>[<aqua><min>℃ <gray>～ <red><max>℃<gray>]");
        String statusNormal = cfg.getString("bossbar.status-labels.normal", "<green>[安定]");
        String statusHot1 = cfg.getString("bossbar.status-labels.hot-stage1", "<yellow>[高温注意]");
        String statusHot2 = cfg.getString("bossbar.status-labels.hot-stage2", "<red><bold>[高温危険]");
        String statusCold1 = cfg.getString("bossbar.status-labels.cold-stage1", "<aqua>[低温注意]");
        String statusCold2 = cfg.getString("bossbar.status-labels.cold-stage2", "<blue><bold>[低温危険]");

        int previewDuration = Math.max(1, cfg.getInt("zones.preview-duration-seconds", 10));

        return new TemperatureConfig(
                serverName,
                Collections.unmodifiableSet(worlds),
                defaultTemp,
                baseMaxLimit,
                baseMinLimit,
                recoveryDelay,
                recoveryRate,
                saveInterval,
                joinMaxWait,
                lockTimeoutSec * 1000L,
                stage1Immunity,
                stage2Immunity,
                stage1Offset,
                slownessEnabled,
                slownessAmp,
                blindnessEnabled,
                blindnessAmp,
                effectDuration,
                stage2Offset,
                damageEnabled,
                damageAmount,
                damageInterval,
                damageBypassArmor,
                heatFireOverlay,
                coldFreezeOverlay,
                coldFreezeTicks,
                heatDeathMsg,
                coldDeathMsg,
                warningEnabled,
                warningOffset,
                warningInterval,
                warnFadeIn,
                warnStay,
                warnFadeOut,
                heatWarnTitle,
                heatWarnSub,
                heatWarnSound,
                heatWarnVol,
                heatWarnPitch,
                coldWarnTitle,
                coldWarnSub,
                coldWarnSound,
                coldWarnVol,
                coldWarnPitch,
                bossBarEnabled,
                bossBarHideWhenNormal,
                overlay,
                bossBarFormat,
                statusNormal,
                statusHot1,
                statusHot2,
                statusCold1,
                statusCold2,
                previewDuration
        );
    }

    public static TemperatureConfig defaults() {
        return new TemperatureConfig(
                "default", Set.of(), 20.0, 100.0, -80.0,
                10, 20.0, 300, 2000L, 10_000L,
                2, 4,
                40.0, true, 1, true, 0, 60,
                20.0, true, 2.0, 1, true, true, true, 140,
                "<red><bold><player> は灼熱の高温に耐えられず力尽きた... (<temp>℃)",
                "<aqua><bold><player> は極寒の低温に耐えられず凍え死んだ... (<temp>℃)",
                true, 20.0, 5, 5, 30, 10,
                "<red><bold>⚠ 高温警告 ⚠", "<gold>現在 <temp>℃ <gray>(即死ライン: <max>℃)",
                "minecraft:block.note_block.pling", 1.0f, 0.6f,
                "<aqua><bold>⚠ 低温警告 ⚠", "<white>現在 <temp>℃ <gray>(即死ライン: <min>℃)",
                "minecraft:block.note_block.pling", 1.0f, 0.5f,
                true, false, BossBar.Overlay.NOTCHED_10,
                "<status> <white>温度: <temp_color><temp>℃ <gray>[<aqua><min>℃ <gray>～ <red><max>℃<gray>]",
                "<green>[安定]", "<yellow>[高温注意]", "<red><bold>[高温危険]", "<aqua>[低温注意]", "<blue><bold>[低温危険]",
                10
        );
    }

    public boolean isFeatureEnabled() {
        return !enabledWorlds.isEmpty();
    }

    public boolean isEnabledWorld(World world) {
        return world != null && isEnabledWorld(world.getName());
    }

    public boolean isEnabledWorld(String worldName) {
        return worldName != null && enabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    private static double positiveOrDefault(double val, double def, Logger logger, String key) {
        if (!Double.isFinite(val) || val < 0.0) {
            warn(logger, key + " は 0 以上の数値である必要があります。既定値 (" + def + ") を使用します。");
            return def;
        }
        return val;
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) {
            logger.warning("[Temperature] " + message);
        }
    }
}
