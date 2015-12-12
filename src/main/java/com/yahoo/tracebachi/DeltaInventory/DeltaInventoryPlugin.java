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
import com.yahoo.tracebachi.DeltaInventory.Listeners.PlayerListener;
import com.yahoo.tracebachi.DeltaInventory.Runnables.PlayerLoad;
import com.yahoo.tracebachi.DeltaInventory.Runnables.PlayerSaveInsert;
import com.yahoo.tracebachi.DeltaInventory.Runnables.PlayerSaveUpdate;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/11/15.
 */
public class DeltaInventoryPlugin extends JavaPlugin implements LoggablePlugin
{
    public static HikariDataSource dataSource;

    private static final String CREATE_TABLE_STATEMENT =
        " CREATE TABLE IF NOT EXISTS DeltaInv (" +
        " id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY," +
        " name CHAR(32) NOT NULL UNIQUE KEY," +
        " health DOUBLE NOT NULL," +
        " xp_level INT NOT NULL," +
        " xp_progress FLOAT NOT NULL," +
        " gamemode TINYINT NOT NULL," +
        " lastupdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
        " items BLOB" +
        " )";

    private boolean debug;
    private String dbUsername;
    private String dbPassword;
    private String dbUrl;

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
        debug = getConfig().getBoolean("debug_mode", false);
        dbUsername = getConfig().getString("Database.Username");
        dbPassword = getConfig().getString("Database.Password");
        getConfig().getItemStack("");
        dbUrl = getConfig().getString("Database.URL");

        createDataSource();
        if(!setupTable())
        {
            severe("Failed to create inventory table! Shutting down.");
            return;
        }

        DeltaEssentialsPlugin essPlugin = (DeltaEssentialsPlugin) getServer().
            getPluginManager().getPlugin("DeltaEssentials");

        listener = new PlayerListener(this, essPlugin);
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

        dataSource.close();
        dataSource = null;
    }

    @Override
    public void info(String message)
    {
        getLogger().info(message);
    }

    @Override
    public void severe(String message)
    {
        getLogger().severe(message);
    }

    @Override
    public void debug(String message)
    {
        if(debug)
        {
            getLogger().info("[Debug] " + message);
        }
    }

    public void saveInventory(PlayerEntry entry)
    {
        if(entry.getId() == null)
        {
            PlayerSaveInsert runnable = new PlayerSaveInsert(this, entry);
            getServer().getScheduler().runTaskAsynchronously(this, runnable);
            debug("Saving inventory async for {name:" + entry.getName() + ", id: N/A}" );
        }
        else
        {
            PlayerSaveUpdate runnable = new PlayerSaveUpdate(this, entry);
            getServer().getScheduler().runTaskAsynchronously(this, runnable);
            debug("Saving inventory async for {name:" + entry.getName() + ", id:" + entry.getId() + "}" );
        }
    }

    public void saveInventoryNow(PlayerEntry entry)
    {
        if(entry.getId() == null)
        {
            PlayerSaveInsert runnable = new PlayerSaveInsert(this, entry);
            debug("Saving inventory sync for {name:" + entry.getName() + ", id: N/A}" );
            runnable.runWithoutEvents();
        }
        else
        {
            PlayerSaveUpdate runnable = new PlayerSaveUpdate(this, entry);
            debug("Saving inventory sync for {name:" + entry.getName() + ", id:" + entry.getId() + "}" );
            runnable.runWithoutEvents();
        }
    }

    public void loadInventory(String name, Integer id)
    {
        PlayerLoad runnable = new PlayerLoad(this, name, id);
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
        debug("Loading inventory async for {name:" + name + ", id:" + id + "}" );
    }

    private void createDataSource()
    {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        if(dataSource != null)
        {
            dataSource.close();
        }
        info("Creating DataSource ...");
        dataSource = new HikariDataSource(config);
        info("....................... Done");
    }

    private boolean setupTable()
    {
        try(Connection connection = dataSource.getConnection())
        {
            try(Statement statement = connection.createStatement())
            {
                info("Creating Tables ...");
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
