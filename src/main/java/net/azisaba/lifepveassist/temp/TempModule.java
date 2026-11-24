package net.azisaba.lifepveassist.temp;

import net.azisaba.lifepveassist.temp.armor.ArmorInspector;
import net.azisaba.lifepveassist.temp.command.LifeTempCommand;
import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import net.azisaba.lifepveassist.temp.listener.PlayerLifecycleListener;
import net.azisaba.lifepveassist.temp.mythic.MythicMobsHook;
import net.azisaba.lifepveassist.temp.storage.TemperatureStorage;
import net.azisaba.lifepveassist.temp.ui.BossBarController;
import net.azisaba.lifepveassist.temp.ui.WarningController;
import net.azisaba.lifepveassist.temp.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 裏上級用 温度管理モジュール（LifeTempManager 統合エントリポイント）。
 * {@link #register(JavaPlugin)} / {@link #shutdown()} により LifePvEAssist のライフサイクルへ接続されます。
 */
public final class TempModule {

    private static ConfigManager configManager;
    private static ArmorInspector armorInspector;
    private static TemperatureStorage storage;
    private static BossBarController bossBarController;
    private static WarningController warningController;
    private static TemperatureManager temperatureManager;
    private static ZoneManager zoneManager;

    private TempModule() {
    }

    public static void register(JavaPlugin plugin) {
        configManager = new ConfigManager(plugin);
        armorInspector = new ArmorInspector(plugin, configManager);
        storage = new TemperatureStorage(plugin, configManager);
        bossBarController = new BossBarController(configManager);
        warningController = new WarningController(configManager);
        temperatureManager = new TemperatureManager(
                plugin, configManager, armorInspector, storage, bossBarController, warningController
        );
        zoneManager = new ZoneManager(plugin, configManager, temperatureManager);

        Bukkit.getPluginManager().registerEvents(new PlayerLifecycleListener(temperatureManager, zoneManager), plugin);

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            MythicMobsHook mythicHook = new MythicMobsHook(plugin, configManager, temperatureManager);
            armorInspector.setExternalItemReader(mythicHook);
            Bukkit.getPluginManager().registerEvents(mythicHook, plugin);
            plugin.getLogger().info("[Temp] MythicMobs integration enabled (tempadd / tempset / tempreset / tempinrange).");
        }

        PluginCommand cmd = plugin.getCommand("lifetemp");
        if (cmd != null) {
            LifeTempCommand executor = new LifeTempCommand(
                    configManager, temperatureManager, armorInspector, zoneManager, TempModule::reload
            );
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        temperatureManager.start();
        zoneManager.start();
        plugin.getLogger().info("[Temp] Temperature management module enabled.");
    }

    public static void shutdown() {
        if (zoneManager != null) {
            zoneManager.shutdown();
            zoneManager = null;
        }
        if (temperatureManager != null) {
            temperatureManager.shutdown();
            temperatureManager = null;
        }
        configManager = null;
        armorInspector = null;
        storage = null;
        bossBarController = null;
        warningController = null;
    }

    public static void reload() {
        if (configManager != null) {
            configManager.reload();
        }
        if (zoneManager != null) {
            zoneManager.load();
        }
        if (temperatureManager != null) {
            temperatureManager.restartAutoSave();
        }
    }

    public static TemperatureManager getTemperatureManager() {
        return temperatureManager;
    }

    public static ArmorInspector getArmorInspector() {
        return armorInspector;
    }

    public static ZoneManager getZoneManager() {
        return zoneManager;
    }
}
