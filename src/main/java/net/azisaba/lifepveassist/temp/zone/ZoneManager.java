package net.azisaba.lifepveassist.temp.zone;

import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import net.azisaba.lifepveassist.temp.ui.BossBarController;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 * 空間温度変化エリアの登録・保存・定期適用、および BlockDisplay / TextDisplay による
 * 軽量な境界プレビューを管理するマネージャ。
 */
public class ZoneManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final float EDGE_THICKNESS = 0.08f;

    private record ActivePreview(List<Entity> entities, BukkitTask cleanupTask) {
        void dispose() {
            if (cleanupTask != null) {
                cleanupTask.cancel();
            }
            for (Entity entity : entities) {
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
        }
    }

    private final Plugin plugin;
    private final ConfigManager config;
    private final TemperatureManager temperatureManager;
    private final File zonesFile;

    private final Map<String, TemperatureZone> zones = new LinkedHashMap<>();
    private final Map<UUID, ActivePreview> activePreviews = new ConcurrentHashMap<>();

    private BukkitTask zoneTickTask;
    private long elapsedSeconds = 0L;

    public ZoneManager(Plugin plugin, ConfigManager config, TemperatureManager temperatureManager) {
        this.plugin = plugin;
        this.config = config;
        this.temperatureManager = temperatureManager;
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
        clearAllPreviews();
        save();
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
     * 指定エリアの12辺を BlockDisplay、中央ラベルを TextDisplay で一定時間プレイヤー本人のみに表示します。
     * すべて非永続 (persistent=false) かつ visibleByDefault=false で生成され、終了後に自動削除されます。
     */
    public void showZonePreview(Player player, TemperatureZone zone) {
        clearPreview(player.getUniqueId());

        World world = Bukkit.getWorld(zone.worldName());
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }

        Material blockMaterial;
        Color glowColor;
        String deltaColor;
        if (zone.delta() > 0.0) {
            blockMaterial = Material.RED_STAINED_GLASS;
            glowColor = Color.RED;
            deltaColor = "<red>+";
        } else if (zone.delta() < 0.0) {
            blockMaterial = Material.LIGHT_BLUE_STAINED_GLASS;
            glowColor = Color.AQUA;
            deltaColor = "<aqua>";
        } else {
            blockMaterial = Material.WHITE_STAINED_GLASS;
            glowColor = Color.WHITE;
            deltaColor = "<white>";
        }

        double minX = zone.minX();
        double minY = zone.minY();
        double minZ = zone.minZ();
        double maxX = zone.maxX() + 1.0;
        double maxY = zone.maxY() + 1.0;
        double maxZ = zone.maxZ() + 1.0;

        float lenX = (float) (maxX - minX);
        float lenY = (float) (maxY - minY);
        float lenZ = (float) (maxZ - minZ);
        float t = EDGE_THICKNESS;

        List<Entity> spawned = new ArrayList<>(13);

        // X 軸方向の4辺
        spawned.add(spawnEdge(player, world, minX, minY, minZ, lenX, t, t, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, minX, minY, maxZ - t, lenX, t, t, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, minX, maxY - t, minZ, lenX, t, t, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, minX, maxY - t, maxZ - t, lenX, t, t, blockMaterial, glowColor));

        // Y 軸方向の4辺
        spawned.add(spawnEdge(player, world, minX, minY, minZ, t, lenY, t, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, maxX - t, minY, minZ, t, lenY, t, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, minX, minY, maxZ - t, t, lenY, t, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, maxX - t, minY, maxZ - t, t, lenY, t, blockMaterial, glowColor));

        // Z 軸方向の4辺
        spawned.add(spawnEdge(player, world, minX, minY, minZ, t, t, lenZ, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, maxX - t, minY, minZ, t, t, lenZ, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, minX, maxY - t, minZ, t, t, lenZ, blockMaterial, glowColor));
        spawned.add(spawnEdge(player, world, maxX - t, maxY - t, minZ, t, t, lenZ, blockMaterial, glowColor));

        // 中央ラベル (TextDisplay)
        Location center = new Location(world, zone.centerX(), zone.centerY(), zone.centerZ());
        String label = "<gold><bold>[Zone: " + zone.name() + "]</bold>\n"
                + deltaColor + BossBarController.formatTemp(zone.delta()) + "℃ <gray>/ " + zone.intervalSeconds() + "秒\n"
                + "<dark_gray>(" + zone.minX() + "," + zone.minY() + "," + zone.minZ()
                + ") ～ (" + zone.maxX() + "," + zone.maxY() + "," + zone.maxZ() + ")";
        TextDisplay textDisplay = world.spawn(center, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.text(MINI.deserialize(label));
        });
        player.showEntity(plugin, textDisplay);
        spawned.add(textDisplay);

        long durationTicks = config.getPreviewDurationSeconds() * 20L;
        UUID playerUuid = player.getUniqueId();
        BukkitTask cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(playerUuid), durationTicks);
        activePreviews.put(playerUuid, new ActivePreview(spawned, cleanupTask));
    }

    private BlockDisplay spawnEdge(
            Player viewer,
            World world,
            double x, double y, double z,
            float sx, float sy, float sz,
            Material material,
            Color glowColor
    ) {
        Location loc = new Location(world, x, y, z);
        BlockDisplay display = world.spawn(loc, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setBlock(material.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(glowColor);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(),
                    new Vector3f(sx, sy, sz),
                    new AxisAngle4f()
            ));
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    public void clearPreview(UUID playerUuid) {
        ActivePreview prev = activePreviews.remove(playerUuid);
        if (prev != null) {
            prev.dispose();
        }
    }

    public void clearAllPreviews() {
        for (ActivePreview preview : activePreviews.values()) {
            preview.dispose();
        }
        activePreviews.clear();
    }

    private static String normalizeKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
