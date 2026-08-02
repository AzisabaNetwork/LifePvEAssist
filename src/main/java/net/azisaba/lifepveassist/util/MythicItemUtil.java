package net.azisaba.lifepveassist.util;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;

public class MythicItemUtil {

    public MythicItemUtil() {}

    public boolean isMythicItem(ItemStack item) {
        if (item == null) return false;
        return MythicBukkit.inst().getItemManager().isMythicItem(item);
    }

    public ItemStack getMythicItem(String string) {
        return MythicBukkit.inst().getItemManager().getItem(string).isPresent() ? MythicBukkit.inst().getItemManager().getItemStack(string) : null;
    }

    public String getMythicType(ItemStack item) {
        if (item == null) {
            return null;
        } else {
            return MythicBukkit.inst().getItemManager().getMythicTypeFromItem(item);
        }
    }
}
