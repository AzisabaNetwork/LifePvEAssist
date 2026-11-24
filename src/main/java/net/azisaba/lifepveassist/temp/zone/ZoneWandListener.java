package net.azisaba.lifepveassist.temp.zone;

import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 温度エリア選択ワンドの左クリック (pos1) / 右クリック (pos2) を処理するリスナー。
 */
public class ZoneWandListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ZoneManager zoneManager;
    private final ConfigManager config;

    public ZoneWandListener(ZoneManager zoneManager, ConfigManager config) {
        this.zoneManager = zoneManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!zoneManager.isWand(event.getItem())) {
            return;
        }
        if (!config.isAdmin(player)) {
            return;
        }

        Action action = event.getAction();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            ZoneSelection sel = zoneManager.getSelection(player);
            sel.setPos1(block.getLocation());
            sendSelectionMessage(player, "pos1", block.getLocation(), sel);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            ZoneSelection sel = zoneManager.getSelection(player);
            sel.setPos2(block.getLocation());
            sendSelectionMessage(player, "pos2", block.getLocation(), sel);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        zoneManager.clearSelection(event.getPlayer().getUniqueId());
    }

    private void sendSelectionMessage(Player player, String label, org.bukkit.Location loc, ZoneSelection sel) {
        String volumeText = sel.isComplete() ? " <gray>(体積: <white>" + sel.volume() + "ブロック<gray>)" : "";
        player.sendMessage(MINI.deserialize(
                "<gold>[LifeTemp] <green>" + label + " を設定しました: <yellow>("
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                        + ") <gray>[" + loc.getWorld().getName() + "]" + volumeText
        ));
    }
}
