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

import com.yahoo.tracebachi.DeltaEssentials.DeltaEssentialsPlugin;
import com.yahoo.tracebachi.DeltaEssentials.Events.PlayerServerSwitchEvent;
import com.yahoo.tracebachi.DeltaEssentials.Prefixes;
import com.yahoo.tracebachi.DeltaInventory.DeltaInventoryPlugin;
import com.yahoo.tracebachi.DeltaInventory.Events.InventoryLoadEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.InventorySaveEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.NoInventoryFoundEvent;
import com.yahoo.tracebachi.DeltaInventory.Storage.InventoryPair;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.UnmodifiablePlayerEntry;
import de.luricos.bukkit.xAuth.event.command.player.xAuthCommandLoginEvent;
import de.luricos.bukkit.xAuth.event.player.xAuthPlayerJoinEvent;
import de.luricos.bukkit.xAuth.xAuthPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerListener implements Listener
{
    private DeltaInventoryPlugin plugin;
    private DeltaEssentialsPlugin essentialsPlugin;

    private HashSet<String> lockedPlayers = new HashSet<>(32);
    private HashMap<String, Integer> idMap = new HashMap<>(32);

    private HashMap<String, ServerChangeRequest> serverChangeRequests = new HashMap<>(32);
    private HashMap<String, InventoryPair> inventoryMap = new HashMap<>(32);

    public PlayerListener(DeltaInventoryPlugin plugin, DeltaEssentialsPlugin essPlugin)
    {
        this.plugin = plugin;
        this.essentialsPlugin = essPlugin;
    }

    public void shutdown()
    {
        for(Player player : Bukkit.getOnlinePlayers())
        {
            String name = player.getName().toLowerCase();
            InventoryPair pair = inventoryMap.get(name);
            PlayerEntry entry = createPlayerEntry(player, pair);

            serverChangeRequests.remove(name);

            plugin.saveInventoryNow(entry);
        }

        this.plugin = null;
        this.essentialsPlugin = null;
        this.lockedPlayers.clear();
        this.idMap.clear();
        this.serverChangeRequests.clear();
        this.inventoryMap.clear();
    }

    @EventHandler
    public void onCommandLoginEvent(xAuthCommandLoginEvent event)
    {
        if(event.getStatus() == xAuthPlayer.Status.AUTHENTICATED)
        {
            Player player = Bukkit.getPlayer(event.getPlayerName());
            if(player != null && player.isOnline())
            {
                String name = player.getName().toLowerCase();

                // Lock the inventory to prevent changes
                lockedPlayers.add(name);

                // Schedule an inventory load
                Integer id = idMap.get(name);
                plugin.loadInventory(name, id);
            }
        }
    }

    @EventHandler
    public void onPlayerJoinEvent(xAuthPlayerJoinEvent event)
    {
        if(event.getStatus() == xAuthPlayer.Status.AUTHENTICATED)
        {
            Player player = Bukkit.getPlayer(event.getPlayerName());
            if(player != null && player.isOnline())
            {
                String name = player.getName().toLowerCase();

                // Lock the inventory to prevent changes
                lockedPlayers.add(name);

                // Schedule an inventory load
                Integer id = idMap.get(name);
                plugin.loadInventory(name, id);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        ServerChangeRequest request = serverChangeRequests.remove(name);
        long currentTime = System.currentTimeMillis();

        // If there is no request or the request "timed out"
        if(request == null || (currentTime - request.requestAt) > 2000)
        {
            // Create an entry
            InventoryPair pair = inventoryMap.get(name);
            PlayerEntry entry = createPlayerEntry(player, pair);

            // Schedule an inventory save
            plugin.saveInventory(entry);
        }

        // Remove the inventory pair
        inventoryMap.remove(name);

        // Remove the lock if it exists
        lockedPlayers.remove(name);
    }

    @EventHandler
    public void onPlayerServerSwitch(PlayerServerSwitchEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        InventoryPair pair = inventoryMap.get(name);
        PlayerEntry entry = createPlayerEntry(player, pair);

        // Cancel event until inventory is saved
        event.setCancelled(true);
        plugin.debug("Cancelled server change event for {name:" + name + "}");

        // Add request for when the save is complete
        serverChangeRequests.put(name, new ServerChangeRequest(
            event.getDestinationServer(), System.currentTimeMillis()));

        // Lock the inventory to prevent changes
        lockedPlayers.add(name);

        // Schedule an inventory save
        plugin.saveInventory(entry);
    }

    @EventHandler
    public void onInventoryLoaded(InventoryLoadEvent event)
    {
        UnmodifiablePlayerEntry entry = event.getEntry();
        Player player = Bukkit.getPlayer(entry.getName());
        String name = entry.getName().toLowerCase();

        if(player != null && player.isOnline())
        {
            // Save the ID and remove the lock
            idMap.put(name, entry.getId());
            plugin.debug("Loaded inventory for {name:" + name + ", id:" + entry.getId() + "}");

            // Load from entry
            copyFromEntry(player, entry);
        }

        // Remove the lock if it exists
        lockedPlayers.remove(name);
    }

    @EventHandler
    public void onInventorySaved(InventorySaveEvent event)
    {
        Integer id = event.getId();
        String name = event.getName().toLowerCase();
        Player player = Bukkit.getPlayer(name);
        ServerChangeRequest request = serverChangeRequests.get(name);

        // Save the ID and remove the lock
        idMap.put(name, id);
        lockedPlayers.remove(name);
        plugin.debug("Saved inventory for {name:" + name + ", id:" + id + "}");

        if(request != null)
        {
            // Send to server without calling event
            request.requestAt = System.currentTimeMillis();
            essentialsPlugin.sendToServer(player, request.destination, false);
        }
    }

    @EventHandler
    public void onNoInventoryFound(NoInventoryFoundEvent event)
    {
        String name = event.getName();
        InventoryPair pair = new InventoryPair();
        Player player = Bukkit.getPlayer(name);

        plugin.debug("No inventory found or loaded inventory for {name:" + name + "}");

        if(player != null && player.isOnline())
        {
            pair.setSurvival(player.getInventory().getContents());
            inventoryMap.put(name, new InventoryPair());

            lockedPlayers.remove(name.toLowerCase());
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        InventoryPair pair = inventoryMap.get(name);
        GameMode originalMode = player.getGameMode();

        if(!player.hasPermission("DeltaInv.SingleInv"))
        {
            if(originalMode != event.getNewGameMode())
            {
                if(originalMode == GameMode.SURVIVAL)
                {
                    pair.setSurvival(player.getInventory().getContents());
                }
                else if(originalMode == GameMode.CREATIVE)
                {
                    pair.setCreative(player.getInventory().getContents());
                }

                if(event.getNewGameMode() == GameMode.SURVIVAL)
                {
                    player.getInventory().setContents(pair.getSurvival());
                    pair.setSurvival(null);
                }
                else if(event.getNewGameMode() == GameMode.CREATIVE)
                {
                    player.getInventory().setContents(pair.getCreative());
                    pair.setCreative(null);
                }
                else
                {
                    // Clear the inventory if new game mode is Adventure or Spectator
                    player.getInventory().clear();
                }
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event)
    {
        String name = event.getPlayer().getName().toLowerCase();
        if(lockedPlayers.contains(name))
        {
            event.getPlayer().sendMessage(Prefixes.INFO +
                "Your inventory is locked. Wait until it is loaded.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryInteract(InventoryInteractEvent event)
    {
        String name = event.getWhoClicked().getName().toLowerCase();
        if(lockedPlayers.contains(name))
        {
            event.getWhoClicked().sendMessage(Prefixes.INFO +
                "Your inventory is locked. Wait until it is loaded.");
            event.setCancelled(true);
        }
    }

    private PlayerEntry createPlayerEntry(Player player, InventoryPair pair)
    {
        PlayerEntry entry = new PlayerEntry();

        entry.setId(idMap.get(player.getName().toLowerCase()));
        entry.setName(player.getName());
        entry.setHealth(player.getHealth());
        entry.setXpLevel(player.getLevel());
        entry.setXpProgress(player.getExp());

        switch(player.getGameMode())
        {
            case SURVIVAL:
                entry.setGameMode(PlayerEntry.SURVIVAL);
                entry.setSurvivalInventory(player.getInventory().getContents());
                entry.setCreativeInventory(pair.getCreative());
                break;
            case CREATIVE:
                entry.setGameMode(PlayerEntry.CREATIVE);
                entry.setCreativeInventory(player.getInventory().getContents());
                entry.setSurvivalInventory(pair.getSurvival());
                break;
            case ADVENTURE:
                entry.setGameMode(PlayerEntry.ADVENTURE);
                entry.setSurvivalInventory(pair.getSurvival());
                entry.setCreativeInventory(pair.getCreative());
                break;
            case SPECTATOR:
                entry.setGameMode(PlayerEntry.SPECTATOR);
                entry.setSurvivalInventory(pair.getSurvival());
                entry.setCreativeInventory(pair.getCreative());
                break;
        }

        entry.setArmor(player.getInventory().getArmorContents());
        entry.setEnderInventory(player.getEnderChest().getContents());

        return entry;
    }

    private void copyFromEntry(Player player, UnmodifiablePlayerEntry entry)
    {
        InventoryPair pair = new InventoryPair();
        String name = player.getName().toLowerCase();

        player.setHealth(entry.getHealth());
        player.setLevel(entry.getXpLevel());
        player.setExp(entry.getXpProgress());

        if(entry.getArmor() != null)
        {
            player.getInventory().setArmorContents(entry.getArmor());
        }
        if(entry.getEnderInventory() != null)
        {
            player.getEnderChest().setContents(entry.getEnderInventory());
        }

        pair.setSurvival(entry.getSurvivalInventory());
        pair.setCreative(entry.getCreativeInventory());
        inventoryMap.put(name, pair);

        // Load the same inventory as the player's current game mode
        switch(player.getGameMode())
        {
            case SURVIVAL:
                player.getInventory().setContents(pair.getSurvival());
                pair.setSurvival(null);
                break;
            case CREATIVE:
                player.getInventory().setContents(pair.getCreative());
                pair.setCreative(null);
                break;
            default:
                player.getInventory().clear();
                break;
        }

        // Switch the server to game mode they should be in
        switch(entry.getGameMode())
        {
            case PlayerEntry.SURVIVAL:
                if(player.getGameMode() != GameMode.SURVIVAL)
                {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                break;
            case PlayerEntry.CREATIVE:
                if(player.getGameMode() != GameMode.CREATIVE)
                {
                    player.setGameMode(GameMode.CREATIVE);
                }
                break;
            case PlayerEntry.ADVENTURE:
                if(player.getGameMode() != GameMode.ADVENTURE)
                {
                    player.setGameMode(GameMode.ADVENTURE);
                }
                break;
            case PlayerEntry.SPECTATOR:
                if(player.getGameMode() != GameMode.SPECTATOR)
                {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                break;
        }
    }

    private class ServerChangeRequest
    {
        private final String destination;
        private long requestAt;

        public ServerChangeRequest(String destination, long requestAt)
        {
            this.destination = destination;
            this.requestAt = requestAt;
        }
    }
}
