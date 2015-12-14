package com.yahoo.tracebachi.DeltaInventory;

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
    Pattern semiColonPattern = Pattern.compile(";");
    Pattern commaPattern = Pattern.compile(",");

    static String serialize(Collection<PotionEffect> effects)
    {
        StringBuilder builder = new StringBuilder("");

        for(PotionEffect effect : effects)
        {
            builder.append(serializePotionEffect(effect));
            builder.append(";");
        }

        return builder.toString();
    }

    static List<PotionEffect> deserialize(String source)
    {
        String[] split = semiColonPattern.split(source);
        List<PotionEffect> effects = new ArrayList<>(split.length);

        for(String splitSource : split)
        {
            try
            {
                PotionEffect effect = deserializePotionEffect(splitSource);
                effects.add(effect);
            }
            catch(NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        }

        return effects;
    }

    static String serializePotionEffect(PotionEffect effect)
    {
        return effect.getType().getId() + "," + effect.getAmplifier() + "," + effect.getDuration();
    }

    static PotionEffect deserializePotionEffect(String source)
    {
        String split[] = commaPattern.split(source, 3);
        int id = Integer.parseInt(split[0]);
        int amplifier = Integer.parseInt(split[1]);
        int duration = Integer.parseInt(split[2]);

        return new PotionEffect(PotionEffectType.getById(id), duration, amplifier);
    }
}
