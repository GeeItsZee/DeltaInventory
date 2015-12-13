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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public interface InventoryUtils
{
    static byte[] serialize(PlayerEntry entry) throws IOException
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

        byte[] uncompBytes = configuration.saveToString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(uncompBytes);
        gzip.close();
        bos.close();
        return bos.toByteArray();
    }

    static Map<String, ItemStack[]> deserialize(byte[] compBytes) throws InvalidConfigurationException, IOException
    {
        int nRead;
        byte[] data = new byte[2048];
        ByteArrayInputStream bis = new ByteArrayInputStream(compBytes);
        GZIPInputStream gzip = new GZIPInputStream(bis);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        while((nRead = gzip.read(data, 0, data.length)) != -1)
        {
            buffer.write(data, 0, nRead);
        }

        HashMap<String, ItemStack[]> map = new HashMap<>();
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(new String(buffer.toByteArray(), StandardCharsets.UTF_8));

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
