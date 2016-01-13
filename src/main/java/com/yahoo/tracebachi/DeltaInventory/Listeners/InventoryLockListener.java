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
package com.yahoo.tracebachi.DeltaInventory.Listeners;

import com.yahoo.tracebachi.DeltaRedis.Spigot.Prefixes;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class InventoryLockListener implements Listener
{
    private HashSet<String> locked = new HashSet<>(32);

    public void shutdown()
    {
        locked.clear();
        locked = null;
    }

    public boolean addLock(String name)
    {
        return locked.add(name.toLowerCase());
    }

    public boolean removeLock(String name)
    {
        return locked.remove(name.toLowerCase());
    }

    public boolean isLocked(String name)
    {
        return locked.contains(name.toLowerCase());
    }

    /**
     * Handles players opening inventories while waiting for their inventory to
     * load or save. This prevents a player from abusing the time period where
     * they are waiting for their inventory and player information to save (which
     * is usually less than a second).
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event)
    {
        String name = event.getPlayer().getName().toLowerCase();
        if(locked.contains(name))
        {
            event.getPlayer().sendMessage(Prefixes.INFO +
                "Your inventory is locked. Wait until it is loaded.");
            event.setCancelled(true);
        }
    }

    /**
     * Handles players interacting with inventories while waiting for their
     * inventory to load or save. This prevents a player from abusing the time
     * period where they are waiting for their inventory and player information
     * to save (which is usually less than a second).
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryInteract(InventoryInteractEvent event)
    {
        String name = event.getWhoClicked().getName().toLowerCase();
        if(locked.contains(name))
        {
            event.getWhoClicked().sendMessage(Prefixes.INFO +
                "Your inventory is locked. Wait until it is loaded.");
            event.setCancelled(true);
        }
    }

    /**
     * Handles players dropping an item from their inventories. This prevents
     * a player from abusing the time period where they are waiting for their
     * inventory and player information to save (which is usually less than
     * a second).
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDropItemEvent(PlayerDropItemEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if(locked.contains(name))
        {
            player.sendMessage(Prefixes.INFO + "Your inventory is locked. Wait until it is loaded.");
            event.setCancelled(true);
        }
    }

    /**
     * Handles players picking up an item into their inventories. This prevents
     * a player from abusing the time period where they are waiting for their
     * inventory and player information to save (which is usually less than
     * a second).
     *
     * @param event Event to process
     */
    @EventHandler
    public void onPlayerPickupItemEvent(PlayerPickupItemEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if(locked.contains(name))
        {
            player.sendMessage(Prefixes.INFO + "Your inventory is locked. Wait until it is loaded.");
            event.setCancelled(true);
        }
    }

    public void onPlayerQuitEvent(PlayerQuitEvent event)
    {
        locked.remove(event.getPlayer().getName().toLowerCase());
    }
}
