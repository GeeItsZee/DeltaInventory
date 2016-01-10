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
import com.yahoo.tracebachi.DeltaInventory.Exceptions.InventorySaveException;
import com.yahoo.tracebachi.DeltaInventory.Storage.ModifiablePlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerInventory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        ItemStack[] itemStacks;

        if(entry.getSurvivalInventory() != null)
        {
            PlayerInventory playerInv = entry.getSurvivalInventory();

            itemStacks = playerInv.getArmor();
            for(int i = 0; i < itemStacks.length; ++i)
            {
                if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
                {
                    configuration.set("Survival.Armor." + i, itemStacks[i]);
                }
            }

            itemStacks = playerInv.getContents();
            for(int i = 0; i < itemStacks.length; ++i)
            {
                if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
                {
                    configuration.set("Survival.Inv." + i, itemStacks[i]);
                }
            }
        }

        if(entry.getCreativeInventory() != null)
        {
            PlayerInventory playerInv = entry.getCreativeInventory();

            itemStacks = playerInv.getArmor();
            for(int i = 0; i < itemStacks.length; ++i)
            {
                if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
                {
                    configuration.set("Creative.Armor." + i, itemStacks[i]);
                }
            }

            itemStacks = playerInv.getContents();
            for(int i = 0; i < itemStacks.length; ++i)
            {
                if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
                {
                    configuration.set("Creative.Inv." + i, itemStacks[i]);
                }
            }
        }

        if(entry.getEnderInventory() != null)
        {
            itemStacks = entry.getEnderInventory();
            for(int i = 0; i < itemStacks.length; ++i)
            {
                if(itemStacks[i] != null && itemStacks[i].getType() != Material.AIR)
                {
                    configuration.set("EnderChest." + i, itemStacks[i]);
                }
            }
        }

        try
        {
            byte[] uncompBytes = configuration.saveToString().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);

            gzip.write(uncompBytes);
            gzip.close();
            bos.close();
            return bos.toByteArray();
        }
        catch(NullPointerException ex)
        {
            throw new InventorySaveException(ex);
        }
    }

    static void deserialize(byte[] compBytes, ModifiablePlayerEntry entry) throws InvalidConfigurationException, IOException
    {
        int nRead;
        byte[] data = new byte[2048];
        ByteArrayInputStream bis = new ByteArrayInputStream(compBytes);
        GZIPInputStream gzip = new GZIPInputStream(bis);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PlayerInventory playerInventory;

        while((nRead = gzip.read(data, 0, data.length)) != -1)
        {
            buffer.write(data, 0, nRead);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(new String(buffer.toByteArray(), StandardCharsets.UTF_8));

        ConfigurationSection survivalSection = configuration.getConfigurationSection("Survival");
        playerInventory = readPlayerInventory(survivalSection);
        entry.setSurvivalInventory(playerInventory);

        ConfigurationSection creativeSection = configuration.getConfigurationSection("Creative");
        playerInventory = readPlayerInventory(creativeSection);
        entry.setCreativeInventory(playerInventory);

        ConfigurationSection enderChestSection = configuration.getConfigurationSection("EnderChest");
        ItemStack[] itemStacks = readItemStacks(enderChestSection, 27);
        entry.setEnderInventory(itemStacks);
    }

    static PlayerInventory readPlayerInventory(ConfigurationSection section)
    {
        if(section != null)
        {
            ItemStack[] armor = readItemStacks(section.getConfigurationSection("Armor"), 4);
            ItemStack[] inventory = readItemStacks(section.getConfigurationSection("Inv"), 36);
            return new PlayerInventory(armor, inventory);
        }
        else
        {
            return new PlayerInventory(new ItemStack[4], new ItemStack[36]);
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
