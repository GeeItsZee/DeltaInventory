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
import com.yahoo.tracebachi.DeltaInventory.DeltaInventoryPlugin;
import com.yahoo.tracebachi.DeltaInventory.Events.InventoryLoadEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.InventorySaveEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.NoInventoryFoundEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.PrePlayerSaveEvent;
import com.yahoo.tracebachi.DeltaInventory.PotionEffectUtils;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.HashSet;

import static com.yahoo.tracebachi.DeltaRedis.Spigot.Prefixes.*;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class PlayerListener implements Listener
{
    private DeltaInventoryPlugin plugin;
    private DeltaEssentialsPlugin essentialsPlugin;
    private GameMode forcedGameMode = null;

    private HashMap<String, Integer> idMap = new HashMap<>(32);
    private HashSet<String> authenticated = new HashSet<>(32);
    private HashSet<String> locked = new HashSet<>(32);
    private HashSet<String> ignoreGameModeChange = new HashSet<>(32);

    private HashMap<String, ServerChangeRequest> serverChangeRequests = new HashMap<>(32);
    private HashMap<String, InventoryPair> inventoryMap = new HashMap<>(32);

    public PlayerListener(DeltaInventoryPlugin plugin, DeltaEssentialsPlugin essPlugin)
    {
        this.plugin = plugin;
        this.essentialsPlugin = essPlugin;

        if(plugin.getConfig().getBoolean("ForcedGameMode.Enable", false))
        {
            this.forcedGameMode = GameMode.valueOf(plugin.getConfig().getString(
                "ForcedGameMode.GameMode"));
        }
    }

    public void shutdown()
    {
        for(Player player : Bukkit.getOnlinePlayers())
        {
            try
            {
                String name = player.getName().toLowerCase();
                InventoryPair pair = inventoryMap.get(name);
                PlayerEntry entry = createPlayerEntry(player, pair);

                PrePlayerSaveEvent preSaveEvent = new PrePlayerSaveEvent(player);
                Bukkit.getPluginManager().callEvent(preSaveEvent);
                serverChangeRequests.remove(name);
                plugin.saveInventoryNow(entry);
            }
            catch(Exception ex)
            {
                plugin.severe("Failed to save inventory on shutdown for " + player.getName());
                ex.printStackTrace();
            }
        }

        this.plugin = null;
        this.essentialsPlugin = null;
        this.locked.clear();
        this.idMap.clear();
        this.serverChangeRequests.clear();
        this.inventoryMap.clear();
    }

    /**
     * Called when xAuth handles the login command. DeltaInventory will only queue
     * loading the player's inventory when they have been authenticated.
     *
     * @param event Event to process
     */
    @EventHandler
    public void onCommandLoginEvent(xAuthCommandLoginEvent event)
    {
        if(event.getStatus() == xAuthPlayer.Status.AUTHENTICATED)
        {
            Player player = Bukkit.getPlayer(event.getPlayerName());
            if(player != null && player.isOnline())
            {
                // Lock the inventory to prevent changes and add to authenticated
                String name = player.getName().toLowerCase();
                locked.add(name);
                authenticated.add(name);

                // Schedule an inventory load
                Integer id = idMap.get(name);
                plugin.loadInventory(name, id);
            }
        }
    }

    /**
     * Called when xAuth handles a player join event where the player session
     * is still valid. If the player is authenticated, their inventory will be
     * queued for loading.
     *
     * @param event Event to process
     */
    @EventHandler
    public void onPlayerJoinEvent(xAuthPlayerJoinEvent event)
    {
        if(event.getStatus() == xAuthPlayer.Status.AUTHENTICATED)
        {
            Player player = Bukkit.getPlayer(event.getPlayerName());
            if(player != null && player.isOnline())
            {
                // Lock the inventory to prevent changes and add to authenticated
                String name = player.getName().toLowerCase();
                locked.add(name);
                authenticated.add(name);

                // Schedule an inventory load
                Integer id = idMap.get(name);
                plugin.loadInventory(name, id);
            }
        }
    }

    /**
     * Handles cleanup and inventory saving on logout.
     *
     * Because server switching can also be a caused for PlayerQuitEvent, this
     * method checks if the player recently (within 2000ms) made a switch
     * request. If they did, it is assumed the inventory has already been
     * processed and saved.
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if(!authenticated.contains(name)) { return; }

        ServerChangeRequest request = serverChangeRequests.remove(name);
        long currentTime = System.currentTimeMillis();

        // If there is no request or the request "timed out"
        if(request == null || (currentTime - request.requestAt) > 2000)
        {
            // Create an entry
            InventoryPair pair = inventoryMap.get(name);
            PlayerEntry entry = createPlayerEntry(player, pair);

            // Schedule an inventory save
            PrePlayerSaveEvent preSaveEvent = new PrePlayerSaveEvent(player);
            Bukkit.getPluginManager().callEvent(preSaveEvent);
            plugin.saveInventory(entry);
        }

        // Remove the inventory pair
        inventoryMap.remove(name);

        // Remove the lock and remove from authenticated
        locked.remove(name);
        authenticated.remove(name);
    }

    /**
     * Intercepts the intent to switch servers and cancels it.
     *
     * In order to make sure the inventory is saved BEFORE switching servers,
     * DeltaInventory will cancel the original event, queue an inventory save,
     * and complete the request on completion of the save.
     *
     * @param event Event to process
     */
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
        locked.add(name);

        // Schedule an inventory save
        PrePlayerSaveEvent preSaveEvent = new PrePlayerSaveEvent(player);
        Bukkit.getPluginManager().callEvent(preSaveEvent);
        plugin.saveInventory(entry);
    }

    /**
     * Handles the inventory being loaded from the database. It will "reapply"
     * the inventory and player information.
     *
     * @param event Event to process
     */
    @EventHandler
    public void onInventoryLoaded(InventoryLoadEvent event)
    {
        UnmodifiablePlayerEntry entry = event.getEntry();
        Player player = Bukkit.getPlayer(entry.getName());
        String name = entry.getName().toLowerCase();

        // Remove the lock if it exists
        locked.remove(name);

        if(player != null && player.isOnline())
        {
            // Save the ID and remove the lock
            idMap.put(name, entry.getId());
            plugin.debug("Applying inventory for {name:" + name + ", id:" + entry.getId() + "}");

            // Load from entry
            copyFromEntry(player, entry);
        }
    }

    /**
     * Handles the completion of an inventory save.
     *
     * If the player made a request to change servers, it will be completed
     * in this method.
     *
     * @param event Event to process
     */
    @EventHandler
    public void onInventorySaved(InventorySaveEvent event)
    {
        Integer id = event.getId();
        String name = event.getName().toLowerCase();
        Player player = Bukkit.getPlayer(name);
        ServerChangeRequest request = serverChangeRequests.get(name);

        // Save the ID and remove the lock
        idMap.put(name, id);
        locked.remove(name);
        plugin.debug("Saved inventory for {name:" + name + ", id:" + id + "}");

        if(request != null)
        {
            // Send to server without calling event
            request.requestAt = System.currentTimeMillis();
            essentialsPlugin.sendToServer(player, request.destination, false);
        }
    }

    /**
     * Handles the case of a (supposedly) new player in the database. Since there is
     * no inventory to "reapply", it will treat the player's current state as the
     * result in survival mode.
     *
     * @param event Event to process
     */
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

            locked.remove(name.toLowerCase());
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Handles game mode changes.
     *
     * If the player does not have the SingleInv permission, they will have separate
     * inventories for creative and survival.
     *
     * If a game mode is forced and the player does not have the bypass permission,
     * they will not be allowed to change modes.
     *
     * @param event Event to process
     */
    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if(!authenticated.contains(name)) { return; }

        if(locked.contains(name))
        {
            player.sendMessage(FAILURE + "Not allowed to change game modes while " +
                "inventory is being saved or loaded.");
            event.setCancelled(true);
            return;
        }

        if(forcedGameMode != null)
        {
            if(!ignoreGameModeChange.remove(name) &&
                !player.hasPermission("DeltaInv.ForcedGameMode.Bypass"))
            {
                player.sendMessage(FAILURE + "Gamemode is being forced. You are not allowed to change it.");
                event.setCancelled(true);
                return;
            }
        }

        InventoryPair pair = inventoryMap.get(name);
        GameMode originalMode = player.getGameMode();

        if(originalMode != event.getNewGameMode())
        {
            if(!player.hasPermission("DeltaInv.SingleInv"))
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

    /**
     * Handles players opening inventories while waiting for their inventory to
     * load or save. This prevents a player from abusing the time period where
     * they are waiting for their inventory and player information to save (which
     * is usually less than a second).
     *
     * @param event Event to process
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event)
    {
        String name = event.getPlayer().getName().toLowerCase();
        if(locked.contains(name))
        {
            event.getPlayer().sendMessage(INFO +
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
    @EventHandler
    public void onInventoryInteract(InventoryInteractEvent event)
    {
        String name = event.getWhoClicked().getName().toLowerCase();
        if(locked.contains(name))
        {
            event.getWhoClicked().sendMessage(INFO +
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
        entry.setFoodLevel(player.getFoodLevel());
        entry.setXpLevel(player.getLevel());
        entry.setXpProgress(player.getExp());
        entry.setPotionEffects(PotionEffectUtils.serialize(player.getActivePotionEffects()));
        entry.setArmor(player.getInventory().getArmorContents());
        entry.setEnderInventory(player.getEnderChest().getContents());

        if(player.hasPermission("DeltaInv.SingleInv"))
        {
            // Single inventory players will get items saved in survival every time
            entry.setGameMode(player.getGameMode());
            entry.setSurvivalInventory(player.getInventory().getContents());
            entry.setCreativeInventory(null);
        }
        else
        {
            // Save survival and creative according to current game mode
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
        }
        return entry;
    }

    private void copyFromEntry(Player player, UnmodifiablePlayerEntry entry)
    {
        InventoryPair pair = new InventoryPair();
        String name = player.getName().toLowerCase();

        player.setHealth(entry.getHealth());
        player.setFoodLevel(entry.getFoodLevel());
        player.setLevel(entry.getXpLevel());
        player.setExp(entry.getXpProgress());

        // Apply the potion effects to the player
        for(PotionEffect effect : PotionEffectUtils.deserialize(entry.getPotionEffects()))
        {
            effect.apply(player);
        }

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

        if(player.hasPermission("DeltaInv.SingleInv"))
        {
            // Single inventory players will get items loaded from survival every time
            player.getInventory().setContents(pair.getSurvival());
            pair.setSurvival(null);
            pair.setCreative(null);
        }
        else
        {
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
        }

        // If game mode is not forced
        if(forcedGameMode == null)
        {
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
        else
        {
            if(player.getGameMode() != forcedGameMode)
            {
                ignoreGameModeChange.add(name);
                player.setGameMode(forcedGameMode);
            }
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
