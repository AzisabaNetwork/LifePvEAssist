package net.azisaba.lifepveassist.temp.zone;

import org.bukkit.Location;

/**
 * 空間での継続的な温度変化を行う直方体エリア定義。
 */
public record TemperatureZone(
        String name,
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        double delta,
        int intervalSeconds
) {
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= minX && bx <= maxX
                && by >= minY && by <= maxY
                && bz >= minZ && bz <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerY() {
        return (minY + maxY + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }
}
