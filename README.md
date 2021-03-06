# DeltaInventory
DeltaInventory is an inventory synchronization plugin for Bukkit / Spigot using BungeeCord. It
relies on DeltaEssential's ServerSwitchEvent to save inventories before players switch which
guarantees that data will be ready and up-to-date when they arrive at their destination server.

## Features
- Storage of inventories in YML files that can be shared between servers through soft links
- Bukkit/Spigot's version compatible ItemStack serialization
- Async inventory loading and saving

# Licence ([GPLv3](http://www.gnu.org/licenses/gpl-3.0.en.html))
```
DeltaInventory - Basic server functionality for Bukkit/Spigot servers using BungeeCord.
Copyright (C) 2015  Trace Bachi (tracebachi@gmail.com)

DeltaInventory is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DeltaInventory is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with DeltaInventory.  If not, see <http://www.gnu.org/licenses/>.
```
