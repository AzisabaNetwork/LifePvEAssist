package net.azisaba.lifepveassist.temp.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * config.yml の全設定値を読み込み、型安全に保持するマネージャ。
 */
public class ConfigManager {

    public record MythicItemOverride(String type, double bonus, int pieces) {
    }

    private final JavaPlugin plugin;
    private final File configFile;

    private Set<String> enabledWorlds = Set.of();

    // temperature
    private double defaultTemp = 20.0;
    private double baseMaxLimit = 100.0;
    private double baseMinLimit = -80.0;
    private int recoveryDelaySeconds = 10;
    private double recoveryRatePerSecond = 20.0;
    private boolean resetTimerOnZoneChange = true;

    // lifecycle
    private boolean resetOnDeath = true;
    private boolean ignoreCreativeSpectator = true;
    private int saveIntervalSeconds = 300;

    // armor
    private double defaultLimitBonusPerPiece = 50.0;
    private int stage1ImmunityPieces = 2;
    private int stage2DamageImmunityPieces = 4;
    private String heatNbtKey = "lifetemp_heat";
    private String coldNbtKey = "lifetemp_cold";
    private Map<String, MythicItemOverride> mythicItemOverrides = Map.of();

    // penalties - stage1
    private double stage1Offset = 40.0;
    private boolean slownessEnabled = true;
    private int slownessAmplifier = 1;
    private boolean blindnessEnabled = true;
    private int blindnessAmplifier = 0;
    private int effectDurationTicks = 60;

    // penalties - stage2
    private double stage2Offset = 20.0;
    private boolean damageEnabled = true;
    private double damageAmount = 2.0;
    private int damageIntervalSeconds = 1;
    private boolean damageBypassArmor = false;
    private boolean heatFireOverlay = true;
    private boolean coldFreezeOverlay = true;
    private int coldFreezeTicks = 140;

    // penalties - lethal
    private String heatDeathMessage = "<red><bold>灼熱の高温に耐えられず力尽きた... (<temp>℃)";
    private String coldDeathMessage = "<aqua><bold>極寒の低温に耐えられず凍え死んだ... (<temp>℃)";

    // warning
    private boolean warningEnabled = true;
    private double warningOffset = 20.0;
    private int warningIntervalSeconds = 5;
    private int warningFadeInTicks = 5;
    private int warningStayTicks = 30;
    private int warningFadeOutTicks = 10;
    private String heatWarningTitle = "<red><bold>⚠ 高温警告 ⚠";
    private String heatWarningSubtitle = "<gold>現在 <temp>℃ <gray>(即死ライン: <max>℃)";
    private String heatWarningSound = "minecraft:block.note_block.pling";
    private float heatWarningSoundVolume = 1.0f;
    private float heatWarningSoundPitch = 0.6f;
    private String coldWarningTitle = "<aqua><bold>⚠ 低温警告 ⚠";
    private String coldWarningSubtitle = "<white>現在 <temp>℃ <gray>(即死ライン: <min>℃)";
    private String coldWarningSound = "minecraft:block.note_block.pling";
    private float coldWarningSoundVolume = 1.0f;
    private float coldWarningSoundPitch = 0.5f;

    // bossbar
    private boolean bossBarEnabled = true;
    private boolean bossBarHideWhenNormal = false;
    private BossBar.Overlay bossBarOverlay = BossBar.Overlay.NOTCHED_10;
    private String bossBarTitleFormat = "<status> <white>温度: <temp_color><temp>℃ <gray>[<aqua><min>℃ <gray>～ <red><max>℃<gray>]";
    private String statusNormal = "<green>[安定]";
    private String statusHotStage1 = "<yellow>[高温注意]";
    private String statusHotStage2 = "<red><bold>[高温危険]";
    private String statusColdStage1 = "<aqua>[低温注意]";
    private String statusColdStage2 = "<blue><bold>[低温危険]";

    // database
    private String databaseType = "sqlite";
    private String databaseSqliteFile = "temp/playerdata.db";
    private String databaseHost = "localhost";
    private int databasePort = 3306;
    private String databaseName = "lifepveassist";
    private String databaseUsername = "root";
    private String databasePassword = "";
    private String databaseTablePrefix = "lifetemp_";
    private int databasePoolSize = 4;
    private boolean databaseUseSsl = false;

    // zones
    private int previewDurationSeconds = 10;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(new File(plugin.getDataFolder(), "temp"), "config.yml");
        reload();
    }

    public void reload() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        if (!configFile.exists()) {
            plugin.saveResource("temp/config.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        Set<String> worlds = new HashSet<>();
        for (String w : cfg.getStringList("enabled-worlds")) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.enabledWorlds = Collections.unmodifiableSet(worlds);

        // temperature
        this.defaultTemp = cfg.getDouble("temperature.default", 20.0);
        this.baseMaxLimit = cfg.getDouble("temperature.base-max-limit", 100.0);
        this.baseMinLimit = cfg.getDouble("temperature.base-min-limit", -80.0);
        this.recoveryDelaySeconds = Math.max(0, cfg.getInt("temperature.recovery.delay-seconds", 10));
        this.recoveryRatePerSecond = Math.max(0.0, cfg.getDouble("temperature.recovery.rate-per-second", 20.0));
        this.resetTimerOnZoneChange = cfg.getBoolean("temperature.recovery.reset-timer-on-zone-change", true);

        // lifecycle
        this.resetOnDeath = cfg.getBoolean("lifecycle.reset-on-death", true);
        this.ignoreCreativeSpectator = cfg.getBoolean("lifecycle.ignore-creative-spectator", true);
        this.saveIntervalSeconds = Math.max(10, cfg.getInt("lifecycle.save-interval-seconds", 300));

        // armor
        this.defaultLimitBonusPerPiece = Math.max(0.0, cfg.getDouble("armor.default-limit-bonus-per-piece", 50.0));
        this.stage1ImmunityPieces = Math.max(1, cfg.getInt("armor.stage1-immunity-pieces", 2));
        this.stage2DamageImmunityPieces = Math.max(1, cfg.getInt("armor.stage2-damage-immunity-pieces", 4));
        this.heatNbtKey = cfg.getString("armor.nbt-keys.heat", "lifetemp_heat");
        this.coldNbtKey = cfg.getString("armor.nbt-keys.cold", "lifetemp_cold");

        Map<String, MythicItemOverride> overrides = new HashMap<>();
        ConfigurationSection ovSection = cfg.getConfigurationSection("armor.mythic-item-overrides");
        if (ovSection != null) {
            for (String itemId : ovSection.getKeys(false)) {
                ConfigurationSection sub = ovSection.getConfigurationSection(itemId);
                if (sub != null) {
                    String type = sub.getString("type", "HEAT").toUpperCase(Locale.ROOT);
                    double bonus = sub.getDouble("bonus", defaultLimitBonusPerPiece);
                    int pieces = Math.max(0, sub.getInt("pieces", 1));
                    overrides.put(itemId, new MythicItemOverride(type, bonus, pieces));
                }
            }
        }
        this.mythicItemOverrides = Collections.unmodifiableMap(overrides);

        // penalties - stage1
        this.stage1Offset = Math.max(0.0, cfg.getDouble("penalties.stage1.offset-from-lethal", 40.0));
        this.slownessEnabled = cfg.getBoolean("penalties.stage1.slowness.enabled", true);
        this.slownessAmplifier = Math.max(0, cfg.getInt("penalties.stage1.slowness.amplifier", 1));
        this.blindnessEnabled = cfg.getBoolean("penalties.stage1.blindness.enabled", true);
        this.blindnessAmplifier = Math.max(0, cfg.getInt("penalties.stage1.blindness.amplifier", 0));
        this.effectDurationTicks = Math.max(20, cfg.getInt("penalties.stage1.effect-duration-ticks", 60));

        // penalties - stage2
        this.stage2Offset = Math.max(0.0, cfg.getDouble("penalties.stage2.offset-from-lethal", 20.0));
        this.damageEnabled = cfg.getBoolean("penalties.stage2.damage.enabled", true);
        this.damageAmount = Math.max(0.0, cfg.getDouble("penalties.stage2.damage.amount", 2.0));
        this.damageIntervalSeconds = Math.max(1, cfg.getInt("penalties.stage2.damage.interval-seconds", 1));
        this.damageBypassArmor = cfg.getBoolean("penalties.stage2.damage.bypass-armor", false);
        this.heatFireOverlay = cfg.getBoolean("penalties.stage2.visual-effects.heat-fire-overlay", true);
        this.coldFreezeOverlay = cfg.getBoolean("penalties.stage2.visual-effects.cold-freeze-overlay", true);
        this.coldFreezeTicks = Math.max(0, cfg.getInt("penalties.stage2.visual-effects.cold-freeze-ticks", 140));

        // penalties - lethal
        this.heatDeathMessage = cfg.getString("penalties.lethal.heat-death-message", this.heatDeathMessage);
        this.coldDeathMessage = cfg.getString("penalties.lethal.cold-death-message", this.coldDeathMessage);

        // warning
        this.warningEnabled = cfg.getBoolean("warning.enabled", true);
        this.warningOffset = Math.max(0.0, cfg.getDouble("warning.offset-from-lethal", 20.0));
        this.warningIntervalSeconds = Math.max(1, cfg.getInt("warning.interval-seconds", 5));
        this.warningFadeInTicks = Math.max(0, cfg.getInt("warning.fade-in-ticks", 5));
        this.warningStayTicks = Math.max(1, cfg.getInt("warning.stay-ticks", 30));
        this.warningFadeOutTicks = Math.max(0, cfg.getInt("warning.fade-out-ticks", 10));
        this.heatWarningTitle = cfg.getString("warning.heat.title", this.heatWarningTitle);
        this.heatWarningSubtitle = cfg.getString("warning.heat.subtitle", this.heatWarningSubtitle);
        this.heatWarningSound = cfg.getString("warning.heat.sound", this.heatWarningSound);
        this.heatWarningSoundVolume = (float) cfg.getDouble("warning.heat.sound-volume", 1.0);
        this.heatWarningSoundPitch = (float) cfg.getDouble("warning.heat.sound-pitch", 0.6);
        this.coldWarningTitle = cfg.getString("warning.cold.title", this.coldWarningTitle);
        this.coldWarningSubtitle = cfg.getString("warning.cold.subtitle", this.coldWarningSubtitle);
        this.coldWarningSound = cfg.getString("warning.cold.sound", this.coldWarningSound);
        this.coldWarningSoundVolume = (float) cfg.getDouble("warning.cold.sound-volume", 1.0);
        this.coldWarningSoundPitch = (float) cfg.getDouble("warning.cold.sound-pitch", 0.5);

        // bossbar
        this.bossBarEnabled = cfg.getBoolean("bossbar.enabled", true);
        this.bossBarHideWhenNormal = cfg.getBoolean("bossbar.hide-when-normal", false);
        String overlayName = cfg.getString("bossbar.overlay", "NOTCHED_10").toUpperCase(Locale.ROOT);
        try {
            this.bossBarOverlay = BossBar.Overlay.valueOf(overlayName);
        } catch (IllegalArgumentException e) {
            this.bossBarOverlay = BossBar.Overlay.NOTCHED_10;
        }
        this.bossBarTitleFormat = cfg.getString("bossbar.title-format", this.bossBarTitleFormat);
        this.statusNormal = cfg.getString("bossbar.status-labels.normal", this.statusNormal);
        this.statusHotStage1 = cfg.getString("bossbar.status-labels.hot-stage1", this.statusHotStage1);
        this.statusHotStage2 = cfg.getString("bossbar.status-labels.hot-stage2", this.statusHotStage2);
        this.statusColdStage1 = cfg.getString("bossbar.status-labels.cold-stage1", this.statusColdStage1);
        this.statusColdStage2 = cfg.getString("bossbar.status-labels.cold-stage2", this.statusColdStage2);

        // database
        this.databaseType = cfg.getString("database.type", "sqlite");
        this.databaseSqliteFile = cfg.getString("database.sqlite-file", "temp/playerdata.db");
        this.databaseHost = cfg.getString("database.host", "localhost");
        this.databasePort = cfg.getInt("database.port", 3306);
        this.databaseName = cfg.getString("database.database", "lifepveassist");
        this.databaseUsername = cfg.getString("database.username", "root");
        this.databasePassword = cfg.getString("database.password", "");
        this.databaseTablePrefix = cfg.getString("database.table-prefix", "lifetemp_");
        this.databasePoolSize = Math.max(1, cfg.getInt("database.pool-size", 4));
        this.databaseUseSsl = cfg.getBoolean("database.use-ssl", false);

        // zones
        this.previewDurationSeconds = Math.max(1, cfg.getInt("zones.preview-duration-seconds", 10));
    }

    public boolean isEnabledWorld(World world) {
        return world != null && isEnabledWorld(world.getName());
    }

    public boolean isEnabledWorld(String worldName) {
        return worldName != null && enabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("lifepveassist.temp.admin");
    }

    public boolean isBypass(Player player) {
        return player.hasPermission("lifepveassist.temp.bypass");
    }

    public Set<String> getEnabledWorlds() { return enabledWorlds; }
    public double getDefaultTemp() { return defaultTemp; }
    public double getBaseMaxLimit() { return baseMaxLimit; }
    public double getBaseMinLimit() { return baseMinLimit; }
    public int getRecoveryDelaySeconds() { return recoveryDelaySeconds; }
    public double getRecoveryRatePerSecond() { return recoveryRatePerSecond; }
    public boolean isResetTimerOnZoneChange() { return resetTimerOnZoneChange; }
    public boolean isResetOnDeath() { return resetOnDeath; }
    public boolean isIgnoreCreativeSpectator() { return ignoreCreativeSpectator; }
    public int getSaveIntervalSeconds() { return saveIntervalSeconds; }
    public double getDefaultLimitBonusPerPiece() { return defaultLimitBonusPerPiece; }
    public int getStage1ImmunityPieces() { return stage1ImmunityPieces; }
    public int getStage2DamageImmunityPieces() { return stage2DamageImmunityPieces; }
    public String getHeatNbtKey() { return heatNbtKey; }
    public String getColdNbtKey() { return coldNbtKey; }
    public Map<String, MythicItemOverride> getMythicItemOverrides() { return mythicItemOverrides; }
    public double getStage1Offset() { return stage1Offset; }
    public boolean isSlownessEnabled() { return slownessEnabled; }
    public int getSlownessAmplifier() { return slownessAmplifier; }
    public boolean isBlindnessEnabled() { return blindnessEnabled; }
    public int getBlindnessAmplifier() { return blindnessAmplifier; }
    public int getEffectDurationTicks() { return effectDurationTicks; }
    public double getStage2Offset() { return stage2Offset; }
    public boolean isDamageEnabled() { return damageEnabled; }
    public double getDamageAmount() { return damageAmount; }
    public int getDamageIntervalSeconds() { return damageIntervalSeconds; }
    public boolean isDamageBypassArmor() { return damageBypassArmor; }
    public boolean isHeatFireOverlay() { return heatFireOverlay; }
    public boolean isColdFreezeOverlay() { return coldFreezeOverlay; }
    public int getColdFreezeTicks() { return coldFreezeTicks; }
    public String getHeatDeathMessage() { return heatDeathMessage; }
    public String getColdDeathMessage() { return coldDeathMessage; }
    public boolean isWarningEnabled() { return warningEnabled; }
    public double getWarningOffset() { return warningOffset; }
    public int getWarningIntervalSeconds() { return warningIntervalSeconds; }
    public int getWarningFadeInTicks() { return warningFadeInTicks; }
    public int getWarningStayTicks() { return warningStayTicks; }
    public int getWarningFadeOutTicks() { return warningFadeOutTicks; }
    public String getHeatWarningTitle() { return heatWarningTitle; }
    public String getHeatWarningSubtitle() { return heatWarningSubtitle; }
    public String getHeatWarningSound() { return heatWarningSound; }
    public float getHeatWarningSoundVolume() { return heatWarningSoundVolume; }
    public float getHeatWarningSoundPitch() { return heatWarningSoundPitch; }
    public String getColdWarningTitle() { return coldWarningTitle; }
    public String getColdWarningSubtitle() { return coldWarningSubtitle; }
    public String getColdWarningSound() { return coldWarningSound; }
    public float getColdWarningSoundVolume() { return coldWarningSoundVolume; }
    public float getColdWarningSoundPitch() { return coldWarningSoundPitch; }
    public boolean isBossBarEnabled() { return bossBarEnabled; }
    public boolean isBossBarHideWhenNormal() { return bossBarHideWhenNormal; }
    public BossBar.Overlay getBossBarOverlay() { return bossBarOverlay; }
    public String getBossBarTitleFormat() { return bossBarTitleFormat; }
    public String getStatusNormal() { return statusNormal; }
    public String getStatusHotStage1() { return statusHotStage1; }
    public String getStatusHotStage2() { return statusHotStage2; }
    public String getStatusColdStage1() { return statusColdStage1; }
    public String getStatusColdStage2() { return statusColdStage2; }
    public String getDatabaseType() { return databaseType; }
    public String getDatabaseSqliteFile() { return databaseSqliteFile; }
    public String getDatabaseHost() { return databaseHost; }
    public int getDatabasePort() { return databasePort; }
    public String getDatabaseName() { return databaseName; }
    public String getDatabaseUsername() { return databaseUsername; }
    public String getDatabasePassword() { return databasePassword; }
    public String getDatabaseTablePrefix() { return databaseTablePrefix; }
    public int getDatabasePoolSize() { return databasePoolSize; }
    public boolean isDatabaseUseSsl() { return databaseUseSsl; }
    public int getPreviewDurationSeconds() { return previewDurationSeconds; }
}
