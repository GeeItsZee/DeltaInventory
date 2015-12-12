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

import com.google.common.base.Preconditions;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public interface InventoryUtils
{
    static byte[] serialize(PlayerEntry entry)
    {
        Preconditions.checkNotNull(entry, "Entry cannot be null.");
        YamlConfiguration configuration = new YamlConfiguration();

        if(entry.getArmor() != null)
        {
            ItemStack[] armor = entry.getArmor();
            for(int i = 0; i < armor.length; ++i)
            {
                if(armor[i] != null && armor[i].getType() != Material.AIR)
                {
                    configuration.set("Armor." + i, armor[i]);
                }
            }
        }

        if(entry.getSurvivalInventory() != null)
        {
            ItemStack[] inv = entry.getSurvivalInventory();
            for(int i = 0; i < inv.length; ++i)
            {
                if(inv[i] != null && inv[i].getType() != Material.AIR)
                {
                    configuration.set("Survival." + i, inv[i]);
                }
            }
        }

        if(entry.getCreativeInventory() != null)
        {
            ItemStack[] inv = entry.getCreativeInventory();
            for(int i = 0; i < inv.length; ++i)
            {
                if(inv[i] != null && inv[i].getType() != Material.AIR)
                {
                    configuration.set("Creative." + i, inv[i]);
                }
            }
        }

        if(entry.getEnderInventory() != null)
        {
            ItemStack[] inv = entry.getEnderInventory();
            for(int i = 0; i < inv.length; ++i)
            {
                if(inv[i] != null && inv[i].getType() != Material.AIR)
                {
                    configuration.set("Ender." + i, inv[i]);
                }
            }
        }

        // TODO Zip bytes?
        return configuration.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    static Map<String, ItemStack[]> deserialize(byte[] bytes) throws InvalidConfigurationException
    {
        // TODO Unzip Bytes?

        HashMap<String, ItemStack[]> map = new HashMap<>();
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(new String(bytes, StandardCharsets.UTF_8));

        ConfigurationSection armorSection = configuration.getConfigurationSection("Armor");
        map.put("Armor", readInventorySection(armorSection, 4));

        ConfigurationSection survivalSection = configuration.getConfigurationSection("Survival");
        map.put("Survival", readInventorySection(survivalSection, 36));

        ConfigurationSection creativeSection = configuration.getConfigurationSection("Creative");
        map.put("Creative", readInventorySection(creativeSection, 36));

        ConfigurationSection enderSection = configuration.getConfigurationSection("Ender");
        map.put("Ender", readInventorySection(enderSection, 27));

        return map;
    }

    static ItemStack[] readInventorySection(ConfigurationSection section, int maxSize)
    {
        if(section == null)
        {
            return null;
        }

        ItemStack[] destination = new ItemStack[maxSize];
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
        return destination;
    }
}
