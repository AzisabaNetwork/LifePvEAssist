package net.azisaba.lifepveassist.temp.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.azisaba.lifepveassist.temp.config.ConfigManager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * プレイヤーの温度データを SQLite または MySQL/MariaDB に非同期で永続化するデータベースストレージ。
 */
public class TemperatureStorage {

    private final Plugin plugin;
    private final ConfigManager config;
    private final ExecutorService dbExecutor;

    private HikariDataSource dataSource;
    private boolean isMySql;
    private String tableName;

    public TemperatureStorage(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LifePvEAssist-TempDB");
            t.setDaemon(true);
            return t;
        });
        initialize();
    }

    private void initialize() {
        String type = config.getDatabaseType().toLowerCase(Locale.ROOT);
        this.isMySql = "mysql".equals(type) || "mariadb".equals(type);

        String prefix = config.getDatabaseTablePrefix().replaceAll("[^a-zA-Z0-9_]", "");
        this.tableName = prefix + "player_temperature";

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("LifePvEAssist-TempPool");

        if (isMySql) {
            String host = config.getDatabaseHost();
            int port = config.getDatabasePort();
            String db = config.getDatabaseName();
            boolean useSsl = config.isDatabaseUseSsl();

            hikari.setDriverClassName("org.mariadb.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + db
                    + "?useUnicode=true&characterEncoding=utf8&sslMode=" + (useSsl ? "VERIFY_CA" : "DISABLE"));
            hikari.setUsername(config.getDatabaseUsername());
            hikari.setPassword(config.getDatabasePassword());
            hikari.setMaximumPoolSize(Math.max(1, config.getDatabasePoolSize()));
        } else {
            File dbFile = new File(plugin.getDataFolder(), config.getDatabaseSqliteFile());
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
        }

        hikari.setConnectionTimeout(10_000L);
        hikari.setIdleTimeout(600_000L);
        hikari.setMaxLifetime(1_800_000L);

        try {
            this.dataSource = new HikariDataSource(hikari);
            createTables();
            plugin.getLogger().info("[Temp] Database initialized (" + (isMySql ? "MySQL/MariaDB" : "SQLite")
                    + ", table=" + tableName + ").");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Temp] データベースの初期化に失敗しました。", e);
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "temperature DOUBLE NOT NULL,"
                + "updated_at BIGINT NOT NULL"
                + ")";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * プレイヤーの保存済み温度を非同期で読み込みます。未登録または失敗時は defaultTemp を返します。
     */
    public CompletableFuture<Double> loadAsync(UUID uuid, double defaultTemp) {
        if (dataSource == null || dataSource.isClosed()) {
            return CompletableFuture.completedFuture(defaultTemp);
        }
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT temperature FROM " + tableName + " WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double val = rs.getDouble("temperature");
                        if (Double.isFinite(val)) {
                            return val;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Temp] プレイヤー温度の読み込みに失敗しました: " + uuid, e);
            }
            return defaultTemp;
        }, dbExecutor);
    }

    /**
     * プレイヤーの温度を非同期で保存 (UPSERT) します。
     */
    public void saveAsync(UUID uuid, double temperature) {
        if (!Double.isFinite(temperature) || dataSource == null || dataSource.isClosed()) {
            return;
        }
        long now = System.currentTimeMillis();
        dbExecutor.execute(() -> upsertSingle(uuid, temperature, now));
    }

    /**
     * 全プレイヤーの温度スナップショットを非同期で一括保存します。
     */
    public void flushAllAsync(Map<UUID, Double> snapshot) {
        if (snapshot.isEmpty() || dataSource == null || dataSource.isClosed()) {
            return;
        }
        long now = System.currentTimeMillis();
        dbExecutor.execute(() -> upsertBatch(snapshot, now));
    }

    /**
     * サーバー停止時に同期で全プレイヤーの温度を一括保存し、接続プールを閉じます。
     */
    public void flushAllSyncAndClose(Map<UUID, Double> snapshot) {
        if (!snapshot.isEmpty() && dataSource != null && !dataSource.isClosed()) {
            upsertBatch(snapshot, System.currentTimeMillis());
        }
        dbExecutor.shutdown();
        try {
            dbExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private String upsertSql() {
        if (isMySql) {
            return "INSERT INTO " + tableName + " (uuid, temperature, updated_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE temperature = VALUES(temperature), updated_at = VALUES(updated_at)";
        }
        return "INSERT INTO " + tableName + " (uuid, temperature, updated_at) VALUES (?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET temperature = excluded.temperature, updated_at = excluded.updated_at";
    }

    private void upsertSingle(UUID uuid, double temperature, long updatedAt) {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql())) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, temperature);
            ps.setLong(3, updatedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Temp] プレイヤー温度の保存に失敗しました: " + uuid, e);
        }
    }

    private void upsertBatch(Map<UUID, Double> snapshot, long updatedAt) {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql())) {
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
                    ps.setLong(3, updatedAt);
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
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Temp] プレイヤー温度の一括保存に失敗しました。", e);
        }
    }
}
