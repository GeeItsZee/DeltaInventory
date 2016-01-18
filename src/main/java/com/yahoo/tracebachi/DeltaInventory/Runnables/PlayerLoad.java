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
import com.yahoo.tracebachi.DeltaInventory.Storage.SavedInventory;
import com.yahoo.tracebachi.DeltaInventory.Utils.InventoryUtils;
import com.yahoo.tracebachi.DeltaInventory.Utils.PotionEffectUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerLoad implements Runnable
{
    private final boolean isRunningSync;
    private final String name;
    private final String playerDataFolder;
    private final PlayerListener listener;
    private final DeltaInventoryPlugin plugin;

    public PlayerLoad(String name, PlayerListener listener, DeltaInventoryPlugin plugin)
    {
        this(name, listener, plugin, false);
    }

    public PlayerLoad(String name, PlayerListener listener, DeltaInventoryPlugin plugin,
        boolean isRunningSync)
    {
        Preconditions.checkNotNull(name, "Name cannot be null.");
        Preconditions.checkNotNull(listener, "Listener cannot be null.");
        Preconditions.checkNotNull(plugin, "Plugin cannot be null.");

        this.name = name.toLowerCase();
        this.playerDataFolder = plugin.getPlayerDataFolder();
        this.listener = listener;
        this.plugin = plugin;
        this.isRunningSync = isRunningSync;
    }

    @Override
    public void run()
    {
        Path path = Paths.get(playerDataFolder + File.separator +
            name.charAt(0) + File.separator + name + ".yml");

        if(!path.toFile().exists())
        {
            onNotFoundFailure();
            return;
        }

        String fileContents = readFileWithLock(path);
        if(fileContents == null)
        {
            onLoadFailure();
            return;
        }

        IPlayerEntry entry;
        YamlConfiguration configuration = new YamlConfiguration();
        try
        {
            configuration.loadFromString(fileContents);
            entry = readPlayerDataYaml(configuration);
            onSuccess(entry);
        }
        catch(InvalidConfigurationException e)
        {
            e.printStackTrace();
            onLoadFailure();
        }
    }

    private IPlayerEntry readPlayerDataYaml(YamlConfiguration configuration)
    {
        ItemStack[] itemStacks;
        ConfigurationSection section;
        SavedInventory savedInventory;
        List<PotionEffect> effects;
        PlayerEntry entry = new PlayerEntry(name);

        entry.setHealth(configuration.getDouble("Health", 20.0));
        entry.setFoodLevel(configuration.getInt("Hunger", 20));
        entry.setXpLevel(configuration.getInt("XpLevel", 0));
        entry.setXpProgress(configuration.getDouble("XpProgress", 0.0));
        entry.setGameMode(GameMode.valueOf(configuration.getString("Gamemode", "SURVIVAL")));

        effects = PotionEffectUtils.toEffectList(configuration.getStringList("Effects"));
        entry.setPotionEffects(effects);

        section = configuration.getConfigurationSection("Survival");
        savedInventory = InventoryUtils.toSavedInventory(section);
        entry.setSurvival(savedInventory);

        section = configuration.getConfigurationSection("Creative");
        savedInventory = InventoryUtils.toSavedInventory(section);
        entry.setCreative(savedInventory);

        section = configuration.getConfigurationSection("EnderChest");
        itemStacks = InventoryUtils.toItemStacks(section, 27);
        entry.setEnderChest(itemStacks);

        // TODO Meta section?

        return entry;
    }

    private void onSuccess(IPlayerEntry entry)
    {
        plugin.debug("Loaded inventory for {name:" + entry.getName() + "}");

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

    private String readFileWithLock(Path path)
    {
        FileLock lock = null;

        try
        {
            FileChannel fileChannel = FileChannel.open(path,
                StandardOpenOption.READ, StandardOpenOption.WRITE);

            // Lock the file
            lock = fileChannel.lock();

            ByteBuffer buffer = ByteBuffer.allocate((int) fileChannel.size());
            fileChannel.read(buffer);

            // Unlock the file
            lock.release();

            return new String(buffer.array(), StandardCharsets.UTF_16);
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            if(lock != null)
            {
                try
                {
                    lock.release();
                }
                catch(IOException ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        return null;
    }
}
