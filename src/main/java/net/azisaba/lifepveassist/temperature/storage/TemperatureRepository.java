package net.azisaba.lifepveassist.temperature.storage;

import net.azisaba.lifepveassist.temperature.zone.TemperatureZone;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤー温度および温度変化ゾーンの DB アクセスを担うリポジトリ。
 * 排他方式は {@code locked} フラグ＋絶対値保存に統一しています。
 */
public class TemperatureRepository {

    public static final String PLAYERS_TABLE = "lifepveassist_temperature_players";
    public static final String ZONES_TABLE = "lifepveassist_temperature_zones";

    public record LoadedPlayerRow(double temperature, long previousUpdatedAtMillis, boolean existed) {
    }

    private final DataSource dataSource;

    public TemperatureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initTables() throws SQLException {
        String createPlayers = "CREATE TABLE IF NOT EXISTS `" + PLAYERS_TABLE + "` ("
                + "`uuid` VARCHAR(36) NOT NULL,"
                + "`temperature` DOUBLE NOT NULL,"
                + "`locked` BOOLEAN NOT NULL DEFAULT FALSE,"
                + "`updated_at` BIGINT NOT NULL,"
                + "PRIMARY KEY (`uuid`)"
                + ")";

        String createZones = "CREATE TABLE IF NOT EXISTS `" + ZONES_TABLE + "` ("
                + "`server_name` VARCHAR(64) NOT NULL,"
                + "`name` VARCHAR(64) NOT NULL,"
                + "`world_name` VARCHAR(64) NOT NULL,"
                + "`min_x` INT NOT NULL,"
                + "`min_y` INT NOT NULL,"
                + "`min_z` INT NOT NULL,"
                + "`max_x` INT NOT NULL,"
                + "`max_y` INT NOT NULL,"
                + "`max_z` INT NOT NULL,"
                + "`delta` DOUBLE NOT NULL,"
                + "`interval_seconds` INT NOT NULL,"
                + "PRIMARY KEY (`server_name`, `name`)"
                + ")";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createPlayers);
            stmt.executeUpdate(createZones);
        }
    }

    /**
     * 移動元サーバーのロック解除 (locked=false) またはタイムアウトを待ってから最新温度を読み込み、
     * 本サーバー用に locked=true を取得します。
     */
    public LoadedPlayerRow loadAndAcquireLock(
            UUID uuid,
            double defaultTemp,
            long maxWaitMillis,
            long lockTimeoutMillis
    ) throws SQLException {
        long deadline = System.currentTimeMillis() + Math.max(0L, maxWaitMillis);

        while (true) {
            long now = System.currentTimeMillis();
            RowSnapshot snapshot = selectPlayer(uuid);

            if (snapshot == null) {
                upsertPlayer(uuid, defaultTemp, true, now);
                return new LoadedPlayerRow(defaultTemp, now, false);
            }

            boolean lockExpired = (now - snapshot.updatedAt) >= lockTimeoutMillis;
            if (!snapshot.locked || lockExpired || now >= deadline) {
                double safeTemp = Double.isFinite(snapshot.temperature) ? snapshot.temperature : defaultTemp;
                upsertPlayer(uuid, safeTemp, true, now);
                return new LoadedPlayerRow(safeTemp, snapshot.updatedAt, true);
            }

            try {
                Thread.sleep(Math.min(100L, Math.max(10L, deadline - now)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                double safeTemp = Double.isFinite(snapshot.temperature) ? snapshot.temperature : defaultTemp;
                upsertPlayer(uuid, safeTemp, true, System.currentTimeMillis());
                return new LoadedPlayerRow(safeTemp, snapshot.updatedAt, true);
            }
        }
    }

    /**
     * Quit 時に最終温度（絶対値）を保存し、ロックを解放 (locked=false) します。
     */
    public void savePlayerAndReleaseLock(UUID uuid, double temperature) throws SQLException {
        if (!Double.isFinite(temperature)) {
            return;
        }
        upsertPlayer(uuid, temperature, false, System.currentTimeMillis());
    }

    /**
     * 複数プレイヤーの温度を絶対値で一括保存します。
     * 定期保存では {@code keepLocked=true}、サーバー停止時は {@code keepLocked=false} を指定します。
     */
    public void savePlayersBatch(Map<UUID, Double> snapshot, boolean keepLocked) throws SQLException {
        if (snapshot.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO `" + PLAYERS_TABLE + "` (`uuid`, `temperature`, `locked`, `updated_at`) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "`temperature` = VALUES(`temperature`), "
                + "`locked` = VALUES(`locked`), "
                + "`updated_at` = VALUES(`updated_at`)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (Map.Entry<UUID, Double> entry : snapshot.entrySet()) {
                    Double temp = entry.getValue();
                    if (temp == null || !Double.isFinite(temp)) {
                        continue;
                    }
                    ps.setString(1, entry.getKey().toString());
                    ps.setDouble(2, temp);
                    ps.setBoolean(3, keepLocked);
                    ps.setLong(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public List<TemperatureZone> loadZones(String serverName) throws SQLException {
        String sql = "SELECT `server_name`, `name`, `world_name`, `min_x`, `min_y`, `min_z`, "
                + "`max_x`, `max_y`, `max_z`, `delta`, `interval_seconds` "
                + "FROM `" + ZONES_TABLE + "` WHERE `server_name` = ?";
        List<TemperatureZone> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int minX = rs.getInt("min_x");
                    int minY = rs.getInt("min_y");
                    int minZ = rs.getInt("min_z");
                    int maxX = rs.getInt("max_x");
                    int maxY = rs.getInt("max_y");
                    int maxZ = rs.getInt("max_z");
                    result.add(new TemperatureZone(
                            rs.getString("server_name"),
                            rs.getString("name"),
                            rs.getString("world_name"),
                            Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                            Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ),
                            rs.getDouble("delta"),
                            Math.max(1, rs.getInt("interval_seconds"))
                    ));
                }
            }
        }
        return result;
    }

    public void upsertZone(TemperatureZone zone) throws SQLException {
        String sql = "INSERT INTO `" + ZONES_TABLE + "` "
                + "(`server_name`, `name`, `world_name`, `min_x`, `min_y`, `min_z`, "
                + "`max_x`, `max_y`, `max_z`, `delta`, `interval_seconds`) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "`world_name` = VALUES(`world_name`), "
                + "`min_x` = VALUES(`min_x`), `min_y` = VALUES(`min_y`), `min_z` = VALUES(`min_z`), "
                + "`max_x` = VALUES(`max_x`), `max_y` = VALUES(`max_y`), `max_z` = VALUES(`max_z`), "
                + "`delta` = VALUES(`delta`), `interval_seconds` = VALUES(`interval_seconds`)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, zone.serverName());
            ps.setString(2, zone.name());
            ps.setString(3, zone.worldName());
            ps.setInt(4, zone.minX());
            ps.setInt(5, zone.minY());
            ps.setInt(6, zone.minZ());
            ps.setInt(7, zone.maxX());
            ps.setInt(8, zone.maxY());
            ps.setInt(9, zone.maxZ());
            ps.setDouble(10, zone.delta());
            ps.setInt(11, Math.max(1, zone.intervalSeconds()));
            ps.executeUpdate();
        }
    }

    public boolean deleteZone(String serverName, String zoneName) throws SQLException {
        String sql = "DELETE FROM `" + ZONES_TABLE + "` WHERE `server_name` = ? AND LOWER(`name`) = LOWER(?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverName);
            ps.setString(2, zoneName);
            return ps.executeUpdate() > 0;
        }
    }

    private record RowSnapshot(double temperature, boolean locked, long updatedAt) {
    }

    private RowSnapshot selectPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT `temperature`, `locked`, `updated_at` FROM `" + PLAYERS_TABLE + "` WHERE `uuid` = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RowSnapshot(
                            rs.getDouble("temperature"),
                            rs.getBoolean("locked"),
                            rs.getLong("updated_at")
                    );
                }
            }
        }
        return null;
    }

    private void upsertPlayer(UUID uuid, double temperature, boolean locked, long updatedAt) throws SQLException {
        String sql = "INSERT INTO `" + PLAYERS_TABLE + "` (`uuid`, `temperature`, `locked`, `updated_at`) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "`temperature` = VALUES(`temperature`), "
                + "`locked` = VALUES(`locked`), "
                + "`updated_at` = VALUES(`updated_at`)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, temperature);
            ps.setBoolean(3, locked);
            ps.setLong(4, updatedAt);
            ps.executeUpdate();
        }
    }
}
