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
package com.yahoo.tracebachi.DeltaInventory.Utils;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Trace Bachi (tracebachi@yahoo.com, BigBossZee) on 12/13/15.
 */
public interface PotionEffectUtils
{
    Pattern commaPattern = Pattern.compile(",");

    static List<String> toStringList(Collection<PotionEffect> effects)
    {
        List<String> result = new ArrayList<>(effects.size());

        for(PotionEffect effect : effects)
        {
            result.add(serialize(effect));
        }

        return result;
    }

    static List<PotionEffect> toEffectList(Collection<String> serialized)
    {
        List<PotionEffect> effects = new ArrayList<>(serialized.size());

        for(String effectString : serialized)
        {
            try
            {
                PotionEffect effect = deserialize(effectString);
                effects.add(effect);
            }
            catch(NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        }

        return effects;
    }

    static String serialize(PotionEffect effect)
    {
        return effect.getType().getName() + "," + effect.getAmplifier() + "," + effect.getDuration();
    }

    static PotionEffect deserialize(String source)
    {
        String split[] = commaPattern.split(source, 3);
        int amplifier = Integer.parseInt(split[1]);
        int duration = Integer.parseInt(split[2]);

        return new PotionEffect(PotionEffectType.getByName(split[0]), duration, amplifier);
    }
}
