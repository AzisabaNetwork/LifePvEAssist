package net.azisaba.lifepveassist.temperature.zone;

import net.azisaba.lifepveassist.temperature.storage.TemperatureRepository;
import net.azisaba.lifepveassist.temperature.ui.BossBarController;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * DB 保存の空間温度変化エリアを不変スナップショットで管理し、
 * 重複チェック・最大絶対値1件選択・BlockDisplay / TextDisplay 軽量プレビューを提供するマネージャ。
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
    private final TemperatureRepository repository;
    private final ExecutorService dbExecutor;

    private volatile Map<String, TemperatureZone> zonesSnapshot = Map.of();
    private final Map<UUID, ActivePreview> activePreviews = new ConcurrentHashMap<>();

    public ZoneManager(Plugin plugin, TemperatureRepository repository, ExecutorService dbExecutor) {
        this.plugin = plugin;
        this.repository = repository;
        this.dbExecutor = dbExecutor;
    }

    public void loadSync(String serverName) throws SQLException {
        List<TemperatureZone> loaded = repository.loadZones(serverName);
        replaceSnapshot(loaded);
    }

    public CompletableFuture<Void> reloadAsync(String serverName) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<TemperatureZone> loaded = repository.loadZones(serverName);
                replaceSnapshot(loaded);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Temperature] DB からの Zone 再読み込みに失敗しました。", e);
            }
        }, dbExecutor);
    }

    private void replaceSnapshot(List<TemperatureZone> zones) {
        Map<String, TemperatureZone> next = new LinkedHashMap<>();
        for (TemperatureZone z : zones) {
            next.put(normalizeKey(z.name()), z);
        }
        this.zonesSnapshot = Collections.unmodifiableMap(next);
    }

    /**
     * 同一サーバー・同一ワールド内で candidate と座標が重なっている既存ゾーンを探します（同名上書きは除外）。
     */
    public TemperatureZone findOverlappingZone(TemperatureZone candidate) {
        String candidateKey = normalizeKey(candidate.name());
        for (Map.Entry<String, TemperatureZone> entry : zonesSnapshot.entrySet()) {
            if (entry.getKey().equals(candidateKey)) {
                continue;
            }
            if (candidate.intersects(entry.getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 指定座標を含むゾーンのうち、変化量の絶対値が最大の1件を返します（重複時の安全フォールバック）。
     */
    public TemperatureZone findStrongestZoneAt(Location location) {
        TemperatureZone best = null;
        double bestAbs = -1.0;
        for (TemperatureZone zone : zonesSnapshot.values()) {
            if (zone.delta() == 0.0 || !zone.contains(location)) {
                continue;
            }
            double abs = Math.abs(zone.delta());
            if (best == null || abs > bestAbs) {
                best = zone;
                bestAbs = abs;
            }
        }
        return best;
    }

    public CompletableFuture<Boolean> addZoneAsync(TemperatureZone zone) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.upsertZone(zone);
                Map<String, TemperatureZone> next = new LinkedHashMap<>(zonesSnapshot);
                next.put(normalizeKey(zone.name()), zone);
                this.zonesSnapshot = Collections.unmodifiableMap(next);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Temperature] Zone の DB 保存に失敗しました: " + zone.name(), e);
                return false;
            }
        }, dbExecutor);
    }

    public CompletableFuture<Boolean> removeZoneAsync(String serverName, String name) {
        String key = normalizeKey(name);
        if (!zonesSnapshot.containsKey(key)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean deleted = repository.deleteZone(serverName, name);
                Map<String, TemperatureZone> next = new LinkedHashMap<>(zonesSnapshot);
                next.remove(key);
                this.zonesSnapshot = Collections.unmodifiableMap(next);
                return deleted;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Temperature] Zone の DB 削除に失敗しました: " + name, e);
                return false;
            }
        }, dbExecutor);
    }

    public TemperatureZone getZone(String name) {
        return zonesSnapshot.get(normalizeKey(name));
    }

    public Collection<TemperatureZone> getZones() {
        return zonesSnapshot.values();
    }

    /**
     * 指定エリアの12辺を BlockDisplay、中央ラベルを TextDisplay で管理者本人のみに表示します。
     */
    public void showZonePreview(Player player, TemperatureZone zone, int previewDurationSeconds) {
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
        } else {
            blockMaterial = Material.LIGHT_BLUE_STAINED_GLASS;
            glowColor = Color.AQUA;
            deltaColor = "<aqua>";
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

        long durationTicks = Math.max(1, previewDurationSeconds) * 20L;
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
