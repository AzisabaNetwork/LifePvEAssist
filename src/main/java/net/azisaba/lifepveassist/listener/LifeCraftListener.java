package net.azisaba.lifepveassist.listener;

import io.lumine.mythic.bukkit.MythicBukkit;
import net.azisaba.lifepveassist.LifePvEAssist;
import net.azisaba.lifepveassist.gui.CreateInventory;
import net.azisaba.lifepveassist.gui.LifeCraftHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LifeCraftListener implements Listener {

    /** 鉱石ブロックを置ける入力スロット */
    private static final int INPUT_START = 10;
    private static final int INPUT_END = 16;

    /** step1 で預かった鉱石ブロック (step2 を閉じたときに返却する) */
    public static final Map<UUID, List<ItemStack>> map = new ConcurrentHashMap<>();

    public LifeCraftListener() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LifeCraftHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack itemStack1 = MythicBukkit.inst().getItemManager().getItemStack("compressed_diamond_block");
        ItemStack itemStack2 = MythicBukkit.inst().getItemManager().getItemStack("compressed_iron_block");
        ItemStack itemStack3 = MythicBukkit.inst().getItemManager().getItemStack("compressed_gold_block");
        if (itemStack1 == null || itemStack2 == null || itemStack3 == null) return;

        Inventory inventory = event.getInventory();
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        // 次へボタン
        if (event.getClickedInventory() == inventory && isNextButton(currentItem)) {
            List<ItemStack> stored = new ArrayList<>();
            for (int i = INPUT_START; i <= INPUT_END; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    stored.add(item.clone());
                    // GUIから取り除いておくことで、閉じたときの二重返却を防ぐ
                    inventory.setItem(i, null);
                }
            }
            if (stored.isEmpty()) return;
            map.merge(player.getUniqueId(), stored, (a, b) -> { a.addAll(b); return a; });
            player.getPersistentDataContainer().set(new NamespacedKey(LifePvEAssist.getInstance(), "lifecraft"), PersistentDataType.INTEGER, 1);
            // クリックイベント中にインベントリを開き直さないよう次tickで開く
            Bukkit.getScheduler().runTask(LifePvEAssist.getInstance(), () -> new CreateInventory().step2(player));
            return;
        }

        if (cursorItem != null && !cursorItem.getType().isAir()) {
            if (isTarget(cursorItem, itemStack1, itemStack2, itemStack3)) {
                set(cursorItem, inventory);
            }
        } else if (currentItem != null && event.getClickedInventory() != inventory
                && isTarget(currentItem, itemStack1, itemStack2, itemStack3)) {
            set(currentItem, inventory);
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
        if (!(event.getInventory().getHolder() instanceof LifeCraftHolder)) return;
        HumanEntity player = event.getPlayer();
        Inventory inventory = event.getInventory();
        for (int i = INPUT_START; i <= INPUT_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(i, null);
                giveBack(player, item);
            }
        }
    }

    public static void giveBack(HumanEntity player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    private boolean isNextButton(ItemStack item) {
        return item != null && item.getType() == Material.WHITE_STAINED_GLASS_PANE && item.hasItemMeta()
                && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 1;
    }

    private boolean isTarget(ItemStack item, ItemStack... targets) {
        for (ItemStack target : targets) {
            if (item.isSimilar(target)) return true;
        }
        return false;
    }

    public void set(ItemStack item, Inventory inv) {
        for (int i = INPUT_START; i <= INPUT_END; ++i) {
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
