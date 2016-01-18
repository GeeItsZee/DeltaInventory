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
package com.yahoo.tracebachi.DeltaInventory.Utils;

import com.yahoo.tracebachi.DeltaInventory.Storage.SavedInventory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public interface InventoryUtils
{
    static YamlConfiguration toYamlSection(ItemStack[] itemStacks)
    {
        YamlConfiguration configuration = new YamlConfiguration();

        for(int i = 0; i < itemStacks.length; ++i)
        {
            if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
            {
                configuration.set(Integer.toString(i), itemStacks[i]);
            }
        }

        return configuration;
    }

    static ItemStack[] toItemStacks(ConfigurationSection section, int maxSize)
    {
        ItemStack[] destination = new ItemStack[maxSize];

        if(section != null)
        {
            for(String key : section.getKeys(false))
            {
                try
                {
                    Integer keyAsInt = Integer.parseInt(key);
                    destination[keyAsInt] = section.getItemStack(key);
                }
                catch(NumberFormatException ex)
                {
                    ex.printStackTrace();
                }
            }
        }

        return destination;
    }

    static SavedInventory toSavedInventory(ConfigurationSection section)
    {
        if(section != null)
        {
            ItemStack[] armor = toItemStacks(section.getConfigurationSection("Armor"), 4);
            ItemStack[] inventory = toItemStacks(section.getConfigurationSection("Contents"), 36);
            return new SavedInventory(armor, inventory);
        }
        return SavedInventory.EMPTY;
    }
}
