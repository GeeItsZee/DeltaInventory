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
import com.yahoo.tracebachi.DeltaEssentials.Events.PlayerServerSwitchEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.PlayerLoadedEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.PlayerPreSaveEvent;
import com.yahoo.tracebachi.DeltaInventory.Events.PlayerSavedEvent;
import com.yahoo.tracebachi.DeltaInventory.Storage.InventoryPair;
import com.yahoo.tracebachi.DeltaInventory.Storage.ModifiablePlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.yahoo.tracebachi.DeltaInventory.Storage.PlayerInventory;
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
import org.bukkit.inventory.ItemStack;
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
                // Allow others plugins to modify inventory and players before saving
                PlayerPreSaveEvent preSaveEvent = new PlayerPreSaveEvent(player);
                Bukkit.getPluginManager().callEvent(preSaveEvent);

                // Create an entry
                String name = player.getName().toLowerCase();
                InventoryPair pair = inventoryMap.get(name);
                PlayerEntry entry = createPlayerEntry(player, pair);

                // Save inventory now
                serverChangeRequests.remove(name);
                plugin.saveInventoryForShutdown(entry);
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
     * Handles the inventory being loaded from the database. It will "reapply"
     * the inventory and player information.
     *
     * @param entry Entry to load
     */
    public void onInventoryLoaded(PlayerEntry entry)
    {
        Player player = Bukkit.getPlayer(entry.getName());
        String name = entry.getName();
        PlayerLoadedEvent event = new PlayerLoadedEvent(name, player);

        plugin.debug("Loaded inventory for {name:" + entry.getName() + ", id:" + entry.getId() + "}");
        Bukkit.getPluginManager().callEvent(event);

        // Save the ID and remove the lock
        idMap.put(name, entry.getId());
        locked.remove(name);

        // If the player is online, apply the entry
        if(player != null && player.isOnline())
        {
            copyFromEntry(player, entry);
        }
    }

    /**
     * Handles the completion of an inventory save.
     *
     * If the player made a request to change servers, it will be completed
     * in this method.
     *
     * @param name Name of the player whose inventory was saved
     * @param id Integer id of the database row where the inventory is saved
     */
    public void onInventorySaved(String name, Integer id)
    {
        Player player = Bukkit.getPlayer(name);
        ServerChangeRequest request = serverChangeRequests.get(name);
        PlayerSavedEvent event = new PlayerSavedEvent(name, player);

        plugin.debug("Saved inventory for {name:" + name + ", id:" + id + "}");
        Bukkit.getPluginManager().callEvent(event);

        // Save the ID and remove the lock
        idMap.put(name, id);
        locked.remove(name);

        // If there is a server change request, send to server without DeltaEssentials event
        if(player != null && player.isOnline() && request != null)
        {
            request.requestAt = System.currentTimeMillis();
            essentialsPlugin.sendToServer(player, request.destination, false);
        }
    }

    /**
     * Handles the case of a (supposedly) new player in the database. Since there is
     * no inventory to "reapply", it will treat the player's current state as the
     * result in survival mode.
     *
     * @param name Name of the player whose inventory was not found
     */
    public void onInventoryNotFound(String name)
    {
        InventoryPair pair = new InventoryPair();
        Player player = Bukkit.getPlayer(name);

        // Remove the lock
        locked.remove(name);

        // If the player is still online
        if(player != null && player.isOnline())
        {
            PlayerLoadedEvent event = new PlayerLoadedEvent(name, player);
            inventoryMap.put(name, pair);

            if(player.getGameMode() != GameMode.SURVIVAL)
            {
                if(forcedGameMode != null)
                {
                    ignoreGameModeChange.add(name);
                }

                player.setGameMode(GameMode.SURVIVAL);
            }

            plugin.debug("No inventory found or loaded inventory for {name:" + name + "}");
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    /**
     * Handles the case of where a player's inventory could not be saved.
     *
     * @param name Name of the player whose inventory was not saved
     */
    public void onInventorySaveFailure(String name, String message)
    {
        plugin.debug("Failed to save inventory for {name:" + name + "}");

        // Remove the lock
        locked.remove(name);

        // Send a message to the player
        Player player = Bukkit.getPlayer(name);
        if(player != null && player.isOnline())
        {
            player.sendMessage(FAILURE + message);
        }
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
     * Because server switching can also be a cause for PlayerQuitEvent, this
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

        // Ignore if player was never authenticated
        if(!authenticated.contains(name)) { return; }

        ServerChangeRequest request = serverChangeRequests.remove(name);
        long currentTime = System.currentTimeMillis();

        // If there is no request or the request "timed out"
        if(request == null || (currentTime - request.requestAt) > 2000)
        {
            // Allow others plugins to modify inventory and players before saving
            PlayerPreSaveEvent preSaveEvent = new PlayerPreSaveEvent(player);
            Bukkit.getPluginManager().callEvent(preSaveEvent);

            // Create an entry
            InventoryPair pair = inventoryMap.get(name);
            PlayerEntry entry = createPlayerEntry(player, pair);

            // Schedule an inventory save
            plugin.saveInventory(entry);
        }

        // Remove the inventory pair, locked, and authenticated
        inventoryMap.remove(name);
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

        // Cancel event until inventory is saved
        event.setCancelled(true);
        plugin.debug("Cancelled server change event for {name:" + name + "}");

        // Add request for when the save is complete
        serverChangeRequests.put(name, new ServerChangeRequest(
            event.getDestinationServer(), System.currentTimeMillis()));

        // Allow others plugins to modify inventory and players before saving
        PlayerPreSaveEvent preSaveEvent = new PlayerPreSaveEvent(player);
        Bukkit.getPluginManager().callEvent(preSaveEvent);

        // Create an entry
        InventoryPair pair = inventoryMap.get(name);
        PlayerEntry entry = createPlayerEntry(player, pair);

        // Lock the inventory to prevent changes by the player
        locked.add(name);

        // Schedule an inventory save
        plugin.saveInventory(entry);
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
        GameMode originalMode = player.getGameMode();

        // Ignore game mode changes where the game modes are the same
        if(originalMode == event.getNewGameMode()) { return; }

        // Ignore players that have the single inventory permission
        if(player.hasPermission("DeltaInv.SingleInv")) { return; }

        // Ignore if player was never authenticated
        if(!authenticated.contains(name)) { return; }

        // Prevent game mode change during saves
        if(locked.contains(name))
        {
            player.sendMessage(FAILURE + "Not allowed to change game modes while " +
                "inventory is being saved or loaded.");
            event.setCancelled(true);
            return;
        }

        // Prevent game mode changes unless there is no forced game mode or player is exempt
        if(forcedGameMode != null && !ignoreGameModeChange.remove(name) &&
            !player.hasPermission("DeltaInv.ForcedGameMode.Bypass"))
        {
            player.sendMessage(FAILURE + "Game mode is being forced. You are not allowed to change it.");
            event.setCancelled(true);
            return;
        }

        InventoryPair pair = inventoryMap.get(name);
        PlayerInventory inventory = new PlayerInventory(
            player.getInventory().getArmorContents(),
            player.getInventory().getContents());

        // Save the old
        if(originalMode == GameMode.SURVIVAL)
        {
            pair.setSurvival(inventory);
        }
        else if(originalMode == GameMode.CREATIVE)
        {
            pair.setCreative(inventory);
        }

        // Apply the new
        if(event.getNewGameMode() == GameMode.SURVIVAL)
        {
            player.getInventory().setContents(pair.getSurvival().getContents());
            player.getInventory().setArmorContents(pair.getSurvival().getArmor());

            // Clear the reference to the saved inventory
            pair.setSurvival(null);
        }
        else if(event.getNewGameMode() == GameMode.CREATIVE)
        {
            player.getInventory().setContents(pair.getCreative().getContents());
            player.getInventory().setArmorContents(pair.getCreative().getArmor());

            // Clear the reference to the saved inventory
            pair.setCreative(null);
        }
        else
        {
            // Clear the inventory if new game mode is Adventure or Spectator
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
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
        ModifiablePlayerEntry entry = new ModifiablePlayerEntry();
        PlayerInventory inventory = new PlayerInventory(
            player.getInventory().getArmorContents(),
            player.getInventory().getContents());

        entry.setId(idMap.get(player.getName().toLowerCase()));
        entry.setName(player.getName());
        entry.setHealth(player.getHealth());
        entry.setFoodLevel(player.getFoodLevel());
        entry.setXpLevel(player.getLevel());
        entry.setXpProgress(player.getExp());
        entry.setPotionEffects(PotionEffectUtils.serialize(player.getActivePotionEffects()));
        entry.setEnderInventory(player.getEnderChest().getContents());

        if(player.hasPermission("DeltaInv.SingleInv"))
        {
            entry.setGameMode(player.getGameMode());
            entry.setSurvivalInventory(inventory);
            entry.setCreativeInventory(null);
        }
        else
        {
            switch(player.getGameMode())
            {
                case SURVIVAL:
                    entry.setGameMode(GameMode.SURVIVAL);
                    entry.setSurvivalInventory(inventory);
                    entry.setCreativeInventory(pair.getCreative());
                    break;
                case CREATIVE:
                    entry.setGameMode(GameMode.CREATIVE);
                    entry.setCreativeInventory(inventory);
                    entry.setSurvivalInventory(pair.getSurvival());
                    break;
                case ADVENTURE:
                    entry.setGameMode(GameMode.ADVENTURE);
                    entry.setSurvivalInventory(pair.getSurvival());
                    entry.setCreativeInventory(pair.getCreative());
                    break;
                case SPECTATOR:
                    entry.setGameMode(GameMode.SPECTATOR);
                    entry.setSurvivalInventory(pair.getSurvival());
                    entry.setCreativeInventory(pair.getCreative());
                    break;
            }
        }
        return entry;
    }

    private void copyFromEntry(Player player, PlayerEntry entry)
    {
        InventoryPair pair = new InventoryPair();
        String name = player.getName().toLowerCase();

        player.setHealth(entry.getHealth());
        player.setFoodLevel(entry.getFoodLevel());
        player.setLevel(entry.getXpLevel());
        player.setExp(entry.getXpProgress());

        for(PotionEffect effect : PotionEffectUtils.deserialize(entry.getPotionEffects()))
        {
            effect.apply(player);
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
            player.getInventory().setContents(pair.getSurvival().getContents());
            player.getInventory().setArmorContents(pair.getSurvival().getArmor());
            pair.setSurvival(null);
            pair.setCreative(null);
        }
        else
        {
            // Load the same inventory as the player's current game mode
            switch(player.getGameMode())
            {
                case SURVIVAL:
                    player.getInventory().setContents(pair.getSurvival().getContents());
                    player.getInventory().setArmorContents(pair.getSurvival().getArmor());
                    pair.setSurvival(null);
                    break;
                case CREATIVE:
                    player.getInventory().setContents(pair.getCreative().getContents());
                    player.getInventory().setArmorContents(pair.getCreative().getArmor());
                    pair.setCreative(null);
                    break;
                default:
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(new ItemStack[4]);
                    break;
            }
        }

        // If game mode is not forced
        if(forcedGameMode == null || player.hasPermission("DeltaInv.ForcedGameMode.Bypass"))
        {
            // Switch the server to game mode they should be in
            switch(entry.getGameMode())
            {
                case SURVIVAL:
                    if(player.getGameMode() != GameMode.SURVIVAL)
                    {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                    break;
                case CREATIVE:
                    if(player.getGameMode() != GameMode.CREATIVE)
                    {
                        player.setGameMode(GameMode.CREATIVE);
                    }
                    break;
                case ADVENTURE:
                    if(player.getGameMode() != GameMode.ADVENTURE)
                    {
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                    break;
                case SPECTATOR:
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
