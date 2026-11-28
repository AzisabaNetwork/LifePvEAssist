package net.azisaba.lifepveassist.temperature.zone;

import org.bukkit.Location;

/**
 * 空間での継続的な温度変化を行う直方体エリア定義。
 * マルチサーバー対応のため serverName + name で一意に識別されます。
 */
public record TemperatureZone(
        String serverName,
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

    /**
     * 同一サーバー・同一ワールド内で他のゾーンと1ブロック以上重なっているかを判定します。
     */
    public boolean intersects(TemperatureZone other) {
        if (other == null) {
            return false;
        }
        if (!this.serverName.equalsIgnoreCase(other.serverName)
                || !this.worldName.equalsIgnoreCase(other.worldName)) {
            return false;
        }
        return this.minX <= other.maxX && this.maxX >= other.minX
                && this.minY <= other.maxY && this.maxY >= other.minY
                && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
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
