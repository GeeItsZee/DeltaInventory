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
package com.yahoo.tracebachi.DeltaInventory.Runnables;

import com.google.common.base.Preconditions;
import com.yahoo.tracebachi.DeltaInventory.DeltaInventoryPlugin;
import com.yahoo.tracebachi.DeltaInventory.Listeners.PlayerListener;
import com.yahoo.tracebachi.DeltaInventory.Storage.IPlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Utils.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerLoad implements Runnable
{
    private static final String SELECT_BY_NAME =
        " SELECT id, name, health, hunger, xp_level, xp_progress, gamemode, effects, items" +
        " FROM deltainventory" +
        " WHERE name = ?" +
        " LIMIT 1;";
    private static final String SELECT_BY_ID =
        " SELECT id, name, health, hunger, xp_level, xp_progress, gamemode, effects, items" +
        " FROM deltainventory" +
        " WHERE id = ?" +
        " LIMIT 1;";

    private final boolean isRunningSync;
    private final String name;
    private final Integer id;
    private final PlayerListener listener;
    private final DeltaInventoryPlugin plugin;

    public PlayerLoad(String name, Integer id, PlayerListener listener, DeltaInventoryPlugin plugin)
    {
        this(name, id, listener, plugin, false);
    }

    public PlayerLoad(String name, Integer id, PlayerListener listener, DeltaInventoryPlugin plugin,
        boolean isRunningSync)
    {
        Preconditions.checkNotNull(name, "Name cannot be null.");
        Preconditions.checkNotNull(listener, "Listener cannot be null.");
        Preconditions.checkNotNull(plugin, "Plugin cannot be null.");

        this.name = name.toLowerCase();
        this.id = id;
        this.listener = listener;
        this.plugin = plugin;
        this.isRunningSync = isRunningSync;
    }

    @Override
    public void run()
    {
        try(Connection connection = DeltaInventoryPlugin.dataSource.getConnection())
        {
            if(id == null)
            {
                try(PreparedStatement statement = connection.prepareStatement(SELECT_BY_NAME))
                {
                    statement.setString(1, name);
                    try(ResultSet resultSet = statement.executeQuery())
                    {
                        if(resultSet.next())
                        {
                            IPlayerEntry entry = getEntryFromResultSet(resultSet);
                            onSuccess(entry);
                        }
                        else
                        {
                            onNotFoundFailure();
                        }
                    }
                }
            }
            else
            {
                try(PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID))
                {
                    statement.setInt(1, id);
                    try(ResultSet resultSet = statement.executeQuery())
                    {
                        if(resultSet.next())
                        {
                            IPlayerEntry entry = getEntryFromResultSet(resultSet);
                            onSuccess(entry);
                        }
                        else
                        {
                            onNotFoundFailure();
                        }
                    }
                }
            }
        }
        catch(SQLException | InvalidConfigurationException | IOException ex)
        {
            ex.printStackTrace();
            onLoadFailure();
        }
    }

    private IPlayerEntry getEntryFromResultSet(ResultSet resultSet) throws
        SQLException, InvalidConfigurationException, IOException
    {
        Integer id = resultSet.getInt("id");
        String name = resultSet.getString("name");
        PlayerEntry entry = new PlayerEntry(id, name);

        entry.setHealth(resultSet.getDouble("health"));
        entry.setFoodLevel(resultSet.getInt("hunger"));
        entry.setXpLevel(resultSet.getInt("xp_level"));
        entry.setXpProgress(resultSet.getFloat("xp_progress"));
        entry.setGameMode(GameMode.valueOf(resultSet.getString("gamemode")));
        entry.setPotionEffects(resultSet.getString("effects"));

        byte[] itemBytes = resultSet.getBytes("items");
        InventoryUtils.deserialize(itemBytes, entry);
        return entry;
    }

    private void onSuccess(IPlayerEntry entry)
    {
        plugin.debug("Loaded inventory for {name:" + entry.getName() +
            ", id:" + entry.getId() + "}");

        if(isRunningSync)
        {
            listener.onInventoryLoaded(entry);
        }
        else
        {
            Bukkit.getScheduler().runTask(plugin,
                () -> listener.onInventoryLoaded(entry));
        }
    }

    private void onLoadFailure()
    {
        plugin.debug("Inventory could not be loaded for {name:" + name + "}");

        if(isRunningSync)
        {
            listener.onInventoryLoadFailure(name);
        }
        else
        {
            Bukkit.getScheduler().runTask(plugin,
                () -> listener.onInventoryLoadFailure(name));
        }
    }

    private void onNotFoundFailure()
    {
        plugin.debug("No inventory found for {name:" + name + "}");

        if(isRunningSync)
        {
            listener.onInventoryNotFound(name);
        }
        else
        {
            Bukkit.getScheduler().runTask(plugin,
                () -> listener.onInventoryNotFound(name));
        }
    }
}
