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

import com.google.common.base.Preconditions;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/12/15.
 */
public class SavedInventory
{
    public static final SavedInventory EMPTY = new SavedInventory(new ItemStack[4], new ItemStack[36]);

    private ItemStack[] armor;
    private ItemStack[] contents;

    public SavedInventory(Player player)
    {
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack[] contents = player.getInventory().getContents();

        this.armor = (armor != null) ? armor : new ItemStack[4];
        this.contents = (contents != null) ? contents : new ItemStack[36];
    }

    public SavedInventory(ItemStack[] armor, ItemStack[] contents)
    {
        Preconditions.checkNotNull(armor, "Armor cannot be null.");
        Preconditions.checkNotNull(contents, "Contents cannot be null.");
        Preconditions.checkArgument(armor.length == 4, "Armor size must be 4.");
        Preconditions.checkArgument(contents.length == 36, "Content size must be 36.");

        this.armor = armor;
        this.contents = contents;
    }

    public ItemStack[] getArmor()
    {
        return armor;
    }

    public ItemStack[] getContents()
    {
        return contents;
    }
}
