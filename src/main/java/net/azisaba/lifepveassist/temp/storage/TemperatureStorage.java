package net.azisaba.lifepveassist.temp.storage;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * プレイヤーの温度データを PDC (プレイヤー本体) と playerdata.yml の二重で永続化するストレージ。
 */
public class TemperatureStorage {

    private final Plugin plugin;
    private final NamespacedKey playerTempKey;
    private final File dataFile;
    private YamlConfiguration yaml;

    public TemperatureStorage(Plugin plugin) {
        this.plugin = plugin;
        this.playerTempKey = new NamespacedKey(plugin, "player_temperature");
        this.dataFile = new File(new File(plugin.getDataFolder(), "temp"), "playerdata.yml");
        loadFile();
    }

    private void loadFile() {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        if (!dataFile.exists()) {
            this.yaml = new YamlConfiguration();
        } else {
            this.yaml = YamlConfiguration.loadConfiguration(dataFile);
        }
    }

    /**
     * プレイヤーの保存済み温度を読み込みます。未保存の場合は defaultTemp を返します。
     */
    public double load(Player player, double defaultTemp) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Double pdcVal = pdc.get(playerTempKey, PersistentDataType.DOUBLE);
        if (pdcVal != null && Double.isFinite(pdcVal)) {
            return pdcVal;
        }

        String path = "players." + player.getUniqueId();
        if (yaml.contains(path)) {
            double fileVal = yaml.getDouble(path, defaultTemp);
            if (Double.isFinite(fileVal)) {
                pdc.set(playerTempKey, PersistentDataType.DOUBLE, fileVal);
                return fileVal;
            }
        }
        return defaultTemp;
    }

    /**
     * プレイヤーの現在温度を PDC とメモリ内 YAML に反映します。
     */
    public void savePlayer(Player player, double temperature) {
        if (!Double.isFinite(temperature)) {
            return;
        }
        player.getPersistentDataContainer().set(playerTempKey, PersistentDataType.DOUBLE, temperature);
        yaml.set("players." + player.getUniqueId(), temperature);
    }

    /**
     * 全キャッシュを一括で YAML に反映しディスクへ書き出します。
     */
    public void flushAll(Map<UUID, Double> snapshot) {
        for (Map.Entry<UUID, Double> entry : snapshot.entrySet()) {
            if (Double.isFinite(entry.getValue())) {
                yaml.set("players." + entry.getKey(), entry.getValue());
            }
        }
        saveFile();
    }

    public void saveFile() {
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "playerdata.yml の保存に失敗しました", e);
        }
    }
}
