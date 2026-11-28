package net.azisaba.lifepveassist;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import net.azisaba.lifepveassist.command.LifePvEAssistCommand;
import net.azisaba.lifepveassist.ffn.FFN;
import net.azisaba.lifepveassist.temperature.TemperatureFeature;
import net.azisaba.lifepveassist.losingcats.LosingCats;
import net.azisaba.lifepveassist.killerschocolate.KillerChocolateListener;
import net.azisaba.lifepveassist.listener.CraftListener;
import net.azisaba.lifepveassist.listener.LifeCraftListener;
import net.azisaba.lifepveassist.listener.PvPCancelListener;
import net.azisaba.lifepveassist.minecraft.server.BlockingCommand;
import net.azisaba.lifepveassist.minecraft.server.PacketListenerImpl;
import net.azisaba.lifepveassist.minecraft.server.PlayerJoinListener;
import net.azisaba.lifepvelevel.SpigotPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;

public final class LifePvEAssist extends JavaPlugin {

    private static LifePvEAssist instance;
    public static SpigotPlugin pveLevel;
    private TemperatureFeature temperatureFeature;
    private PacketListenerCommon packetListener;

    public LifePvEAssist() {
        instance = this;
    }

    public static LifePvEAssist getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.migrateFFNConfig();
        this.migrateTemperatureConfig();

        this.temperatureFeature = new TemperatureFeature(this);

        LifePvEAssistCommand mainCommand = new LifePvEAssistCommand(this.temperatureFeature);
        var mainPluginCmd = this.getCommand("lifepveassist");
        if (mainPluginCmd != null) {
            mainPluginCmd.setExecutor(mainCommand);
            mainPluginCmd.setTabCompleter(mainCommand);
        }
        this.getCommand("blocking").setExecutor(new BlockingCommand());

        PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(new LifeCraftListener(), this);
        pluginManager.registerEvents(new CraftListener(), this);
        pluginManager.registerEvents(new PvPCancelListener(this), this);
        pluginManager.registerEvents(new PlayerJoinListener(this), this);

        FFN.register(this);
        this.temperatureFeature.enable();

        pluginManager.registerEvents(new KillerChocolateListener(), this);
        pluginManager.registerEvents(new LosingCats(this), this);

        this.packetListener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerImpl());

        this.getLogger().info("LifePvEAssist has been enabled.");
    }

    @Override
    public void onDisable() {
        if (this.packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(this.packetListener);
            this.packetListener = null;
        }
        if (this.temperatureFeature != null) {
            this.temperatureFeature.disable();
        }
        FFN.clear();
        this.getLogger().info("LifePvEAssist has been disabled.");
    }

    /**
     * 旧 FFN プラグインの設定を {@code ffn:} セクション配下へ移行する。
     * 統合前のサーバーには {@code ffn:} が存在せず、トップレベルに
     * {@code GatherWorlds} / {@code GatherDelaySeconds} / {@code GatherList} があるため、
     * それらを引き継いで旧キーを削除する。該当キーがなければ既定値を補う。
     */
    private void migrateFFNConfig() {
        FileConfiguration config = this.getConfig();
        if (config.isConfigurationSection("ffn")) {
            return;
        }

        ConfigurationSection ffn = config.createSection("ffn");

        if (config.isSet("GatherWorlds")) {
            ffn.set("GatherWorlds", config.getStringList("GatherWorlds"));
        } else {
            ffn.set("GatherWorlds", Collections.singletonList("FFTrial"));
        }
        config.set("GatherWorlds", null);

        ffn.set("GatherDelaySeconds", config.getInt("GatherDelaySeconds", 30));
        config.set("GatherDelaySeconds", null);

        ConfigurationSection gatherList = config.getConfigurationSection("GatherList");
        if (gatherList != null) {
            for (String materialKey : gatherList.getKeys(false)) {
                ffn.set("GatherList." + materialKey, gatherList.get(materialKey));
            }
        } else {
            ffn.createSection("GatherList");
        }
        config.set("GatherList", null);

        this.saveConfig();
    }

    /**
     * 既存サーバーの {@code config.yml} に {@code database:} / {@code temperature:} セクションや
     * 新規キーが不足している場合、JAR 内デフォルト設定から不足分のみ追記して保存する。
     * 既存の他設定（{@code pvp-enabled-worlds}, {@code ffn} 等）は上書きしない。
     */
    private void migrateTemperatureConfig() {
        FileConfiguration config = this.getConfig();
        org.bukkit.configuration.Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        boolean modified = false;
        for (String rootKey : new String[]{"database", "temperature"}) {
            ConfigurationSection defaultSec = defaults.getConfigurationSection(rootKey);
            if (defaultSec == null) {
                continue;
            }
            for (String relKey : defaultSec.getKeys(true)) {
                if (defaultSec.isConfigurationSection(relKey)) {
                    continue;
                }
                String fullPath = rootKey + "." + relKey;
                if (!config.isSet(fullPath)) {
                    config.set(fullPath, defaultSec.get(relKey));
                    modified = true;
                }
            }
        }
        if (modified) {
            this.saveConfig();
            this.getLogger().info("[Temperature] config.yml に不足していた database / temperature 設定を自動追記しました。");
        }
    }

    public void setup() {
        if (getServer().getPluginManager().getPlugin("LifePvELevel") != null) {
            pveLevel = (SpigotPlugin) getServer().getPluginManager().getPlugin("LifePvELevel");
        }
    }
}
