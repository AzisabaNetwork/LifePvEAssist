package net.azisaba.lifepveassist.temperature.storage;

import net.azisaba.lifepveassist.temperature.zone.TemperatureZone;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤー温度および温度変化ゾーンの DB アクセスを担うリポジトリ。
 * 排他方式は所有者トークン ({@code lock_owner})＋ハートビート ({@code locked_at}) を用いた
 * CAS ロック＋絶対値保存に統一しています。
 */
public class TemperatureRepository {

    public static final String PLAYERS_TABLE = "lifepveassist_temperature_players";
    public static final String ZONES_TABLE = "lifepveassist_temperature_zones";

    public record LoadedPlayerRow(
            double temperature,
            long previousUpdatedAtMillis,
            boolean existed,
            boolean lockAcquired
    ) {
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
                + "`lock_owner` VARCHAR(64) NULL,"
                + "`locked_at` BIGINT NOT NULL DEFAULT 0,"
                + "`updated_at` BIGINT NOT NULL,"
                + "PRIMARY KEY (`uuid`)"
                + ")";

        String alterAddOwner = "ALTER TABLE `" + PLAYERS_TABLE + "` ADD COLUMN IF NOT EXISTS `lock_owner` VARCHAR(64) NULL";
        String alterAddLockedAt = "ALTER TABLE `" + PLAYERS_TABLE + "` ADD COLUMN IF NOT EXISTS `locked_at` BIGINT NOT NULL DEFAULT 0";

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
            stmt.executeUpdate(alterAddOwner);
            stmt.executeUpdate(alterAddLockedAt);
            stmt.executeUpdate(createZones);
        }
    }

    /**
     * 移動元サーバーのロック解除 ({@code locked=false}) またはクラッシュ後のタイムアウトを待ってから
     * CAS で自分の {@code owner} によるロックを取得し、最新温度を読み込みます。
     * 他サーバーが生存してロックを保持し続けている場合は奪わず {@code lockAcquired=false} を返します。
     */
    public LoadedPlayerRow loadAndAcquireLock(
            UUID uuid,
            String owner,
            double defaultTemp,
            long maxWaitMillis,
            long lockTimeoutMillis
    ) throws SQLException {
        long now = System.currentTimeMillis();
        if (tryInsertNewPlayer(uuid, defaultTemp, owner, now)) {
            return new LoadedPlayerRow(defaultTemp, now, false, true);
        }

        long deadline = now + Math.max(0L, maxWaitMillis);
        while (true) {
            now = System.currentTimeMillis();
            if (tryAcquireLockCas(uuid, owner, lockTimeoutMillis, now)) {
                RowSnapshot snapshot = selectPlayer(uuid);
                if (snapshot == null) {
                    if (tryInsertNewPlayer(uuid, defaultTemp, owner, now)) {
                        return new LoadedPlayerRow(defaultTemp, now, false, true);
                    }
                    continue;
                }
                double safeTemp = Double.isFinite(snapshot.temperature) ? snapshot.temperature : defaultTemp;
                return new LoadedPlayerRow(safeTemp, snapshot.updatedAt, true, true);
            }

            if (now >= deadline) {
                RowSnapshot snapshot = selectPlayer(uuid);
                if (snapshot == null) {
                    if (tryInsertNewPlayer(uuid, defaultTemp, owner, now)) {
                        return new LoadedPlayerRow(defaultTemp, now, false, true);
                    }
                    return new LoadedPlayerRow(defaultTemp, now, false, false);
                }
                double safeTemp = Double.isFinite(snapshot.temperature) ? snapshot.temperature : defaultTemp;
                return new LoadedPlayerRow(safeTemp, snapshot.updatedAt, true, false);
            }

            try {
                Thread.sleep(Math.min(100L, Math.max(10L, deadline - now)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                RowSnapshot snapshot = selectPlayer(uuid);
                double safeTemp = (snapshot != null && Double.isFinite(snapshot.temperature))
                        ? snapshot.temperature : defaultTemp;
                long prevUpdated = snapshot != null ? snapshot.updatedAt : System.currentTimeMillis();
                return new LoadedPlayerRow(safeTemp, prevUpdated, snapshot != null, false);
            }
        }
    }

    /**
     * Quit 時に最終温度（絶対値）を保存し、自分が所有しているロックを解放 ({@code locked=false}) します。
     * 他サーバーが既に正当なロックを所有している場合は上書きしません。
     */
    public boolean savePlayerAndReleaseLock(UUID uuid, String owner, double temperature) throws SQLException {
        if (!Double.isFinite(temperature)) {
            return false;
        }
        long now = System.currentTimeMillis();
        String sql = "UPDATE `" + PLAYERS_TABLE + "` SET "
                + "`temperature` = ?, `locked` = FALSE, `lock_owner` = NULL, `locked_at` = 0, `updated_at` = ? "
                + "WHERE `uuid` = ? AND (`lock_owner` = ? OR `locked` = FALSE)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, temperature);
            ps.setLong(2, now);
            ps.setString(3, uuid.toString());
            ps.setString(4, owner);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 自分がロック所有者のプレイヤーのみ温度を絶対値で一括保存します。
     * 定期保存では {@code keepLocked=true}（ハートビート更新を兼ねる）、サーバー停止時は {@code keepLocked=false} を指定します。
     */
    public void savePlayersBatch(String owner, Map<UUID, Double> snapshot, boolean keepLocked) throws SQLException {
        if (snapshot.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String sql = keepLocked
                ? "UPDATE `" + PLAYERS_TABLE + "` SET "
                + "`temperature` = ?, `locked` = TRUE, `locked_at` = ?, `updated_at` = ? "
                + "WHERE `uuid` = ? AND `lock_owner` = ?"
                : "UPDATE `" + PLAYERS_TABLE + "` SET "
                + "`temperature` = ?, `locked` = FALSE, `lock_owner` = NULL, `locked_at` = 0, `updated_at` = ? "
                + "WHERE `uuid` = ? AND (`lock_owner` = ? OR `locked` = FALSE)";

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
                    if (keepLocked) {
                        ps.setDouble(1, temp);
                        ps.setLong(2, now);
                        ps.setLong(3, now);
                        ps.setString(4, entry.getKey().toString());
                        ps.setString(5, owner);
                    } else {
                        ps.setDouble(1, temp);
                        ps.setLong(2, now);
                        ps.setString(3, entry.getKey().toString());
                        ps.setString(4, owner);
                    }
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

    /**
     * オンライン中の全プレイヤー（dirty でないプレイヤーを含む）のロック有効期限 ({@code locked_at}) を更新します。
     */
    public void renewLocksBatch(String owner, Collection<UUID> uuids) throws SQLException {
        if (uuids.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String sql = "UPDATE `" + PLAYERS_TABLE + "` SET `locked_at` = ? "
                + "WHERE `uuid` = ? AND `locked` = TRUE AND `lock_owner` = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (UUID uuid : uuids) {
                    ps.setLong(1, now);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, owner);
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

    private record RowSnapshot(double temperature, boolean locked, String lockOwner, long lockedAt, long updatedAt) {
    }

    private boolean tryInsertNewPlayer(UUID uuid, double temperature, String owner, long now) throws SQLException {
        String sql = "INSERT IGNORE INTO `" + PLAYERS_TABLE + "` "
                + "(`uuid`, `temperature`, `locked`, `lock_owner`, `locked_at`, `updated_at`) "
                + "VALUES (?, ?, TRUE, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, temperature);
            ps.setString(3, owner);
            ps.setLong(4, now);
            ps.setLong(5, now);
            return ps.executeUpdate() > 0;
        }
    }

    private boolean tryAcquireLockCas(UUID uuid, String owner, long lockTimeoutMillis, long now) throws SQLException {
        long expiryCutoff = now - Math.max(0L, lockTimeoutMillis);
        String sql = "UPDATE `" + PLAYERS_TABLE + "` SET "
                + "`locked` = TRUE, `lock_owner` = ?, `locked_at` = ? "
                + "WHERE `uuid` = ? AND (`locked` = FALSE OR `lock_owner` = ? OR `locked_at` <= ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setLong(2, now);
            ps.setString(3, uuid.toString());
            ps.setString(4, owner);
            ps.setLong(5, expiryCutoff);
            return ps.executeUpdate() > 0;
        }
    }

    private RowSnapshot selectPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT `temperature`, `locked`, `lock_owner`, `locked_at`, `updated_at` "
                + "FROM `" + PLAYERS_TABLE + "` WHERE `uuid` = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RowSnapshot(
                            rs.getDouble("temperature"),
                            rs.getBoolean("locked"),
                            rs.getString("lock_owner"),
                            rs.getLong("locked_at"),
                            rs.getLong("updated_at")
                    );
                }
            }
        }
        return null;
    }
}
