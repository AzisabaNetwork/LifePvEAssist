package net.azisaba.lifepveassist.listener;

import io.lumine.xikage.mythicmobs.MythicMobs;
import net.azisaba.lifepveassist.LifePvEAssist;
import net.azisaba.lifepveassist.gui.CreateInventory;
import net.azisaba.lifepveassist.gui.LifeCraftHolder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LifeCraftListener implements Listener {

    public LifeCraftListener() {}

    public static final List<ItemStack> list = new ArrayList<>();
    public static Map<Player, List<ItemStack>> map = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LifeCraftHolder) {
            event.setCancelled(true);
            ItemStack cursorItem = event.getCursor();
            ItemStack currentItem = event.getCurrentItem();
            ItemStack itemStack1 = MythicMobs.inst().getItemManager().getItemStack("compressed_diamond_block");
            ItemStack itemStack2 = MythicMobs.inst().getItemManager().getItemStack("compressed_iron_block");
            ItemStack itemStack3 = MythicMobs.inst().getItemManager().getItemStack("compressed_gold_block");
            Inventory inventory = event.getInventory();
            Inventory clickedInventory = event.getClickedInventory();
            if (currentItem != null && currentItem.hasItemMeta() && currentItem.getType().equals(Material.WHITE_STAINED_GLASS_PANE) && currentItem.getItemMeta() != null && currentItem.getItemMeta().hasCustomModelData() && currentItem.getItemMeta().getCustomModelData() == 1 && clickedInventory != null && itemStack1 != null && itemStack2 != null && itemStack3 != null && (clickedInventory.contains(itemStack1) || clickedInventory.contains(itemStack2) || clickedInventory.contains(itemStack3))) {
                for (ItemStack setItem : event.getInventory().getContents()) {
                    if (setItem != null && !setItem.getType().equals(Material.AIR) && (setItem.getType().equals(itemStack1.getType()) || setItem.getType().equals(itemStack2.getType()) || setItem.getType().equals(itemStack3.getType()))) {
                        list.add(setItem);
                        map.put((Player) event.getWhoClicked(), list);
                    }
                }
                event.getWhoClicked().getPersistentDataContainer().set(new NamespacedKey(LifePvEAssist.getInstance(), "lifecraft"), PersistentDataType.INTEGER, 1);
                new CreateInventory().step2(((Player) event.getWhoClicked()).getPlayer());
            }

            if (cursorItem != null && currentItem.hasItemMeta() && itemStack1 != null && itemStack2 != null && itemStack3 != null) {
                if (cursorItem.isSimilar(itemStack1)) {
                    set(cursorItem, inventory);
                } else if (cursorItem.isSimilar(itemStack2)) {
                    set(cursorItem, inventory);
                } else if (cursorItem.isSimilar(itemStack3)) {
                    set(cursorItem, inventory);
                }
            } else if (currentItem != null && currentItem.hasItemMeta() && itemStack1 != null && itemStack2 != null && itemStack3 != null) {
                if (currentItem.isSimilar(itemStack1)) {
                    set(currentItem, inventory);
                } else if (currentItem.isSimilar(itemStack2)) {
                    set(currentItem, inventory);
                } else if (currentItem.isSimilar(itemStack3)) {
                    set(currentItem, inventory);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LifeCraftHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().getPersistentDataContainer().has(new NamespacedKey(LifePvEAssist.getInstance(), "open"), PersistentDataType.INTEGER)) {
            event.getPlayer().getPersistentDataContainer().remove(new NamespacedKey(LifePvEAssist.getInstance(), "open"));
            return;
        }
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && !item.getType().equals(Material.AIR) && (item.getType().equals(Material.DIAMOND_BLOCK) || item.getType().equals(Material.IRON_BLOCK) || item.getType().equals(Material.GOLD_BLOCK))) {
                event.getPlayer().getInventory().addItem(item);
            }
        }
        if (!map.isEmpty() && map.containsKey(event.getPlayer())) {
            for (ItemStack item : map.get(event.getPlayer())) {
                event.getPlayer().getInventory().addItem(item);
            }
            map.remove(event.getPlayer());
            list.clear();
        }
    }

    public void set(ItemStack item, Inventory inv) {
        if (item != null) {
            for(int i = 10; i <= 16; ++i) {
                if (inv.getItem(i) == null && item.getAmount() >= 1) {
                    ItemStack itemStack = new ItemStack(item);
                    itemStack.setAmount(1);
                    inv.setItem(i, itemStack);
                    item.setAmount(item.getAmount() - 1);
                    break;
                }
            }
        }
    }
}
