package com.usermc.horizon.config;

import com.usermc.horizon.Horizon;
import org.bukkit.configuration.file.FileConfiguration;

public class HorizonConfig {

    // Database
    private final String dbHost, dbName, dbUser, dbPassword;
    private final int    dbPort;

    // Ship
    private final int movementIntervalTicks;
    private final int defaultSpeed, maxSpeed;
    private final int playerSoftLimit, playerHardLimit, adminLimit;
    private final int scanStepsPerTick;
    private final int autoSaveInterval;

    // Warp
    private final int    warpChargeSeconds;
    private final double blocksPerFuelUnit;

    // Crew
    private final long   moraleDecayIntervalTicks;
    private final double moraleDecayPerTick;
    private final int    baseSalary;

    // Messages
    private final String messagePrefix;

    // Gemini
    private final String geminiApiKey;
    private final String geminiModel;

    // Asteroids
    private final String asteroidWorldName;

    public HorizonConfig(Horizon plugin) {
        FileConfiguration c = plugin.getConfig();

        dbHost     = c.getString("database.host",     "localhost");
        dbPort     = c.getInt   ("database.port",     3306);
        dbName     = c.getString("database.name",     "horizon");
        dbUser     = c.getString("database.user",     "root");
        dbPassword = c.getString("database.password", "");

        movementIntervalTicks = c.getInt("ship.movement-interval-ticks", 10);
        defaultSpeed          = c.getInt("ship.default-speed",            2);
        maxSpeed              = c.getInt("ship.max-speed",                5);
        playerSoftLimit       = c.getInt("ship.player-soft-limit",     2500);
        playerHardLimit       = c.getInt("ship.player-hard-limit",     3000);
        adminLimit            = c.getInt("ship.admin-limit",          10000);
        scanStepsPerTick      = c.getInt("ship.scan-steps-per-tick",   1000);
        autoSaveInterval      = c.getInt("ship.auto-save-interval",      30);

        warpChargeSeconds  = c.getInt   ("warp.charge-time-seconds",   5);
        blocksPerFuelUnit  = c.getDouble("warp.blocks-per-fuel-unit", 100.0);

        moraleDecayIntervalTicks = c.getLong  ("crew.morale-decay-interval-ticks", 6000L);
        moraleDecayPerTick       = c.getDouble("crew.morale-decay-per-tick",       0.5);
        baseSalary               = c.getInt   ("crew.base-salary",                 100);

        messagePrefix = c.getString("messages.prefix", "&#00b4d8[Horizon] &r");

        geminiApiKey  = c.getString("gemini.api-key", "");
        geminiModel   = c.getString("gemini.model",   "gemma-4-31b-it");

        asteroidWorldName = c.getString("asteroids.world-name", "world");
    }

    // Database
    public String getDbHost()     { return dbHost; }
    public int    getDbPort()     { return dbPort; }
    public String getDbName()     { return dbName; }
    public String getDbUser()     { return dbUser; }
    public String getDbPassword() { return dbPassword; }

    // Ship
    public int getMovementIntervalTicks() { return movementIntervalTicks; }
    public int getDefaultSpeed()          { return defaultSpeed; }
    public int getMaxSpeed()              { return maxSpeed; }
    public int getPlayerSoftLimit()       { return playerSoftLimit; }
    public int getPlayerHardLimit()       { return playerHardLimit; }
    public int getAdminLimit()            { return adminLimit; }
    public int getScanStepsPerTick()      { return scanStepsPerTick; }
    public int getAutoSaveInterval()      { return autoSaveInterval; }

    // Warp
    public int    getWarpChargeSeconds()  { return warpChargeSeconds; }
    public double getBlocksPerFuelUnit()  { return blocksPerFuelUnit; }

    // Crew
    public long   getMoraleDecayIntervalTicks() { return moraleDecayIntervalTicks; }
    public double getMoraleDecayPerTick()       { return moraleDecayPerTick; }
    public int    getBaseSalary()               { return baseSalary; }

    // Messages
    public String getMessagePrefix() { return messagePrefix; }

    // Gemini
    public String getGeminiApiKey()  { return geminiApiKey; }
    public String getGeminiModel()   { return geminiModel; }

    // Asteroids
    public String getAsteroidWorldName() { return asteroidWorldName; }
}