package net.azisaba.lifepveassist.listener;

import net.azisaba.lifepveassist.gui.CraftHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftListener implements Listener {

    private static final int[][] INPUT_RANGES = {{10, 16}, {19, 25}, {28, 34}, {37, 43}};

    public CraftListener() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CraftHolder) {
            event.setCancelled(true);
            ItemStack cursorItem = event.getCursor();
            ItemStack clickedItem = event.getCurrentItem();
            if (cursorItem != null && cursorItem.hasItemMeta() && cursorItem.getItemMeta() != null) {
                List<Component> lore = cursorItem.getItemMeta().lore();
                if (lore != null) {
                    for (Component lineComp : lore) {
                        String line = PlainTextComponentSerializer.plainText().serialize(lineComp);
                        if (line.contains("[LifeCraft]")) {
                            this.set(cursorItem, event.getInventory());
                        }
                    }
                }
            } else if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta() != null) {
                List<Component> lore = clickedItem.getItemMeta().lore();
                if (lore != null) {
                    for (Component lineComp : lore) {
                        String line = PlainTextComponentSerializer.plainText().serialize(lineComp);
                        if (line.contains("[LifeCraft]")) {
                            this.set(clickedItem, event.getInventory());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CraftHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftHolder)) return;
        List<ItemStack> stored = LifeCraftListener.map.remove(event.getPlayer().getUniqueId());
        if (stored != null) {
            for (ItemStack item : stored) {
                LifeCraftListener.giveBack(event.getPlayer(), item);
            }
        }
        // step2 に置いた [LifeCraft] アイテムも返却する (返さないと消失する)
        Inventory inventory = event.getInventory();
        for (int[] range : INPUT_RANGES) {
            for (int i = range[0]; i <= range[1]; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    inventory.setItem(i, null);
                    LifeCraftListener.giveBack(event.getPlayer(), item);
                }
            }
        }
    }

    public void set(ItemStack item, Inventory inventory) {
        if (item != null) {
            for(int i = 10; i <= 16; ++i) {
                if (inventory.getItem(i) == null && item.getAmount() >= 1) {
                    ItemStack itemStack = new ItemStack(item);
                    itemStack.setAmount(1);
                    inventory.setItem(i, itemStack);
                    item.setAmount(item.getAmount() - 1);
                    return;
                }
            }

            for(int i = 19; i <= 25; ++i) {
                if (inventory.getItem(i) == null && item.getAmount() >= 1) {
                    ItemStack itemStack = new ItemStack(item);
                    itemStack.setAmount(1);
                    inventory.setItem(i, itemStack);
                    item.setAmount(item.getAmount() - 1);
                    return;
                }
            }

            for(int i = 28; i <= 34; ++i) {
                if (inventory.getItem(i) == null && item.getAmount() >= 1) {
                    ItemStack itemStack = new ItemStack(item);
                    itemStack.setAmount(1);
                    inventory.setItem(i, itemStack);
                    item.setAmount(item.getAmount() - 1);
                    return;
                }
            }

            for(int i = 37; i <= 43; ++i) {
                if (inventory.getItem(i) == null && item.getAmount() >= 1) {
                    ItemStack itemStack = new ItemStack(item);
                    itemStack.setAmount(1);
                    inventory.setItem(i, itemStack);
                    item.setAmount(item.getAmount() - 1);
                    return;
                }
            }

        }
    }
}
