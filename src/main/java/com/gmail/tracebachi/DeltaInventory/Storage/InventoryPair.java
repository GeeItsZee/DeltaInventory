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

/**
 * Created by Trace Bachi (tracebachi@gmail.com, BigBossZee) on 12/12/15.
 */
public class InventoryPair
{
    private SavedInventory survival;
    private SavedInventory creative;

    public SavedInventory getSurvival()
    {
        return (survival != null) ? survival : SavedInventory.EMPTY;
    }

    public void setSurvival(SavedInventory survival)
    {
        this.survival = survival;
    }

    public SavedInventory getCreative()
    {
        return (creative != null) ? creative : SavedInventory.EMPTY;
    }

    public void setCreative(SavedInventory creative)
    {
        this.creative = creative;
    }
}
