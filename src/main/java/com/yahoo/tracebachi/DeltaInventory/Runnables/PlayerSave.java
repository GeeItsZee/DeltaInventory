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
import com.yahoo.tracebachi.DeltaInventory.Utils.InventoryUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerSave implements Runnable
{
    private static final String SELECT_PLAYER =
        " SELECT id FROM deltainventory" +
        " WHERE name = ?" +
        " LIMIT 1;";
    private static final String UPDATE_PLAYER =
        " UPDATE deltainventory SET" +
        " health = ?," +
        " hunger = ?," +
        " xp_level = ?," +
        " xp_progress = ?," +
        " gamemode = ?," +
        " effects = ?," +
        " items = ?" +
        " WHERE id = ?;";
    private static final String INSERT_PLAYER =
        " INSERT INTO deltainventory" +
        " (name, health, hunger, xp_level, xp_progress, gamemode, effects, items)" +
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

    private final boolean isRunningSync;
    private final IPlayerEntry entry;
    private final PlayerListener listener;
    private final DeltaInventoryPlugin plugin;

    public PlayerSave(IPlayerEntry entry, PlayerListener listener, DeltaInventoryPlugin plugin)
    {
        this(entry, listener, plugin, false);
    }

    public PlayerSave(IPlayerEntry entry, PlayerListener listener, DeltaInventoryPlugin plugin, boolean isRunningSync)
    {
        Preconditions.checkNotNull(plugin, "Plugin cannot be null.");
        Preconditions.checkNotNull(entry, "Entry cannot be null.");

        this.entry = entry;
        this.listener = listener;
        this.plugin = plugin;
        this.isRunningSync = isRunningSync;
    }

    @Override
    public void run()
    {
        try(Connection connection = DeltaInventoryPlugin.dataSource.getConnection())
        {
            Integer foundId = entry.getId();
            boolean success;

            if(foundId != null)
            {
                // If the ID is known, use an UPDATE
                success = updatePlayer(foundId, connection);
            }
            else
            {
                // To find the ID, use a SELECT
                foundId = selectPlayer(connection);

                if(foundId != null)
                {
                    // Since ID was found, use an UPDATE
                    success = updatePlayer(foundId, connection);
                }
                else
                {
                    // Since ID was not found, use an INSERT
                    foundId = insertPlayer(connection);
                    success = (foundId != null);
                }
            }

            // Handle the result of the save run
            if(success) onSuccess(foundId);
            else onFailure();
        }
        catch(SQLException | IOException | NullPointerException ex)
        {
            ex.printStackTrace();
            onFailure();
        }
    }

    private Integer selectPlayer(Connection connection) throws SQLException
    {
        try(PreparedStatement statement = connection.prepareStatement(SELECT_PLAYER))
        {
            statement.setString(1, entry.getName());
            try(ResultSet resultSet = statement.executeQuery())
            {
                if(resultSet.next())
                {
                    return resultSet.getInt("id");
                }
                return null;
            }
        }
    }

    private boolean updatePlayer(Integer foundId, Connection connection) throws
        SQLException, IOException, NullPointerException
    {
        try(PreparedStatement statement = connection.prepareStatement(UPDATE_PLAYER))
        {
            byte[] serializedInv = InventoryUtils.serialize(entry);

            statement.setDouble(1, entry.getHealth());
            statement.setInt(2, entry.getFoodLevel());
            statement.setInt(3, entry.getXpLevel());
            statement.setFloat(4, entry.getXpProgress());
            statement.setString(5, entry.getGameMode().toString());
            statement.setString(6, entry.getPotionEffects());
            statement.setBytes(7, serializedInv);

            if(entry.getId() != null)
            {
                statement.setInt(8, entry.getId());
            }
            else
            {
                statement.setInt(8, foundId);
            }

            return (statement.executeUpdate() != 0);
        }
    }

    private Integer insertPlayer(Connection connection) throws
        SQLException, IOException, NullPointerException
    {
        try(PreparedStatement statement = connection.prepareStatement(INSERT_PLAYER,
            PreparedStatement.RETURN_GENERATED_KEYS))
        {
            byte[] serializedInv = InventoryUtils.serialize(entry);

            statement.setString(1, entry.getName());
            statement.setDouble(2, entry.getHealth());
            statement.setInt(3, entry.getFoodLevel());
            statement.setInt(4, entry.getXpLevel());
            statement.setFloat(5, entry.getXpProgress());
            statement.setString(6, entry.getGameMode().toString());
            statement.setString(7, entry.getPotionEffects());
            statement.setBytes(8, serializedInv);

            statement.executeUpdate();

            try(ResultSet keySet = statement.getGeneratedKeys())
            {
                if(keySet.next())
                {
                    return keySet.getInt(1);
                }
                return null;
            }
        }
    }

    private void onSuccess(Integer foundId)
    {
        plugin.debug("Saved inventory for {name:" + entry.getName() + ", id:" + foundId + "}");

        if(isRunningSync)
        {
            listener.onInventorySaved(entry.getName(), foundId);
        }
        else
        {
            final Integer finalFoundId = foundId;
            plugin.getServer().getScheduler().runTask(plugin,
                () -> listener.onInventorySaved(entry.getName(), finalFoundId));
        }
    }

    private void onFailure()
    {
        plugin.debug("Failed to save inventory for {name:" + entry.getName() + "}");

        if(isRunningSync)
        {
            listener.onInventorySaveFailure(entry.getName());
        }
        else
        {
            plugin.getServer().getScheduler().runTask(plugin,
                () -> listener.onInventorySaveFailure(entry.getName()));
        }
    }
}
