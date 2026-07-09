package net.azisaba.lifepveassist.gui;

import net.azisaba.lifepveassist.LifePvEAssist;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class CreateItem {

    public CreateItem() {}

    public ItemStack getBackPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        } else {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f"));
            meta.getPersistentDataContainer().set(new NamespacedKey(LifePvEAssist.getInstance(), "lifecraft"), PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);
            return item;
        }
    }

    public ItemStack getNextStep2() {
        ItemStack item = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        } else {
            meta.setCustomModelData(1);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lこのアイテムを元に次へ進む"));
            meta.getPersistentDataContainer().set(new NamespacedKey(LifePvEAssist.getInstance(), "lifecraft"), PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);
            return item;
        }
    }
}
