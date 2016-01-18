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

import com.yahoo.tracebachi.DeltaEssentials.DeltaEssentialsPlugin;
import com.yahoo.tracebachi.DeltaInventory.Listeners.InventoryLockListener;
import com.yahoo.tracebachi.DeltaInventory.Listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/11/15.
 */
public class DeltaInventoryPlugin extends JavaPlugin
{
    private boolean debugMode;
    private String playerDataFolder;
    private PlayerListener playerListener;
    private InventoryLockListener inventoryLockListener;

    @Override
    public void onLoad()
    {
        saveDefaultConfig();
    }

    @Override
    public void onEnable()
    {
        reloadConfig();
        debugMode = getConfig().getBoolean("DebugMode", false);
        playerDataFolder = getConfig().getString("PlayerDataFolder");

        if(playerDataFolder == null || playerDataFolder.trim().equals(""))
        {
            playerDataFolder = getDataFolder() + File.separator +
                "PlayerData" + File.separator;
        }

        File file = new File(playerDataFolder);
        if(!file.exists() && !file.mkdirs())
        {
            severe("Failed to create neccesary directories! Shutting down ...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DeltaEssentialsPlugin dePlugin = (DeltaEssentialsPlugin) getServer()
            .getPluginManager().getPlugin("DeltaEssentials");

        inventoryLockListener = new InventoryLockListener();
        getServer().getPluginManager().registerEvents(inventoryLockListener, this);
        playerListener = new PlayerListener(dePlugin, inventoryLockListener, this);
        getServer().getPluginManager().registerEvents(playerListener, this);
    }

    @Override
    public void onDisable()
    {
        if(playerListener != null)
        {
            playerListener.shutdown();
            playerListener = null;
        }

        if(inventoryLockListener != null)
        {
            inventoryLockListener.shutdown();
            inventoryLockListener = null;
        }
    }

    public String getPlayerDataFolder()
    {
        return playerDataFolder;
    }

    public void info(String message)
    {
        getLogger().info(message);
    }

    public void severe(String message)
    {
        getLogger().severe(message);
    }

    public void debug(String message)
    {
        if(debugMode)
        {
            getLogger().info("[Debug] " + message);
        }
    }
}
