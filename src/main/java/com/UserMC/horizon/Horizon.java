package com.usermc.horizon;

import com.usermc.horizon.command.EconomyCommand;
import com.usermc.horizon.command.ShipCommand;
import com.usermc.horizon.config.HorizonConfig;
import com.usermc.horizon.crew.AutoPilot;
import com.usermc.horizon.crew.CrewManager;
import com.usermc.horizon.database.DatabaseManager;
import com.usermc.horizon.economy.EconomyManager;
import com.usermc.horizon.fuel.FuelManager;
import com.usermc.horizon.listener.*;
import com.usermc.horizon.mission.MissionManager;
import com.usermc.horizon.rank.RankManager;
import com.usermc.horizon.ship.ShipManager;
import com.usermc.horizon.ship.engine.ShipMovementEngine;
import com.usermc.horizon.station.StationItem;
import com.usermc.horizon.station.StationManager;
import com.usermc.horizon.station.gui.GuiManager;
import com.usermc.horizon.warp.WarpManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Horizon extends JavaPlugin {

    private static Horizon instance;

    private HorizonConfig       horizonConfig;
    private DatabaseManager     databaseManager;
    private ShipManager         shipManager;
    private ShipMovementEngine  movementEngine;
    private FuelManager         fuelManager;
    private WarpManager         warpManager;
    private CrewManager         crewManager;
    private AutoPilot           autoPilot;
    private StationManager      stationManager;
    private GuiManager          guiManager;
    private EconomyManager      economyManager;
    private RankManager         rankManager;
    private MissionManager      missionManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        horizonConfig = new HorizonConfig(this);

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to connect to database — disabling Horizon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Core systems (order matters — some depend on others)
        fuelManager    = new FuelManager(this);
        economyManager = new EconomyManager(this);
        rankManager    = new RankManager(this);
        warpManager    = new WarpManager(this);
        crewManager    = new CrewManager(this);
        autoPilot      = new AutoPilot(this);
        stationManager = new StationManager(this);
        guiManager     = new GuiManager();
        missionManager = new MissionManager(this);

        // Ship system
        shipManager = new ShipManager(this);
        shipManager.loadAll();

        movementEngine = new ShipMovementEngine(this);
        movementEngine.start();

        // Load persisted data
        warpManager.loadAll();
        crewManager.loadAll();
        stationManager.loadAll();
        rankManager.loadAll();
        missionManager.loadAll();

        // Crafting recipes for station blocks + fuel
        StationItem.registerRecipes(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new ShipCoreListener(this),    this);
        getServer().getPluginManager().registerEvents(new PlayerBoardListener(this), this);
        getServer().getPluginManager().registerEvents(new StationListener(this),     this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(this),    this);

        // Commands
        var shipCmd = new ShipCommand(this);
        getCommand("ship").setExecutor(shipCmd);
        getCommand("ship").setTabCompleter(shipCmd);

        var ecCmd = new EconomyCommand(this);
        getCommand("ec").setExecutor(ecCmd);
        getCommand("ec").setTabCompleter(ecCmd);

        getLogger().info("Horizon " + getPluginMeta().getVersion() + " online.");
    }

    @Override
    public void onDisable() {
        if (guiManager    != null) guiManager.closeAll();
        if (autoPilot     != null) autoPilot.stopAll();
        if (movementEngine!= null) movementEngine.stop();
        if (missionManager!= null) missionManager.shutdown();
        // Order matters: stations/crew/rank/economy reference ships via shipId,
        // and ship saves are heaviest (structure_data), so flush the smaller
        // tables first, ships last, then close the pool. All saveAll() calls
        // below are SYNCHRONOUS — they block briefly but guarantee the write
        // completes before databaseManager.close() runs.
        if (stationManager!= null) stationManager.saveAll();
        if (crewManager   != null) crewManager.saveAll();
        if (rankManager   != null) rankManager.saveAll();
        if (economyManager!= null) economyManager.saveAll();
        if (shipManager   != null) shipManager.saveAll();
        if (databaseManager!=null) databaseManager.close();
        getLogger().info("Horizon offline.");
    }

    /**
     * Synchronously flushes all dirty data to the database WITHOUT shutting
     * anything down — used by the admin "/ship save" command before a manual
     * restart, or just for peace of mind.
     */
    public void forceSaveAll() {
        if (shipManager   != null) shipManager.flushDirty();
        if (stationManager!= null) stationManager.flushDirty();
        if (crewManager   != null) crewManager.flushDirty();
        if (rankManager   != null) rankManager.flushDirty();
        if (economyManager!= null) economyManager.flushDirty();
    }

    public static Horizon getInstance()              { return instance; }
    public HorizonConfig    getHorizonConfig()       { return horizonConfig; }
    public DatabaseManager  getDatabaseManager()     { return databaseManager; }
    public ShipManager      getShipManager()         { return shipManager; }
    public ShipMovementEngine getMovementEngine()    { return movementEngine; }
    public FuelManager      getFuelManager()         { return fuelManager; }
    public WarpManager      getWarpManager()         { return warpManager; }
    public CrewManager      getCrewManager()         { return crewManager; }
    public AutoPilot        getAutoPilot()           { return autoPilot; }
    public StationManager   getStationManager()      { return stationManager; }
    public GuiManager       getGuiManager()          { return guiManager; }
    public EconomyManager   getEconomyManager()      { return economyManager; }
    public RankManager      getRankManager()         { return rankManager; }
    public MissionManager   getMissionManager()      { return missionManager; }
}