package net.azisaba.lifepveassist.temp.zone;

import org.bukkit.Location;

/**
 * プレイヤーごとの WorldEdit 風範囲選択 (pos1 / pos2) 状態。
 */
public class ZoneSelection {

    private Location pos1;
    private Location pos2;

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1 == null ? null : pos1.toBlockLocation();
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2 == null ? null : pos2.toBlockLocation();
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null
                && pos1.getWorld() != null && pos2.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld());
    }

    public long volume() {
        if (!isComplete()) {
            return 0L;
        }
        int dx = Math.abs(pos1.getBlockX() - pos2.getBlockX()) + 1;
        int dy = Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1;
        int dz = Math.abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1;
        return (long) dx * dy * dz;
    }

    public TemperatureZone toZone(String name, double delta, int intervalSeconds) {
        if (!isComplete()) {
            throw new IllegalStateException("Selection is incomplete or spans multiple worlds");
        }
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        return new TemperatureZone(
                name,
                pos1.getWorld().getName(),
                minX, minY, minZ,
                maxX, maxY, maxZ,
                delta,
                Math.max(1, intervalSeconds)
        );
    }
}
