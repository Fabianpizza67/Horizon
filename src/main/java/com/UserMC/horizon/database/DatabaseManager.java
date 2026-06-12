package com.usermc.horizon.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.usermc.horizon.Horizon;
import com.usermc.horizon.config.HorizonConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Horizon plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Horizon plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        HorizonConfig cfg = plugin.getHorizonConfig();
        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mariadb://" + cfg.getDbHost() + ":" + cfg.getDbPort()
                + "/" + cfg.getDbName() + "?autoReconnect=true&useSSL=false");
        hikari.setUsername(cfg.getDbUser());
        hikari.setPassword(cfg.getDbPassword());
        hikari.setMaximumPoolSize(5);
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(10_000);
        hikari.setIdleTimeout(300_000);
        hikari.setMaxLifetime(600_000);
        hikari.addDataSourceProperty("cachePrepStmts",        "true");
        hikari.addDataSourceProperty("prepStmtCacheSize",     "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.setPoolName("Horizon-DB");

        try {
            dataSource = new HikariDataSource(hikari);
            createTables();
            plugin.getLogger().info("Database connected.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Database connection failed: " + e.getMessage());
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_ships (
                    ship_id        VARCHAR(36)  PRIMARY KEY,
                    name           VARCHAR(64)  NOT NULL,
                    owner_uuid     VARCHAR(36)  NOT NULL,
                    ship_class     VARCHAR(32)  NOT NULL DEFAULT 'SHUTTLE',
                    world_name     VARCHAR(64)  NOT NULL,
                    core_x         INT          NOT NULL,
                    core_y         INT          NOT NULL,
                    core_z         INT          NOT NULL,
                    heading        FLOAT        NOT NULL DEFAULT 0.0,
                    status         VARCHAR(16)  NOT NULL DEFAULT 'DOCKED',
                    warp_status    VARCHAR(16)  NOT NULL DEFAULT 'IDLE',
                    fuel_level     INT          NOT NULL DEFAULT 0,
                    structure_data MEDIUMTEXT   NULL,
                    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_owner (owner_uuid),
                    INDEX idx_world (world_name)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_crew (
                    crew_id     VARCHAR(36)  PRIMARY KEY,
                    ship_id     VARCHAR(36)  NOT NULL,
                    npc_id      INT          NOT NULL DEFAULT -1,
                    name        VARCHAR(64)  NOT NULL,
                    species     VARCHAR(64)  NOT NULL DEFAULT 'Human',
                    role        VARCHAR(32)  NOT NULL,
                    skill_level INT          NOT NULL DEFAULT 1,
                    morale      DOUBLE       NOT NULL DEFAULT 100.0,
                    salary      INT          NOT NULL DEFAULT 100,
                    hired_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (ship_id) REFERENCES horizon_ships(ship_id) ON DELETE CASCADE,
                    INDEX idx_ship (ship_id)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_warp_beacons (
                    beacon_id   VARCHAR(36)  PRIMARY KEY,
                    name        VARCHAR(64)  NOT NULL,
                    world_name  VARCHAR(64)  NOT NULL,
                    x           INT          NOT NULL,
                    y           INT          NOT NULL,
                    z           INT          NOT NULL,
                    description TEXT         NOT NULL DEFAULT '',
                    admin_only  BOOLEAN      NOT NULL DEFAULT FALSE,
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_name (name)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_stations (
                    station_id  VARCHAR(36)  PRIMARY KEY,
                    ship_id     VARCHAR(36)  NOT NULL,
                    type        VARCHAR(32)  NOT NULL,
                    world_name  VARCHAR(64)  NOT NULL,
                    x           INT          NOT NULL,
                    y           INT          NOT NULL,
                    z           INT          NOT NULL,
                    FOREIGN KEY (ship_id) REFERENCES horizon_ships(ship_id) ON DELETE CASCADE,
                    INDEX idx_ship (ship_id)
                )
            """);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database pool closed.");
        }
    }
}