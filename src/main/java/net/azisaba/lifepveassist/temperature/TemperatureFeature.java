package net.azisaba.lifepveassist.temperature;

import net.azisaba.lifepveassist.database.DBConnector;
import net.azisaba.lifepveassist.database.DatabaseConfig;
import net.azisaba.lifepveassist.temperature.armor.ArmorInspector;
import net.azisaba.lifepveassist.temperature.command.TemperatureSubCommand;
import net.azisaba.lifepveassist.temperature.config.TemperatureConfig;
import net.azisaba.lifepveassist.temperature.core.TemperatureService;
import net.azisaba.lifepveassist.temperature.listener.TemperatureLifecycleListener;
import net.azisaba.lifepveassist.temperature.mythic.TemperatureMythicListener;
import net.azisaba.lifepveassist.temperature.storage.TemperatureRepository;
import net.azisaba.lifepveassist.temperature.ui.BossBarController;
import net.azisaba.lifepveassist.temperature.ui.WarningController;
import net.azisaba.lifepveassist.temperature.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * LifePvEAssist 内の温度管理機能のライフサイクル管理クラス。
 * {@code temperature.enabled-worlds} が空のサーバーでは DB 接続を含め一切起動せず、
 * 起動時 DB 障害時は温度機能のみ安全に停止します。
 */
public final class TemperatureFeature {

    private final JavaPlugin plugin;
    private final DBConnector dbConnector = new DBConnector();

    private ExecutorService dbExecutor;
    private TemperatureService service;
    private ZoneManager zoneManager;
    private TemperatureSubCommand subCommand;
    private boolean active;

    public TemperatureFeature(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        TemperatureConfig initialConfig = TemperatureConfig.fromSection(
                plugin.getConfig().getConfigurationSection("temperature"),
                plugin.getLogger()
        );

        if (!initialConfig.isFeatureEnabled()) {
            plugin.getLogger().info("[Temperature] enabled-worlds が空のため、温度機能および DB 接続を起動しません。");
            this.active = false;
            return;
        }

        DatabaseConfig dbConfig = DatabaseConfig.fromSection(
                plugin.getConfig().getConfigurationSection("database")
        );

        try {
            dbConnector.init(dbConfig);
            TemperatureRepository repository = new TemperatureRepository(dbConnector.getDataSource());
            repository.initTables();

            AtomicInteger threadSeq = new AtomicInteger(1);
            this.dbExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "LifePvEAssist-TempDB-" + threadSeq.getAndIncrement());
                t.setDaemon(true);
                return t;
            });

            String rawOwner = initialConfig.serverName() + "-" + UUID.randomUUID().toString().substring(0, 8);
            String lockOwner = rawOwner.length() > 64 ? rawOwner.substring(0, 64) : rawOwner;

            ArmorInspector armorInspector = new ArmorInspector(plugin);
            BossBarController bossBarController = new BossBarController();
            WarningController warningController = new WarningController();
            this.zoneManager = new ZoneManager(plugin, repository, dbExecutor);
            this.zoneManager.loadSync(initialConfig.serverName());

            this.service = new TemperatureService(
                    plugin, initialConfig, lockOwner, armorInspector, repository,
                    zoneManager, bossBarController, warningController, dbExecutor
            );
            this.subCommand = new TemperatureSubCommand(
                    plugin, service, armorInspector, zoneManager, this::reload
            );

            Bukkit.getPluginManager().registerEvents(new TemperatureLifecycleListener(service), plugin);
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                Bukkit.getPluginManager().registerEvents(new TemperatureMythicListener(plugin, service), plugin);
                // PlugMan 等によるホットリロード時は MythicMobs が旧インスタンスを参照し続けるため自動再読み込みする
                if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && !Bukkit.getOnlinePlayers().isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("[Temperature] ホットリロードを検知したため MythicMobs のスキル連携を再バインドします。");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm reload");
                    });
                }
            }

            service.start();
            this.active = true;
            plugin.getLogger().info("[Temperature] 温度管理機能を有効化しました (server="
                    + initialConfig.serverName() + ", worlds=" + initialConfig.enabledWorlds() + ")。");
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[Temperature] DB の初期化に失敗したため、温度機能のみ安全に停止します（LifePvEAssist の他機能には影響しません）。", e);
            disable();
        }
    }

    public void reload() {
        plugin.reloadConfig();
        if (!active) {
            TemperatureConfig next = TemperatureConfig.fromSection(
                    plugin.getConfig().getConfigurationSection("temperature"),
                    plugin.getLogger()
            );
            if (next.isFeatureEnabled()) {
                enable();
            }
            return;
        }
        TemperatureConfig next = TemperatureConfig.fromSection(
                plugin.getConfig().getConfigurationSection("temperature"),
                plugin.getLogger()
        );
        service.reloadConfig(next);
        zoneManager.reloadAsync(next.serverName());
    }

    public void disable() {
        this.active = false;
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                dbExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dbExecutor = null;
        }
        dbConnector.close();
        zoneManager = null;
        subCommand = null;
    }

    public boolean isActive() {
        return active;
    }

    public TemperatureSubCommand getSubCommand() {
        return subCommand;
    }
}
