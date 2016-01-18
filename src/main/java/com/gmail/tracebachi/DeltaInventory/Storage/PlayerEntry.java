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
package com.gmail.tracebachi.DeltaInventory.Storage;

import com.google.common.base.Preconditions;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by Trace Bachi (tracebachi@gmail.com, BigBossZee) on 12/12/15.
 */
public class PlayerEntry implements IPlayerEntry
{
    private final String name;
    private double health;
    private int foodLevel;
    private int xpLevel;
    private double xpProgress;
    private GameMode gameMode;
    private Collection<PotionEffect> potionEffects;
    private SavedInventory survival;
    private SavedInventory creative;
    private ItemStack[] enderChest;

    public PlayerEntry(String name)
    {
        Preconditions.checkNotNull(name, "Name cannot be null.");
        this.name = name;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public double getHealth()
    {
        return health;
    }

    public void setHealth(double health)
    {
        this.health = health;
    }

    @Override
    public int getFoodLevel()
    {
        return foodLevel;
    }

    public void setFoodLevel(int foodLevel)
    {
        this.foodLevel = foodLevel;
    }

    @Override
    public int getXpLevel()
    {
        return xpLevel;
    }

    public void setXpLevel(int xpLevel)
    {
        this.xpLevel = xpLevel;
    }

    @Override
    public double getXpProgress()
    {
        return xpProgress;
    }

    public void setXpProgress(double xpProgress)
    {
        this.xpProgress = xpProgress;
    }

    @Override
    public GameMode getGameMode()
    {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode)
    {
        this.gameMode = gameMode;
    }

    @Override
    public Collection<PotionEffect> getPotionEffects()
    {
        return (potionEffects == null) ? Collections.emptyList() : potionEffects;
    }

    public void setPotionEffects(Collection<PotionEffect> potionEffects)
    {
        this.potionEffects = potionEffects;
    }

    @Override
    public SavedInventory getSurvival()
    {
        return (survival != null) ? survival : SavedInventory.EMPTY;
    }

    public void setSurvival(SavedInventory survival)
    {
        this.survival = survival;
    }

    @Override
    public SavedInventory getCreative()
    {
        return (creative != null) ? creative : SavedInventory.EMPTY;
    }

    public void setCreative(SavedInventory creative)
    {
        this.creative = creative;
    }

    @Override
    public ItemStack[] getEnderChest()
    {
        return (enderChest != null) ? enderChest : new ItemStack[27];
    }

    public void setEnderChest(ItemStack[] enderChest)
    {
        this.enderChest = enderChest;
    }
}
