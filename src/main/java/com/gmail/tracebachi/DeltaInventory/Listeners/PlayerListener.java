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
package com.gmail.tracebachi.DeltaInventory.Listeners;

import com.gmail.tracebachi.DeltaInventory.Events.PlayerPreSaveEvent;
import com.gmail.tracebachi.DeltaInventory.Runnables.PlayerLoad;
import com.gmail.tracebachi.DeltaInventory.Runnables.PlayerSave;
import com.gmail.tracebachi.DeltaInventory.Storage.IPlayerEntry;
import com.gmail.tracebachi.DeltaInventory.Storage.InventoryPair;
import com.gmail.tracebachi.DeltaInventory.Storage.PlayerEntry;
import com.gmail.tracebachi.DeltaInventory.Storage.SavedInventory;
import com.yahoo.tracebachi.DeltaEssentials.DeltaEssentialsPlugin;
import com.yahoo.tracebachi.DeltaEssentials.Events.PlayerServerSwitchEvent;
import com.gmail.tracebachi.DeltaInventory.DeltaInventoryPlugin;
import com.gmail.tracebachi.DeltaInventory.Events.PlayerLoadedEvent;
import com.gmail.tracebachi.DeltaInventory.Events.PlayerSavedEvent;
import de.luricos.bukkit.xAuth.event.command.player.xAuthCommandLoginEvent;
import de.luricos.bukkit.xAuth.event.command.player.xAuthCommandRegisterEvent;
import de.luricos.bukkit.xAuth.event.player.xAuthPlayerJoinEvent;
import de.luricos.bukkit.xAuth.event.xAuthEvent;
import de.luricos.bukkit.xAuth.event.xAuthEventProperties;
import de.luricos.bukkit.xAuth.xAuthPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;

import static com.yahoo.tracebachi.DeltaRedis.Spigot.Prefixes.FAILURE;
import static com.yahoo.tracebachi.DeltaRedis.Spigot.Prefixes.input;

/**
 * Created by Trace Bachi (tracebachi@gmail.com, BigBossZee) on 12/12/15.
 */
public class PlayerListener implements Listener
{
    /**
     * xAuth 2.6.0 has an error in the source where the player name property is
     * not accessible through any of the provided methods. Through reflection,
     * the xAuthEventProperties of the xAuthCommandRegisterEvent will be read
     * for the player name.
     *
     */
    private static Field propertiesField;
    static
    {
        try
        {
            propertiesField = xAuthEvent.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
        }
        catch(NoSuchFieldException e)
        {
            e.printStackTrace();
            propertiesField = null;
        }
    }

    private DeltaInventoryPlugin plugin;
    private DeltaEssentialsPlugin deltaEssPlugin;
    private InventoryLockListener inventoryLockListener;

    private boolean clearEffectsOnLogin = false;
    private HashSet<GameMode> disabledGameModes = new HashSet<>();
    private GameMode forcedGameMode = null;

    private HashSet<String> authenticated = new HashSet<>(32);
    private HashSet<String> ignoreModeChangeOnce = new HashSet<>(32);
    private HashMap<String, ServerChangeRequest> serverChangeRequests = new HashMap<>(32);
    private HashMap<String, InventoryPair> inventoryMap = new HashMap<>(32);

    public PlayerListener(DeltaEssentialsPlugin essPlugin, InventoryLockListener inventoryLockListener,
        DeltaInventoryPlugin plugin)
    {
        this.deltaEssPlugin = essPlugin;
        this.inventoryLockListener = inventoryLockListener;
        this.plugin = plugin;
        this.clearEffectsOnLogin = plugin.getConfig().getBoolean("ClearEffectsOnLogin", false);

        for(String modeName : plugin.getConfig().getStringList("DisabledGameModes"))
        {
            try
            {
                GameMode gameMode = GameMode.valueOf(modeName.toUpperCase());
                disabledGameModes.add(gameMode);
                plugin.getLogger().info("Disabling GameMode: " + gameMode.name());
            }
            catch(IllegalArgumentException ex)
            {
                plugin.getLogger().info("Unknown GameMode: " + modeName);
            }
        }

        switch(plugin.getConfig().getString("ForcedGameMode").toLowerCase())
        {
            case "survival":
                this.forcedGameMode = GameMode.SURVIVAL;
                break;
            case "creative":
                this.forcedGameMode = GameMode.CREATIVE;
                break;
            case "adventure":
                this.forcedGameMode = GameMode.ADVENTURE;
                break;
            case "spectator":
                this.forcedGameMode = GameMode.SPECTATOR;
                break;
            default:
                this.forcedGameMode = null;
                break;
        }
    }

    public void shutdown()
    {
        for(Player player : Bukkit.getOnlinePlayers())
        {
            try
            {
                String name = player.getName().toLowerCase();
                serverChangeRequests.remove(name);
                saveInventorySync(player);
            }
            catch(Exception ex)
            {
                plugin.severe("Failed to save inventory on shutdown for " + player.getName());
                ex.printStackTrace();
            }
        }

        this.inventoryMap.clear();
        this.inventoryMap = null;
        this.serverChangeRequests.clear();
        this.serverChangeRequests = null;
        this.deltaEssPlugin = null;
        this.plugin = null;
    }

    /**
     * Handles the inventory being loaded from the database. It will "reapply"
     * the inventory and player information.
     *
     * @param entry Entry to load
     */
    public void onInventoryLoaded(IPlayerEntry entry)
    {
        Player player = Bukkit.getPlayer(entry.getName());
        String name = entry.getName();

        // Remove the lock
        inventoryLockListener.removeLock(name);

        // If the player is online, apply the entry
        if(player != null && player.isOnline())
        {
            loadFromEntry(player, entry);

            PlayerLoadedEvent event = new PlayerLoadedEvent(name, player);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    /**
     * Handles the case of a new player in the database. Since there is
     * no inventory to "reapply", it will begin saving from the player's
     * current state.
     *
     * @param name Name of the player whose inventory was not found
     */
    public void onInventoryNotFound(String name)
    {
        Player player = Bukkit.getPlayer(name);

        // Remove the lock and put an empty inventory pair by default
        inventoryLockListener.removeLock(name);
        inventoryMap.put(name, new InventoryPair());

        // If the player is still online
        if(player != null && player.isOnline())
        {
            if(player.getGameMode() != GameMode.SURVIVAL)
            {
                ignoreModeChangeOnce.add(name);
                player.setGameMode(GameMode.SURVIVAL);
            }

            PlayerLoadedEvent event = new PlayerLoadedEvent(name, player);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    /**
     * Handles the case of an inventory failing to load. It will not unlock
     * the player's inventory and will result in additional errors on exit.
     * These errors are intentional and should be used to inform the developer
     * of the issue.
     *
     * @param name Name of the player whose inventory was not found
     */
    public void onInventoryLoadFailure(String name)
    {
        Player player = Bukkit.getPlayer(name);

        // If the player is still online
        if(player != null && player.isOnline())
        {
            player.sendMessage(FAILURE + "Failed to load inventory. " +
                "Report this to the developer! " +
                "You may have future inventory issues if this is not fixed.");
        }
    }

    /**
     * Handles the completion of an inventory save. If the player made a request
     * to change servers, it will be completed in this method.
     *
     * @param name Name of the player whose inventory was saved
     */
    public void onInventorySaved(String name)
    {
        Player player = Bukkit.getPlayer(name);
        ServerChangeRequest request = serverChangeRequests.remove(name);

        PlayerSavedEvent event = new PlayerSavedEvent(name, player);
        Bukkit.getPluginManager().callEvent(event);

        // Remove the lock
        inventoryLockListener.removeLock(name);

        // If there is a server change request, send to server without DeltaEssentials event
        if(player != null && player.isOnline() && request != null)
        {
            // Reinsert the request to handle in the quit event
            ServerChangeRequest newRequest = new ServerChangeRequest(
                request.destination, System.currentTimeMillis());
            serverChangeRequests.put(name, newRequest);

            // Readd the lock to prevent changes until switch is finalized
            inventoryLockListener.addLock(name);

            // Send to server without PlayerServerSwitchEvent
            deltaEssPlugin.sendToServer(player, request.destination, false);
        }
    }

    /**
     * Handles the case of where a player's inventory could not be saved.
     *
     * @param name Name of the player whose inventory was not saved
     */
    public void onInventorySaveFailure(String name)
    {
        // Remove the lock
        inventoryLockListener.removeLock(name);

        // Send a message to the player
        Player player = Bukkit.getPlayer(name);
        if(player != null && player.isOnline())
        {
            player.sendMessage(FAILURE + "Failed to save inventory! " +
                "Do you have any invalid items in your inventory?");
        }
    }

    /**
     * Called when xAuth handles the login command. DeltaInventory will only queue
     * loading the player's inventory when they have been authenticated.
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommandLoginEvent(xAuthCommandLoginEvent event)
    {
        if(event.getStatus() == xAuthPlayer.Status.AUTHENTICATED)
        {
            Player player = Bukkit.getPlayer(event.getPlayerName());
            if(player != null && player.isOnline())
            {
                // Lock the inventory to prevent changes and add to authenticated
                String name = player.getName().toLowerCase();
                inventoryLockListener.addLock(name);
                authenticated.add(name);

                // Schedule an inventory load
                loadInventoryAsync(name);
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
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoinEvent(xAuthPlayerJoinEvent event)
    {
        if(event.getStatus() == xAuthPlayer.Status.AUTHENTICATED)
        {
            Player player = Bukkit.getPlayer(event.getPlayerName());
            if(player != null && player.isOnline())
            {
                // Lock the inventory to prevent changes and add to authenticated
                String name = player.getName().toLowerCase();
                inventoryLockListener.addLock(name);
                authenticated.add(name);

                // Schedule an inventory load
                loadInventoryAsync(name);
            }
        }
    }

    /**
     * Called when xAuth handles a player registration. Due to an error in the source
     * of xAuth, the player name is not accessible. A helper method uses Reflection
     * to access the player name.
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerRegisterEvent(xAuthCommandRegisterEvent event)
    {
        String name = getPlayerNameFromRegisterEvent(event);

        if(name == null) { return; }

        if(authenticated.contains(name)) { return; }

        if(event.getAction() == xAuthCommandRegisterEvent.Action.PLAYER_REGISTERED)
        {
            Player player = Bukkit.getPlayer(name);
            if(player != null && player.isOnline())
            {
                // Lock the inventory to prevent changes and add to authenticated
                inventoryLockListener.addLock(name);
                authenticated.add(name);

                // Schedule an inventory load
                loadInventoryAsync(name);
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
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if(!authenticated.contains(name)) { return; }

        ServerChangeRequest request = serverChangeRequests.remove(name);
        long currentTime = System.currentTimeMillis();

        // If there is no request or the request "timed out" (after 2 seconds)
        if(request == null || (currentTime - request.requestAt) > 2000)
        {
            // Schedule an inventory save
            saveInventoryAsync(player);
        }

        // Remove the inventory pair and authenticated
        inventoryMap.remove(name);
        authenticated.remove(name);
    }

    /**
     * Intercepts the intent to switch servers and cancels it.
     *
     * In order to make sure the inventory is saved before switching servers,
     * DeltaInventory will cancel the original event, queue an inventory save,
     * and complete the request on completion of the save.
     *
     * @param event Event to process
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
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

        // Schedule an inventory save
        saveInventoryAsync(player);
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
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event)
    {
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        GameMode originalMode = player.getGameMode();
        GameMode newMode = event.getNewGameMode();
        boolean isIgnoringChangeOnce = ignoreModeChangeOnce.remove(name);

        // Ignore game mode changes where the game modes are the same
        if(originalMode == event.getNewGameMode()) { return; }

        // Ignore if player was not authenticated
        if(!authenticated.contains(name)) { return; }

        // Check if the game mode is disabled
        if(disabledGameModes.contains(newMode))
        {
            if(!player.hasPermission("DeltaInv.Disabled.Bypass." + newMode.name()))
            {
                player.sendMessage(FAILURE + "You do not have permission to switch to " +
                    input(newMode.name()));
                event.setCancelled(true);
                return;
            }
        }

        // Prevent game mode change during saves
        if(inventoryLockListener.isLocked(name))
        {
            player.sendMessage(FAILURE + "Not allowed to change game modes while inventory is locked.");
            event.setCancelled(true);
            return;
        }

        // Prevent game mode changes unless there is no forced game mode or player is exempt
        if(forcedGameMode != null)
        {
            if(!isIgnoringChangeOnce && !player.hasPermission("DeltaInv.Forced.Bypass"))
            {
                player.sendMessage(FAILURE + "Game mode is being forced. You are not allowed to change it.");
                event.setCancelled(true);
                return;
            }
        }

        // Ignore players that have the single inventory permission
        if(player.hasPermission("DeltaInv.SingleInv")) { return; }

        InventoryPair pair = inventoryMap.get(name);
        SavedInventory inventory = new SavedInventory(player);

        // Save the inventory associated with the old game mode
        if(originalMode == GameMode.SURVIVAL)
        {
            pair.setSurvival(inventory);
        }
        else if(originalMode == GameMode.CREATIVE)
        {
            pair.setCreative(inventory);
        }

        // Apply the inventory associated with the new game mode
        if(newMode == GameMode.SURVIVAL)
        {
            player.getInventory().setContents(pair.getSurvival().getContents());
            player.getInventory().setArmorContents(pair.getSurvival().getArmor());
            pair.setSurvival(null);
        }
        else if(newMode == GameMode.CREATIVE)
        {
            player.getInventory().setContents(pair.getCreative().getContents());
            player.getInventory().setArmorContents(pair.getCreative().getArmor());
            pair.setCreative(null);
        }
        else
        {
            // Clear the inventory if new game mode is Adventure or Spectator
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
        }
    }

    private void loadInventoryAsync(String name)
    {
        PlayerLoad runnable = new PlayerLoad(name, this, plugin);

        plugin.debug("Loading inventory async for {name:" + name + "}" );
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private void saveInventoryAsync(Player player)
    {
        // Allow others plugins to modify inventory and players before saving
        PlayerPreSaveEvent preSaveEvent = new PlayerPreSaveEvent(player);
        Bukkit.getPluginManager().callEvent(preSaveEvent);

        // Create an entry and lock the inventory to prevent changes by the player
        IPlayerEntry entry = createPlayerEntry(player);
        inventoryLockListener.addLock(entry.getName());

        // Create a runnable and schedule it
        PlayerSave runnable = new PlayerSave(entry, this, plugin);
        plugin.debug("Saving inventory async for {name:" + entry.getName() + "}" );
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private void saveInventorySync(Player player)
    {
        // Allow others plugins to modify inventory and players before saving
        PlayerPreSaveEvent preSaveEvent = new PlayerPreSaveEvent(player);
        Bukkit.getPluginManager().callEvent(preSaveEvent);

        // Create an entry and lock the inventory to prevent changes by the player
        IPlayerEntry entry = createPlayerEntry(player);
        inventoryLockListener.addLock(entry.getName());

        // Create a runnable and schedule it
        PlayerSave runnable = new PlayerSave(entry, this, plugin, true);
        plugin.debug("Saving inventory sync for {name:" + entry.getName() + "}" );
        runnable.run();
    }

    private IPlayerEntry createPlayerEntry(Player player)
    {
        String name = player.getName().toLowerCase();
        PlayerEntry entry = new PlayerEntry(name);
        SavedInventory inventory = new SavedInventory(player);
        InventoryPair pair = inventoryMap.get(name);

        entry.setHealth(player.getHealth());
        entry.setFoodLevel(player.getFoodLevel());
        entry.setXpLevel(player.getLevel());
        entry.setXpProgress(player.getExp());
        entry.setPotionEffects(player.getActivePotionEffects());
        entry.setEnderChest(player.getEnderChest().getContents());

        if(player.hasPermission("DeltaInv.SingleInv"))
        {
            entry.setGameMode(player.getGameMode());
            entry.setSurvival(inventory);
            entry.setCreative(null);
        }
        else
        {
            switch(player.getGameMode())
            {
                case SURVIVAL:
                    entry.setGameMode(GameMode.SURVIVAL);
                    entry.setSurvival(inventory);
                    entry.setCreative(pair.getCreative());
                    break;
                case CREATIVE:
                    entry.setGameMode(GameMode.CREATIVE);
                    entry.setCreative(inventory);
                    entry.setSurvival(pair.getSurvival());
                    break;
                case ADVENTURE:
                    entry.setGameMode(GameMode.ADVENTURE);
                    entry.setSurvival(pair.getSurvival());
                    entry.setCreative(pair.getCreative());
                    break;
                case SPECTATOR:
                    entry.setGameMode(GameMode.SPECTATOR);
                    entry.setSurvival(pair.getSurvival());
                    entry.setCreative(pair.getCreative());
                    break;
            }
        }
        return entry;
    }

    private void loadFromEntry(Player player, IPlayerEntry entry)
    {
        InventoryPair pair = new InventoryPair();
        String name = player.getName().toLowerCase();

        pair.setSurvival(entry.getSurvival());
        pair.setCreative(entry.getCreative());
        inventoryMap.put(name, pair);

        player.setHealth(entry.getHealth());
        player.setFoodLevel(entry.getFoodLevel());
        player.setLevel(entry.getXpLevel());
        player.setExp((float) entry.getXpProgress());
        player.getEnderChest().setContents(entry.getEnderChest());

        for(PotionEffect effect : player.getActivePotionEffects())
        {
            player.removePotionEffect(effect.getType());
        }

        if(!clearEffectsOnLogin)
        {
            for(PotionEffect effect : entry.getPotionEffects())
            {
                effect.apply(player);
            }
        }

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
        if(forcedGameMode == null || player.hasPermission("DeltaInv.Forced.Bypass"))
        {
            if(player.getGameMode() != entry.getGameMode())
            {
                player.setGameMode(entry.getGameMode());
            }
        }
        else
        {
            if(player.getGameMode() != forcedGameMode)
            {
                ignoreModeChangeOnce.add(name);
                player.setGameMode(forcedGameMode);
            }
        }
    }

    private String getPlayerNameFromRegisterEvent(xAuthCommandRegisterEvent event)
    {
        try
        {
            xAuthEventProperties properties = ((xAuthEventProperties) propertiesField.get(event));
            String name = (String) properties.getProperty("playername");
            return (name == null) ? null : name.toLowerCase();
        }
        catch(IllegalAccessException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    private class ServerChangeRequest
    {
        public final String destination;
        public final long requestAt;

        public ServerChangeRequest(String destination, long requestAt)
        {
            this.destination = destination;
            this.requestAt = requestAt;
        }
    }
}
