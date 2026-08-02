package net.azisaba.lifepveassist.minecraft.server;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import net.azisaba.lifepveassist.LifePvEAssist;
import net.azisaba.lifepvelevel.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PacketListenerImpl extends PacketListenerAbstract {

    public static final Map<UUID, Boolean> map = new HashMap<>();
    public static final Set<UUID> hideNotification = new HashSet<>();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Object user = event.getPlayer();
        if (!(user instanceof Player player)) return;

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            if (player.getGameMode() == GameMode.ADVENTURE) {
                RayTraceResult ray = player.rayTraceBlocks(4.5D);
                if (ray != null && ray.getHitBlock() != null && ray.getHitBlockFace() != null) {
                    if (Util.canUseItem(player, player.getInventory().getItemInMainHand()) && Util.canUseItem(player, player.getInventory().getItemInOffHand())) {
                        Block b = ray.getHitBlock();
                        Action action = b.getType() == Material.AIR ? Action.LEFT_CLICK_AIR : Action.LEFT_CLICK_BLOCK;
                        BlockFace face = ray.getHitBlockFace();
                        Bukkit.getScheduler().runTask(LifePvEAssist.getInstance(), () -> {
                            PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                                    player, action, player.getInventory().getItemInMainHand(), b, face
                            );
                            interactEvent.callEvent();
                        });
                    }
                }
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            Vector3i pos = wrapper.getBlockPosition();
            Block targetBlock = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            Material type = targetBlock.getType();

            if (wrapper.getHand() == InteractionHand.MAIN_HAND && (type == Material.IRON_BLOCK || type == Material.GOLD_BLOCK)) {
                org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
                Material handItemType = handItem.getType();
                if (handItemType.isBlock() && handItemType != Material.AIR) {
                    if (handItem.hasItemMeta() && handItem.getItemMeta().hasLore()) {
                        if (!player.isSneaking()) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("このアイテムには説明文があります．強制的に置く場合は、Shiftキーを押しながら設置してください。", NamedTextColor.RED));
                            Bukkit.getScheduler().runTask(LifePvEAssist.getInstance(), player::updateInventory);
                            return;
                        }
                    }
                    return;
                }
                if (!map.containsKey(player.getUniqueId())) {
                    event.setCancelled(true);
                    if (!hideNotification.contains(player.getUniqueId())) {
                        player.sendMessage(Component.text("/blockingで、鉄・金ブロックのmcMMOスキルを発動できるようにします。", NamedTextColor.GREEN));
                        player.sendMessage(Component.text("※この通知は /blocking notify で非表示にできます", NamedTextColor.GRAY));
                    }
                    Bukkit.getScheduler().runTask(LifePvEAssist.getInstance(), player::updateInventory);
                }
            }
        }
    }
}