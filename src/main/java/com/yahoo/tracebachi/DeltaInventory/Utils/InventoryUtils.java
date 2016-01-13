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

import com.google.common.base.Preconditions;
import com.yahoo.tracebachi.DeltaInventory.Storage.IPlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.SavedInventory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public interface InventoryUtils
{
    static byte[] serialize(IPlayerEntry entry) throws
        IOException, NullPointerException
    {
        Preconditions.checkNotNull(entry, "Entry cannot be null.");

        YamlConfiguration configuration = new YamlConfiguration();
        SavedInventory playerInv;

        // Save survival inventory + armor
        playerInv = entry.getSurvival();
        writeItemStacks(configuration, "Survival.Armor", playerInv.getArmor());
        writeItemStacks(configuration, "Survival.Inv", playerInv.getContents());

        // Save creative inventory + armor
        playerInv = entry.getCreative();
        writeItemStacks(configuration, "Creative.Armor", playerInv.getArmor());
        writeItemStacks(configuration, "Creative.Inv", playerInv.getContents());

        // Save ender chest
        writeItemStacks(configuration, "EnderChest", entry.getEnderChest());

        byte[] decompressed = configuration.saveToString().getBytes(StandardCharsets.UTF_8);
        return CompressionUtils.compress(decompressed);
    }

    static void deserialize(byte[] compressed, PlayerEntry entry) throws
        IOException, InvalidConfigurationException
    {
        Preconditions.checkNotNull(compressed, "Bytes cannot be null.");
        Preconditions.checkNotNull(entry, "Entry cannot be null.");

        ConfigurationSection section;
        YamlConfiguration configuration = new YamlConfiguration();
        byte[] decompressed = CompressionUtils.decompress(compressed);
        configuration.loadFromString(new String(decompressed, StandardCharsets.UTF_8));

        section = configuration.getConfigurationSection("Survival");
        entry.setSurvival(readSavedInventory(section));

        section = configuration.getConfigurationSection("Creative");
        entry.setCreative(readSavedInventory(section));

        section = configuration.getConfigurationSection("EnderChest");
        entry.setEnderChest(readItemStacks(section, 27));
    }

    static SavedInventory readSavedInventory(ConfigurationSection section)
    {
        if(section != null)
        {
            ItemStack[] armor = readItemStacks(section.getConfigurationSection("Armor"), 4);
            ItemStack[] inventory = readItemStacks(section.getConfigurationSection("Inv"), 36);
            return new SavedInventory(armor, inventory);
        }
        return SavedInventory.EMPTY;
    }

    static void writeItemStacks(YamlConfiguration configuration, String prefix, ItemStack[] itemStacks)
    {
        for(int i = 0; i < itemStacks.length; ++i)
        {
            if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
            {
                configuration.set(prefix + "." + i, itemStacks[i]);
            }
        }
    }

    static ItemStack[] readItemStacks(ConfigurationSection section, int maxSize)
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
}
