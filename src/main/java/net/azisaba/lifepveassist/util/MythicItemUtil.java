package net.azisaba.lifepveassist.util;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.util.jnbt.CompoundTag;
import org.bukkit.inventory.ItemStack;

public class MythicItemUtil {

    public MythicItemUtil() {}

    public boolean isMythicItem(ItemStack item) {
        if (item == null) return false;
        CompoundTag tag = MythicMobs.inst().getVolatileCodeHandler().getItemHandler().getNBTData(item);
        return tag != null && tag.containsKey("MYTHIC_TYPE");
    }

    public ItemStack getMythicItem(String string) {
        return MythicMobs.inst().getItemManager().getItem(string).isPresent() ? MythicMobs.inst().getItemManager().getItemStack(string) : null;
    }

    public String getMythicType(ItemStack item) {
        if (item == null) {
            return null;
        } else {
            CompoundTag tag = MythicMobs.inst().getVolatileCodeHandler().getItemHandler().getNBTData(item);
            return tag != null && tag.containsKey("MYTHIC_TYPE") ? tag.getString("MYTHIC_TYPE") : null;
        }
    }
}
