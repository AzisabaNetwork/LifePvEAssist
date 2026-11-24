package net.azisaba.lifepveassist.temp.zone;

import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 空間温度変化エリアの登録・保存・定期適用・ワンド選択・境界可視化を管理するマネージャ。
 */
public class ZoneManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager config;
    private final TemperatureManager temperatureManager;
    private final NamespacedKey wandKey;
    private final File zonesFile;

    private final Map<String, TemperatureZone> zones = new LinkedHashMap<>();
    private final Map<UUID, ZoneSelection> selections = new ConcurrentHashMap<>();

    private BukkitTask zoneTickTask;
    private long elapsedSeconds = 0L;

    public ZoneManager(Plugin plugin, ConfigManager config, TemperatureManager temperatureManager) {
        this.plugin = plugin;
        this.config = config;
        this.temperatureManager = temperatureManager;
        this.wandKey = new NamespacedKey(plugin, "zone_wand");
        this.zonesFile = new File(new File(plugin.getDataFolder(), "temp"), "zones.yml");
    }

    public void start() {
        load();
        this.zoneTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickZones, 20L, 20L);
    }

    public void shutdown() {
        if (zoneTickTask != null) {
            zoneTickTask.cancel();
            zoneTickTask = null;
        }
        save();
        selections.clear();
    }

    public void load() {
        zones.clear();
        if (!zonesFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(zonesFile);
        ConfigurationSection section = yaml.getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub == null) {
                continue;
            }
            String name = sub.getString("name", key);
            String world = sub.getString("world", "");
            if (world.isBlank()) {
                continue;
            }
            int minX = sub.getInt("min.x");
            int minY = sub.getInt("min.y");
            int minZ = sub.getInt("min.z");
            int maxX = sub.getInt("max.x");
            int maxY = sub.getInt("max.y");
            int maxZ = sub.getInt("max.z");
            double delta = sub.getDouble("delta", 0.0);
            int interval = Math.max(1, sub.getInt("interval-seconds", 10));

            TemperatureZone zone = new TemperatureZone(
                    name, world,
                    Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                    Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ),
                    delta, interval
            );
            zones.put(normalizeKey(name), zone);
        }
    }

    public void save() {
        File parent = zonesFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, TemperatureZone> entry : zones.entrySet()) {
            TemperatureZone z = entry.getValue();
            String base = "zones." + entry.getKey();
            yaml.set(base + ".name", z.name());
            yaml.set(base + ".world", z.worldName());
            yaml.set(base + ".min.x", z.minX());
            yaml.set(base + ".min.y", z.minY());
            yaml.set(base + ".min.z", z.minZ());
            yaml.set(base + ".max.x", z.maxX());
            yaml.set(base + ".max.y", z.maxY());
            yaml.set(base + ".max.z", z.maxZ());
            yaml.set(base + ".delta", z.delta());
            yaml.set(base + ".interval-seconds", z.intervalSeconds());
        }
        try {
            yaml.save(zonesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "zones.yml の保存に失敗しました", e);
        }
    }

    public void addZone(TemperatureZone zone) {
        zones.put(normalizeKey(zone.name()), zone);
        save();
    }

    public boolean removeZone(String name) {
        TemperatureZone removed = zones.remove(normalizeKey(name));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public TemperatureZone getZone(String name) {
        return zones.get(normalizeKey(name));
    }

    public Collection<TemperatureZone> getZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public ZoneSelection getSelection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), uuid -> new ZoneSelection());
    }

    public void clearSelection(UUID uuid) {
        selections.remove(uuid);
    }

    public ItemStack createWandItem() {
        ItemStack item = new ItemStack(config.getWandMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI.deserialize(config.getWandName()));
            meta.lore(List.of(
                    MINI.deserialize("<gray>左クリック: <yellow>pos1 <gray>を設定"),
                    MINI.deserialize("<gray>右クリック: <yellow>pos2 <gray>を設定"),
                    MINI.deserialize("<dark_gray>/ltm zone create <名前> <変化量> <間隔秒>")
            ));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private void tickZones() {
        elapsedSeconds++;
        if (zones.isEmpty()) {
            return;
        }

        for (TemperatureZone zone : zones.values()) {
            if (zone.delta() == 0.0 || (elapsedSeconds % zone.intervalSeconds()) != 0L) {
                continue;
            }
            if (!config.isEnabledWorld(zone.worldName())) {
                continue;
            }
            World world = Bukkit.getWorld(zone.worldName());
            if (world == null) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                if (player.isDead() || !zone.contains(player.getLocation())) {
                    continue;
                }
                if (config.isBypass(player)) {
                    continue;
                }
                if (config.isIgnoreCreativeSpectator()
                        && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
                    continue;
                }
                temperatureManager.addTemperature(player, zone.delta(), config.isResetTimerOnZoneChange());
            }
        }
    }

    /**
     * 指定したエリアの12辺をパーティクルで一定時間プレイヤーに表示します。
     */
    public void showZonePreview(Player player, TemperatureZone zone) {
        int durationSeconds = config.getPreviewDurationSeconds();
        int totalRuns = durationSeconds * 2; // 0.5秒ごと
        Particle particle = zone.delta() > 0 ? Particle.FLAME
                : (zone.delta() < 0 ? Particle.SNOWFLAKE : Particle.END_ROD);

        final int[] count = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().getName().equalsIgnoreCase(zone.worldName())
                    || count[0] >= totalRuns) {
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                return;
            }
            count[0]++;
            spawnBoxEdges(player, zone, particle);
        }, 0L, 10L);
    }

    private void spawnBoxEdges(Player player, TemperatureZone zone, Particle particle) {
        double minX = zone.minX();
        double minY = zone.minY();
        double minZ = zone.minZ();
        double maxX = zone.maxX() + 1.0;
        double maxY = zone.maxY() + 1.0;
        double maxZ = zone.maxZ() + 1.0;

        double stepX = Math.max(0.5, (maxX - minX) / 25.0);
        double stepY = Math.max(0.5, (maxY - minY) / 25.0);
        double stepZ = Math.max(0.5, (maxZ - minZ) / 25.0);

        for (double x = minX; x <= maxX; x += stepX) {
            spawnDot(player, particle, x, minY, minZ);
            spawnDot(player, particle, x, minY, maxZ);
            spawnDot(player, particle, x, maxY, minZ);
            spawnDot(player, particle, x, maxY, maxZ);
        }
        for (double y = minY; y <= maxY; y += stepY) {
            spawnDot(player, particle, minX, y, minZ);
            spawnDot(player, particle, minX, y, maxZ);
            spawnDot(player, particle, maxX, y, minZ);
            spawnDot(player, particle, maxX, y, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += stepZ) {
            spawnDot(player, particle, minX, minY, z);
            spawnDot(player, particle, minX, maxY, z);
            spawnDot(player, particle, maxX, minY, z);
            spawnDot(player, particle, maxX, maxY, z);
        }
    }

    private void spawnDot(Player player, Particle particle, double x, double y, double z) {
        Location pLoc = player.getLocation();
        double dx = x - pLoc.getX();
        double dy = y - pLoc.getY();
        double dz = z - pLoc.getZ();
        if (dx * dx + dy * dy + dz * dz <= 64.0 * 64.0) {
            player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    public static Component formatLocation(Location loc) {
        return MINI.deserialize("<yellow>(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + ") <gray>[" + loc.getWorld().getName() + "]");
    }

    private static String normalizeKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
