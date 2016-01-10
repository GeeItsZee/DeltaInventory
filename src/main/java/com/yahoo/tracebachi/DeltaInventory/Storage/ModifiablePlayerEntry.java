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
package com.yahoo.tracebachi.DeltaInventory.Storage;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class ModifiablePlayerEntry implements PlayerEntry
{
    private Integer id;
    private String name;
    private double health;
    private int foodLevel;
    private int xpLevel;
    private float xpProgress;
    private GameMode gameMode;
    private String potionEffects;
    private PlayerInventory survivalInventory;
    private PlayerInventory creativeInventory;
    private ItemStack[] enderInventory;

    @Override
    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name.toLowerCase();
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
    public float getXpProgress()
    {
        return xpProgress;
    }

    public void setXpProgress(float xpProgress)
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

    public String getPotionEffects()
    {
        return potionEffects;
    }

    public void setPotionEffects(String potionEffects)
    {
        this.potionEffects = potionEffects;
    }

    @Override
    public PlayerInventory getSurvivalInventory()
    {
        return survivalInventory;
    }

    public void setSurvivalInventory(PlayerInventory survivalInventory)
    {
        this.survivalInventory = survivalInventory;
    }

    @Override
    public PlayerInventory getCreativeInventory()
    {
        return creativeInventory;
    }

    public void setCreativeInventory(PlayerInventory creativeInventory)
    {
        this.creativeInventory = creativeInventory;
    }

    @Override
    public ItemStack[] getEnderInventory()
    {
        return enderInventory;
    }

    public void setEnderInventory(ItemStack[] enderInventory)
    {
        this.enderInventory = enderInventory;
    }
}
