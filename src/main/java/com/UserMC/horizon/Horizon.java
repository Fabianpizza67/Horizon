package com.usermc.horizon;

import com.usermc.horizon.command.ShipCommand;
import com.usermc.horizon.config.HorizonConfig;
import com.usermc.horizon.crew.AutoPilot;
import com.usermc.horizon.crew.CrewManager;
import com.usermc.horizon.database.DatabaseManager;
import com.usermc.horizon.fuel.FuelManager;
import com.usermc.horizon.listener.*;
import com.usermc.horizon.ship.ShipManager;
import com.usermc.horizon.ship.engine.ShipMovementEngine;
import com.usermc.horizon.station.StationItem;
import com.usermc.horizon.station.StationManager;
import com.usermc.horizon.station.gui.GuiManager;
import com.usermc.horizon.warp.WarpManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Horizon extends JavaPlugin {

    private static Horizon instance;

    private HorizonConfig      horizonConfig;
    private DatabaseManager    databaseManager;
    private ShipManager        shipManager;
    private ShipMovementEngine movementEngine;
    private FuelManager        fuelManager;
    private WarpManager        warpManager;
    private CrewManager        crewManager;
    private AutoPilot          autoPilot;
    private StationManager     stationManager;
    private GuiManager         guiManager;

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

        fuelManager    = new FuelManager(this);
        warpManager    = new WarpManager(this);
        crewManager    = new CrewManager(this);
        autoPilot      = new AutoPilot(this);
        stationManager = new StationManager(this);
        guiManager     = new GuiManager();

        shipManager = new ShipManager(this);
        shipManager.loadAll();

        movementEngine = new ShipMovementEngine(this);
        movementEngine.start();

        warpManager.loadAll();
        crewManager.loadAll();
        stationManager.loadAll();

        // Register crafting recipes for station items
        StationItem.registerRecipes(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new ShipCoreListener(this),    this);
        getServer().getPluginManager().registerEvents(new PlayerBoardListener(this), this);
        getServer().getPluginManager().registerEvents(new StationListener(this),     this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(this),    this);

        var cmd = new ShipCommand(this);
        getCommand("ship").setExecutor(cmd);
        getCommand("ship").setTabCompleter(cmd);

        getLogger().info("Horizon " + getPluginMeta().getVersion() + " online.");
    }

    @Override
    public void onDisable() {
        if (guiManager     != null) guiManager.closeAll();
        if (autoPilot      != null) autoPilot.stopAll();
        if (movementEngine != null) movementEngine.stop();
        if (stationManager != null) stationManager.saveAll();
        if (crewManager    != null) crewManager.saveAll();
        if (shipManager    != null) shipManager.saveAll();
        if (databaseManager!= null) databaseManager.close();
        getLogger().info("Horizon offline.");
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
}