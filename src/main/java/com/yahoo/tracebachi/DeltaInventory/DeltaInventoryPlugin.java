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
import com.yahoo.tracebachi.DeltaInventory.Runnables.PlayerLoad;
import com.yahoo.tracebachi.DeltaInventory.Runnables.PlayerSaveInsert;
import com.yahoo.tracebachi.DeltaInventory.Runnables.PlayerSaveUpdate;
import com.yahoo.tracebachi.DeltaInventory.Storage.ModifiablePlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
    private PlayerListener listener;

    @Override
    public void onLoad()
    {
        File file = new File(getDataFolder(), "config.yml");
        if(!file.exists())
        {
            saveDefaultConfig();
        }
    }

    @Override
    public void onEnable()
    {
        reloadConfig();
        debugMode = getConfig().getBoolean("DebugMode", false);
        String databaseName = getConfig().getString("Database");

        DeltaEssentialsPlugin dePlugin = (DeltaEssentialsPlugin) getServer().getPluginManager()
            .getPlugin("DeltaEssentials");

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

        listener = new PlayerListener(this, dePlugin);
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable()
    {
        if(listener != null)
        {
            listener.shutdown();
            listener = null;
        }

        dataSource = null;
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

    public void saveInventory(PlayerEntry entry)
    {
        if(entry.getId() == null)
        {
            PlayerSaveInsert runnable = new PlayerSaveInsert(entry, listener, this);
            getServer().getScheduler().runTaskAsynchronously(this, runnable);
            debug("Saving inventory async for {name:" + entry.getName() + ", id: N/A}" );
        }
        else
        {
            PlayerSaveUpdate runnable = new PlayerSaveUpdate(entry, listener, this);
            getServer().getScheduler().runTaskAsynchronously(this, runnable);
            debug("Saving inventory async for {name:" + entry.getName() + ", id:" + entry.getId() + "}" );
        }
    }

    public void saveInventoryNow(PlayerEntry entry)
    {
        if(entry.getId() == null)
        {
            PlayerSaveInsert runnable = new PlayerSaveInsert(entry, listener, this);
            debug("Saving inventory sync for {name:" + entry.getName() + ", id: N/A}" );
            runnable.run();
        }
        else
        {
            PlayerSaveUpdate runnable = new PlayerSaveUpdate(entry, listener, this);
            debug("Saving inventory sync for {name:" + entry.getName() + ", id:" + entry.getId() + "}" );
            runnable.run();
        }
    }

    public void loadInventory(String name, Integer id)
    {
        PlayerLoad runnable = new PlayerLoad(name, id, listener, this);
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
        debug("Loading inventory async for {name:" + name + ", id:" + id + "}" );
    }

    private boolean setupTable()
    {
        try(Connection connection = dataSource.getConnection())
        {
            try(Statement statement = connection.createStatement())
            {
                info("Creating Table ...");
                statement.executeUpdate(CREATE_TABLE_STATEMENT);
                info("................... Done");
                statement.close();
            }
            connection.close();
            return true;
        }
        catch(SQLException ex)
        {
            ex.printStackTrace();
            return false;
        }
    }
}
