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

    public DatabaseManager(Horizon plugin) { this.plugin = plugin; }

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
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_ships (
                    ship_id VARCHAR(36) PRIMARY KEY, name VARCHAR(64) NOT NULL,
                    owner_uuid VARCHAR(36) NOT NULL, ship_class VARCHAR(32) NOT NULL DEFAULT 'SHUTTLE',
                    world_name VARCHAR(64) NOT NULL, core_x INT NOT NULL, core_y INT NOT NULL,
                    core_z INT NOT NULL, heading FLOAT NOT NULL DEFAULT 0.0,
                    status VARCHAR(16) NOT NULL DEFAULT 'DOCKED',
                    warp_status VARCHAR(16) NOT NULL DEFAULT 'IDLE',
                    fuel_level INT NOT NULL DEFAULT 0, structure_data MEDIUMTEXT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_owner (owner_uuid), INDEX idx_world (world_name)
                )""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_crew (
                    crew_id VARCHAR(36) PRIMARY KEY, ship_id VARCHAR(36) NOT NULL,
                    npc_id INT NOT NULL DEFAULT -1, name VARCHAR(64) NOT NULL,
                    species VARCHAR(64) NOT NULL DEFAULT 'Human', role VARCHAR(32) NOT NULL,
                    skill_level INT NOT NULL DEFAULT 1, morale DOUBLE NOT NULL DEFAULT 100.0,
                    salary INT NOT NULL DEFAULT 100, hired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (ship_id) REFERENCES horizon_ships(ship_id) ON DELETE CASCADE,
                    INDEX idx_ship (ship_id))""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_warp_beacons (
                    beacon_id VARCHAR(36) PRIMARY KEY, name VARCHAR(64) NOT NULL,
                    world_name VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                    description TEXT NOT NULL DEFAULT '', admin_only BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_name (name))""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_stations (
                    station_id VARCHAR(36) PRIMARY KEY, ship_id VARCHAR(36) NOT NULL,
                    type VARCHAR(32) NOT NULL, world_name VARCHAR(64) NOT NULL,
                    x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                    FOREIGN KEY (ship_id) REFERENCES horizon_ships(ship_id) ON DELETE CASCADE,
                    INDEX idx_ship (ship_id))""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_economy (
                    player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL,
                    balance BIGINT NOT NULL DEFAULT 0, total_earned BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_captains (
                    player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL,
                    rank VARCHAR(32) NOT NULL DEFAULT 'CADET', experience BIGINT NOT NULL DEFAULT 0,
                    missions_completed INT NOT NULL DEFAULT 0,
                    total_warp_distance BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_missions (
                    mission_id VARCHAR(36) PRIMARY KEY, type VARCHAR(32) NOT NULL,
                    title VARCHAR(128) NOT NULL, description TEXT NOT NULL,
                    target_beacon_id VARCHAR(36) NOT NULL, target_beacon_name VARCHAR(64) NOT NULL,
                    difficulty INT NOT NULL DEFAULT 1, reward_ec INT NOT NULL DEFAULT 100,
                    reward_xp BIGINT NOT NULL DEFAULT 25, expires_at BIGINT NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
                    accepted_by VARCHAR(36) NULL, accepted_at BIGINT NOT NULL DEFAULT 0,
                    INDEX idx_status (status))""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_story_arcs (
                    arc_id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    premise TEXT NOT NULL DEFAULT '',
                    current_chapter INT NOT NULL DEFAULT 1,
                    total_chapters INT NOT NULL DEFAULT 5,
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_player (player_uuid),
                    INDEX idx_status (status))""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_story_chapters (
                    chapter_id VARCHAR(36) PRIMARY KEY,
                    arc_id VARCHAR(36) NOT NULL,
                    chapter_number INT NOT NULL,
                    title VARCHAR(128) NOT NULL DEFAULT '',
                    narrative TEXT NOT NULL DEFAULT '',
                    objective_flavor TEXT NOT NULL DEFAULT '',
                    completion_text TEXT NOT NULL DEFAULT '',
                    objective_type VARCHAR(32) NOT NULL,
                    progress INT NOT NULL DEFAULT 0,
                    required INT NOT NULL DEFAULT 1,
                    reward_credits INT NOT NULL DEFAULT 0,
                    reward_xp INT NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    FOREIGN KEY (arc_id) REFERENCES horizon_story_arcs(arc_id) ON DELETE CASCADE,
                    INDEX idx_arc (arc_id))""");

            // Factions
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_factions (
                    faction_id   VARCHAR(36) PRIMARY KEY,
                    name         VARCHAR(64) UNIQUE NOT NULL,
                    description  TEXT        NOT NULL DEFAULT '',
                    leader_uuid  VARCHAR(36) NOT NULL,
                    bank_balance BIGINT      NOT NULL DEFAULT 0,
                    created_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
                )""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_faction_members (
                    player_uuid  VARCHAR(36) PRIMARY KEY,
                    faction_id   VARCHAR(36) NOT NULL,
                    player_name  VARCHAR(64) NOT NULL DEFAULT '',
                    rank         VARCHAR(16) NOT NULL DEFAULT 'RECRUIT',
                    joined_at    BIGINT      NOT NULL DEFAULT 0,
                    FOREIGN KEY (faction_id) REFERENCES horizon_factions(faction_id) ON DELETE CASCADE,
                    INDEX idx_faction (faction_id)
                )""");

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS horizon_faction_relations (
                    faction_a_id VARCHAR(36) NOT NULL,
                    faction_b_id VARCHAR(36) NOT NULL,
                    relation     VARCHAR(16) NOT NULL DEFAULT 'NEUTRAL',
                    established  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (faction_a_id, faction_b_id),
                    FOREIGN KEY (faction_a_id) REFERENCES horizon_factions(faction_id) ON DELETE CASCADE,
                    FOREIGN KEY (faction_b_id) REFERENCES horizon_factions(faction_id) ON DELETE CASCADE
                )""");
        }
    }

    public Connection getConnection() throws SQLException { return dataSource.getConnection(); }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database pool closed.");
        }
    }
}