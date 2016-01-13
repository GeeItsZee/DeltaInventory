/*
 * This file is part of DeltaInventory.
 *
 * DeltaInventory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DeltaInventory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DeltaInventory.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.yahoo.tracebachi.DeltaInventory;

import com.yahoo.tracebachi.DeltaEssentials.DeltaEssentialsPlugin;
import com.yahoo.tracebachi.DeltaInventory.Listeners.InventoryLockListener;
import com.yahoo.tracebachi.DeltaInventory.Listeners.PlayerListener;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/11/15.
 */
public class DeltaInventoryPlugin extends JavaPlugin
{
    public static HikariDataSource dataSource;

    private static final String CREATE_TABLE_STATEMENT =
        " CREATE TABLE IF NOT EXISTS deltainventory (" +
        " id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY," +
        " name CHAR(32) NOT NULL UNIQUE KEY," +
        " health DOUBLE NOT NULL," +
        " hunger INT NOT NULL," +
        " xp_level INT NOT NULL," +
        " xp_progress FLOAT NOT NULL," +
        " gamemode CHAR(10) NOT NULL," +
        " effects VARCHAR(256) NOT NULL," +
        " lastupdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
        " items BLOB" +
        " )";

    private boolean debugMode;
    private String databaseName;
    private PlayerListener playerListener;
    private InventoryLockListener inventoryLockListener;

    @Override
    public void onLoad()
    {
        saveDefaultConfig();
    }

    @Override
    public void onEnable()
    {
        reloadConfig();
        debugMode = getConfig().getBoolean("DebugMode", false);
        databaseName = getConfig().getString("Database");

        DeltaEssentialsPlugin dePlugin = (DeltaEssentialsPlugin) getServer()
            .getPluginManager().getPlugin("DeltaEssentials");

        dataSource = dePlugin.getDataSource(databaseName);
        if(dataSource == null)
        {
            severe("The specified database does not exist! Shutting down ...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if(!setupTable())
        {
            severe("Failed to create inventory table! Shutting down ...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        inventoryLockListener = new InventoryLockListener();
        getServer().getPluginManager().registerEvents(inventoryLockListener, this);
        playerListener = new PlayerListener(dePlugin, inventoryLockListener, this);
        getServer().getPluginManager().registerEvents(playerListener, this);
    }

    @Override
    public void onDisable()
    {
        if(playerListener != null)
        {
            playerListener.shutdown();
            playerListener = null;
        }

        if(inventoryLockListener != null)
        {
            inventoryLockListener.shutdown();
            inventoryLockListener = null;
        }

        dataSource = null;
    }

    public String getDatabaseName()
    {
        return databaseName;
    }

    public void info(String message)
    {
        getLogger().info(message);
    }

    public void severe(String message)
    {
        getLogger().severe(message);
    }

    public void debug(String message)
    {
        if(debugMode)
        {
            getLogger().info("[Debug] " + message);
        }
    }

    private boolean setupTable()
    {
        try(Connection connection = dataSource.getConnection())
        {
            try(Statement statement = connection.createStatement())
            {
                info("Creating `deltainventory` table ...");
                statement.executeUpdate(CREATE_TABLE_STATEMENT);
                info("Creating `deltainventory` table ... Done");
                return true;
            }
        }
        catch(SQLException ex)
        {
            ex.printStackTrace();
            return false;
        }
    }
}
