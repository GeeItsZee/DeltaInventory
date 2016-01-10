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
import com.yahoo.tracebachi.DeltaInventory.Exceptions.InventorySaveException;
import com.yahoo.tracebachi.DeltaInventory.InventoryUtils;
import com.yahoo.tracebachi.DeltaInventory.PlayerListener;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerSaveInsert implements Runnable
{
    private static final String STATEMENT_TEXT =
        " INSERT INTO deltainventory " +
            " (name, health, hunger, xp_level, xp_progress, gamemode, effects, items) " +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        " ON DUPLICATE KEY UPDATE " +
            " health = VALUES(health), " +
            " hunger = VALUES(hunger), " +
            " xp_level = VALUES(xp_level)," +
            " xp_progress = VALUES(xp_progress), " +
            " gamemode = VALUES(gamemode), " +
            " effects = VALUES(effects), " +
            " items = VALUES(items);";

    private static final String GET_ID =
        " SELECT id FROM deltainventory WHERE name = ? LIMIT 1;";

    private final PlayerEntry entry;
    private final PlayerListener listener;
    private final DeltaInventoryPlugin plugin;

    public PlayerSaveInsert(PlayerEntry entry, PlayerListener listener, DeltaInventoryPlugin plugin)
    {
        Preconditions.checkNotNull(plugin, "Plugin cannot be null.");
        Preconditions.checkNotNull(entry, "Entry cannot be null.");

        this.entry = entry;
        this.listener = listener;
        this.plugin = plugin;
    }

    @Override
    public void run()
    {
        try(Connection connection = DeltaInventoryPlugin.dataSource.getConnection())
        {
            try(PreparedStatement statement = connection.prepareStatement(STATEMENT_TEXT,
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
            }

            try(PreparedStatement statement = connection.prepareStatement(GET_ID))
            {
                statement.setString(1, entry.getName());
                try(ResultSet resultSet = statement.executeQuery())
                {
                    if(resultSet.next())
                    {
                        String name = entry.getName();
                        Integer id = resultSet.getInt("id");

                        Bukkit.getScheduler().runTask(plugin, () ->
                            listener.onInventorySaved(name, id));
                    }
                }
            }
        }
        catch(SQLException | IOException | InventorySaveException ex)
        {
            ex.printStackTrace();
            Bukkit.getScheduler().runTask(plugin, () -> listener.onInventorySaveFailure(
                entry.getName(), "Failed to save inventory! Do you have any " +
                    "illegal items in your inventory?"));
        }
    }
}
