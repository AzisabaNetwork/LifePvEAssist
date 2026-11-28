package net.azisaba.lifepveassist.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * LifePvEAssist 共通のデータベース接続プール管理クラス (LifeCore 準拠)。
 */
public final class DBConnector {

    private HikariDataSource dataSource;

    public void init(DatabaseConfig dbConfig) throws SQLException {
        close();

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("LifePvEAssist-DB");
        if (dbConfig.scheme() != null && dbConfig.scheme().startsWith("jdbc:mariadb")) {
            hikari.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());
        }
        hikari.setJdbcUrl(dbConfig.toJdbcUrl());
        hikari.setUsername(dbConfig.username());
        hikari.setPassword(dbConfig.password());
        hikari.setDataSourceProperties(dbConfig.toProperties());
        hikari.setConnectionTimeout(10_000L);
        hikari.setIdleTimeout(600_000L);
        hikari.setMaxLifetime(1_800_000L);

        HikariDataSource ds = new HikariDataSource(hikari);
        // 起動時に疎通を検証
        try (Connection ignored = ds.getConnection()) {
            this.dataSource = ds;
        } catch (SQLException e) {
            ds.close();
            throw e;
        }
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    public DataSource getDataSource() {
        if (!isInitialized()) {
            throw new IllegalStateException("DBConnector is not initialized");
        }
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
