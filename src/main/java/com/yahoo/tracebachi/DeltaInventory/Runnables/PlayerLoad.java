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
import com.yahoo.tracebachi.DeltaInventory.Events.InventoryLoadEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.NoInventoryFoundEvent;
import com.yahoo.tracebachi.DeltaInventory.InventoryUtils;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerLoad implements Runnable
{
    private static final String SELECT_BY_NAME =
        " SELECT id, name, health, hunger, xp_level, xp_progress, gamemode, potion_effects, items" +
            " FROM DeltaInv" +
            " WHERE name=?" +
            " LIMIT 1;";

    private static final String SELECT_BY_ID =
        " SELECT id, name, health, hunger, xp_level, xp_progress, gamemode, potion_effects, items" +
            " FROM DeltaInv" +
            " WHERE id=?" +
            " LIMIT 1;";

    private final DeltaInventoryPlugin plugin;
    private final String name;
    private final Integer id;

    public PlayerLoad(DeltaInventoryPlugin plugin, String name)
    {
        this(plugin, name, null);
    }

    public PlayerLoad(DeltaInventoryPlugin plugin, String name, Integer id)
    {
        Preconditions.checkNotNull(plugin, "Plugin cannot be null.");
        Preconditions.checkNotNull(name, "Name cannot be null.");

        this.plugin = plugin;
        this.name = name.toLowerCase();
        this.id = id;
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
                        handleResultSet(resultSet);
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
                        handleResultSet(resultSet);
                    }
                }
            }
        }
        catch(SQLException | InvalidConfigurationException | IOException ex)
        {
            ex.printStackTrace();

            NoInventoryFoundEvent event = new NoInventoryFoundEvent(name);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(event);
            });
        }
    }

    private void handleResultSet(ResultSet resultSet) throws SQLException, InvalidConfigurationException, IOException
    {
        if(resultSet.next())
        {
            PlayerEntry entry = getEntryFromResultSet(resultSet);
            InventoryLoadEvent event = new InventoryLoadEvent(entry);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(event);
            });
        }
        else
        {
            NoInventoryFoundEvent event = new NoInventoryFoundEvent(name);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(event);
            });
        }
    }

    private PlayerEntry getEntryFromResultSet(ResultSet resultSet) throws SQLException, InvalidConfigurationException, IOException
    {
        PlayerEntry entry = new PlayerEntry();

        entry.setId(resultSet.getInt("id"));
        entry.setName(resultSet.getString("name"));
        entry.setHealth(resultSet.getDouble("health"));
        entry.setFoodLevel(resultSet.getInt("hunger"));
        entry.setXpLevel(resultSet.getInt("xp_level"));
        entry.setXpProgress(resultSet.getFloat("xp_progress"));
        entry.setGameMode(resultSet.getInt("gamemode"));
        entry.setPotionEffects(resultSet.getString("potion_effects"));

        byte[] itemBytes = resultSet.getBytes("items");
        Map<String, ItemStack[]> items = InventoryUtils.deserialize(itemBytes);

        entry.setSurvivalInventory(items.get("Survival"));
        entry.setCreativeInventory(items.get("Creative"));
        entry.setArmor(items.get("Armor"));
        entry.setEnderInventory(items.get("Ender"));

        return entry;
    }
}
