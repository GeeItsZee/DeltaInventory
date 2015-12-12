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

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/11/15.
 */
public interface LoggablePlugin
{
    /**
     * Logs the message as INFO.
     *
     * @param message Message to log.
     */
    void info(String message);

    /**
     * Logs the message as SEVERE.
     *
     * @param message Message to log.
     */
    void severe(String message);

    /**
     * Logs the message as DEBUG if debug is enabled.
     *
     * @param message Message to log.
     */
    void debug(String message);
}
