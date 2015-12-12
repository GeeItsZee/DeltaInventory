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
public interface UnmodifiablePlayerEntry
{
    Integer getId();

    String getName();

    double getHealth();

    int getXpLevel();

    float getXpProgress();

    int getGameMode();

    ItemStack[] getArmor();

    ItemStack[] getSurvivalInventory();

    ItemStack[] getCreativeInventory();

    ItemStack[] getEnderInventory();
}
