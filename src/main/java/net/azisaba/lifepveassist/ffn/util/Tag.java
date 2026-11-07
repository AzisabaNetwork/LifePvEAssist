package net.azisaba.lifepveassist.ffn.util;

import net.azisaba.lifepveassist.ffn.FFN;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class Tag {

    private Tag() {}

    public static boolean hasWeaknessTag(@Nullable LivingEntity living, @Nullable String tag) {
        return checkTag(FFN.weaknessTags, living, tag);
    }

    public static boolean hasResistanceTag(@Nullable LivingEntity living, @Nullable String tag) {
        return checkTag(FFN.resistanceTags, living, tag);
    }

    public static boolean hasTag(@Nullable LivingEntity living, @Nullable String tag) {
        return hasWeaknessTag(living, tag);
    }

    public static boolean hasProtectTag(@Nullable LivingEntity living, @Nullable String tag) {
        return hasResistanceTag(living, tag);
    }

    private static boolean checkTag(Map<LivingEntity, Map<String, Double>> tagSource, @Nullable LivingEntity living, @Nullable String tag) {
        if (living == null || tag == null) {
            return false;
        }
        Map<String, Double> tags = tagSource.get(living);
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.containsKey(tag.toLowerCase(Locale.ROOT));
    }

    public static double getWeakDamage(@Nullable LivingEntity living, @Nullable String tag, double damage) {
        if (living == null || tag == null) {
            return damage;
        }
        Map<String, Double> tags = FFN.weaknessTags.get(living);
        if (tags == null) {
            return damage;
        }
        Double multiple = tags.get(tag.toLowerCase(Locale.ROOT));
        return multiple != null ? damage * multiple : damage;
    }

    public static double getProtectDamage(@Nullable LivingEntity living, @Nullable String tag, double damage) {
        if (living == null || tag == null) {
            return damage;
        }
        Map<String, Double> tags = FFN.resistanceTags.get(living);
        if (tags == null) {
            return damage;
        }
        Double multiple = tags.get(tag.toLowerCase(Locale.ROOT));
        return (multiple != null && multiple != 0.0) ? damage / multiple : damage;
    }
}
