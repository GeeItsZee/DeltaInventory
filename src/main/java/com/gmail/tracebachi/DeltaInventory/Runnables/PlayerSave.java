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
package com.gmail.tracebachi.DeltaInventory.Runnables;

import com.gmail.tracebachi.DeltaInventory.Storage.IPlayerEntry;
import com.google.common.base.Preconditions;
import com.gmail.tracebachi.DeltaInventory.DeltaInventoryPlugin;
import com.gmail.tracebachi.DeltaInventory.Listeners.PlayerListener;
import com.gmail.tracebachi.DeltaInventory.Utils.InventoryUtils;
import com.gmail.tracebachi.DeltaInventory.Utils.PotionEffectUtils;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Created by Trace Bachi (tracebachi@gmail.com, BigBossZee) on 12/12/15.
 */
public class PlayerSave implements Runnable
{
    private final boolean isRunningSync;
    private final String playerDataFolder;
    private final IPlayerEntry entry;
    private final PlayerListener listener;
    private final DeltaInventoryPlugin plugin;

    public PlayerSave(IPlayerEntry entry, PlayerListener listener, DeltaInventoryPlugin plugin)
    {
        this(entry, listener, plugin, false);
    }

    public PlayerSave(IPlayerEntry entry, PlayerListener listener, DeltaInventoryPlugin plugin, boolean isRunningSync)
    {
        Preconditions.checkNotNull(entry, "Entry cannot be null.");
        Preconditions.checkNotNull(listener, "Listener cannot be null.");
        Preconditions.checkNotNull(plugin, "Plugin cannot be null.");

        this.entry = entry;
        this.playerDataFolder = plugin.getPlayerDataFolder();
        this.listener = listener;
        this.plugin = plugin;
        this.isRunningSync = isRunningSync;
    }

    @Override
    public void run()
    {
        try
        {
            YamlConfiguration configuration = writePlayerDataYaml();
            String source = configuration.saveToString();

            Path path = Paths.get(playerDataFolder + File.separator +
                entry.getName().charAt(0) + File.separator + entry.getName() + ".yml");

            if(writeFileWithLock(source, path))
            {
                onSuccess();
            }
            else
            {
                onFailure();
            }
        }
        catch(NullPointerException e)
        {
            e.printStackTrace();
            onFailure();
        }
    }

    private YamlConfiguration writePlayerDataYaml()
    {
        YamlConfiguration serialized;
        YamlConfiguration configuration = new YamlConfiguration();

        configuration.set("LastSave", System.currentTimeMillis());

        configuration.set("Health", entry.getHealth());
        configuration.set("Hunger", entry.getFoodLevel());
        configuration.set("XpLevel", entry.getXpLevel());
        configuration.set("XpProgress", entry.getXpProgress());
        configuration.set("Gamemode", entry.getGameMode().toString());
        configuration.set("Effects", PotionEffectUtils.toStringList(entry.getPotionEffects()));

        serialized = InventoryUtils.toYamlSection(entry.getSurvival().getArmor());
        configuration.set("Survival.Armor", serialized);

        serialized = InventoryUtils.toYamlSection(entry.getSurvival().getContents());
        configuration.set("Survival.Contents", serialized);

        serialized = InventoryUtils.toYamlSection(entry.getCreative().getArmor());
        configuration.set("Creative.Armor", serialized);

        serialized = InventoryUtils.toYamlSection(entry.getCreative().getArmor());
        configuration.set("Creative.Armor", serialized);

        serialized = InventoryUtils.toYamlSection(entry.getEnderChest());
        configuration.set("EnderChest", serialized);

        // TODO Meta section?

        return configuration;
    }

    private void onSuccess()
    {
        plugin.debug("Saved inventory for {name:" + entry.getName() + "}");

        if(isRunningSync)
        {
            listener.onInventorySaved(entry.getName());
        }
        else
        {
            plugin.getServer().getScheduler().runTask(plugin,
                () -> listener.onInventorySaved(entry.getName()));
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

    private boolean writeFileWithLock(String source, Path path)
    {
        FileLock lock = null;

        try
        {
            File file = path.toFile();
            File directory = file.getParentFile();

            if(!directory.exists() && !directory.mkdirs())
            {
                return false;
            }

            if(!file.exists() && !file.createNewFile())
            {
                return false;
            }

            FileChannel fileChannel = FileChannel.open(path,
                StandardOpenOption.READ, StandardOpenOption.WRITE);

            // Lock the file
            lock = fileChannel.lock();

            ByteBuffer buffer = ByteBuffer.wrap(source.getBytes(StandardCharsets.UTF_16));
            fileChannel.write(buffer);

            // Unlock the file
            lock.release();
            return true;
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
        return false;
    }
}
