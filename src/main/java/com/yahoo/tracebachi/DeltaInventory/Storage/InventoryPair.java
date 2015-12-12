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

import org.bukkit.inventory.ItemStack;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class InventoryPair
{
    private ItemStack[] survival;
    private ItemStack[] creative;

    public ItemStack[] getSurvival()
    {
        return (survival == null) ? new ItemStack[36] : survival;
    }

    public void setSurvival(ItemStack[] survival)
    {
        this.survival = survival;
    }

    public ItemStack[] getCreative()
    {
        return (creative == null) ? new ItemStack[36] : creative;
    }

    public void setCreative(ItemStack[] creative)
    {
        this.creative = creative;
    }
}
