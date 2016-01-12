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
import com.yahoo.tracebachi.DeltaInventory.Events.PlayerSavedEvent;
import com.yahoo.tracebachi.DeltaInventory.Exceptions.InventorySaveException;
import com.yahoo.tracebachi.DeltaInventory.InventoryUtils;
import com.yahoo.tracebachi.DeltaInventory.PlayerListener;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerSaveUpdate implements Runnable
{
    private static final String STATEMENT_TEXT =
        " UPDATE deltainventory SET " +
            " health = ?, " +
            " hunger = ?, " +
            " xp_level = ?, " +
            " xp_progress = ?, " +
            " gamemode = ?, " +
            " effects = ?, " +
            " items = ?" +
        " WHERE id = ?;";

    private final PlayerEntry entry;
    private final PlayerListener listener;
    private final DeltaInventoryPlugin plugin;

    public PlayerSaveUpdate(PlayerEntry entry, PlayerListener listener, DeltaInventoryPlugin plugin)
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
            try(PreparedStatement statement = connection.prepareStatement(STATEMENT_TEXT))
            {
                byte[] serializedInv = InventoryUtils.serialize(entry);

                statement.setDouble(1, entry.getHealth());
                statement.setInt(2, entry.getFoodLevel());
                statement.setInt(3, entry.getXpLevel());
                statement.setFloat(4, entry.getXpProgress());
                statement.setString(5, entry.getGameMode().toString());
                statement.setString(6, entry.getPotionEffects());
                statement.setBytes(7, serializedInv);
                statement.setInt(8, entry.getId());

                int changed = statement.executeUpdate();
                if(changed >= 1)
                {
                    String name = entry.getName();
                    Integer id = entry.getId();
                    plugin.debug("Saved inventory for {name:" + name + ", id:" + id + "}");

                    Bukkit.getScheduler().runTask(plugin, () ->
                        listener.onInventorySaved(name, id));
                }
            }
        }
        catch(SQLException | IOException | InventorySaveException ex)
        {
            ex.printStackTrace();
            plugin.debug("Failed to save inventory for {name:" + entry.getName() + "}");

            Bukkit.getScheduler().runTask(plugin, () ->
                listener.onInventorySaveFailure(entry.getName()));
        }
    }

    /**
     * Performs a save synchronously on shutdown. This method should never be
     * called asynchronously.
     */
    public void runForShutdown()
    {
        try(Connection connection = DeltaInventoryPlugin.dataSource.getConnection())
        {
            try(PreparedStatement statement = connection.prepareStatement(STATEMENT_TEXT))
            {
                byte[] serializedInv = InventoryUtils.serialize(entry);

                statement.setDouble(1, entry.getHealth());
                statement.setInt(2, entry.getFoodLevel());
                statement.setInt(3, entry.getXpLevel());
                statement.setFloat(4, entry.getXpProgress());
                statement.setString(5, entry.getGameMode().toString());
                statement.setString(6, entry.getPotionEffects());
                statement.setBytes(7, serializedInv);
                statement.setInt(8, entry.getId());

                int changed = statement.executeUpdate();
                if(changed >= 1)
                {
                    String name = entry.getName();
                    Integer id = entry.getId();
                    plugin.debug("Saved inventory for {name:" + name + ", id:" + id + "}");

                    Player player = Bukkit.getPlayer(name);
                    PlayerSavedEvent event = new PlayerSavedEvent(name, player);
                    Bukkit.getPluginManager().callEvent(event);
                }
            }
        }
        catch(SQLException | IOException | InventorySaveException ex)
        {
            ex.printStackTrace();
            plugin.debug("Failed to save inventory for {name:" + entry.getName() + "}");
        }
    }
}
