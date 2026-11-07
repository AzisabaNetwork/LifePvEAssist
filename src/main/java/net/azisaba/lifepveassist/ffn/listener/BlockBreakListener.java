package net.azisaba.lifepveassist.ffn.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class BlockBreakListener implements Listener {

    private final JavaPlugin plugin;
    private final int delaySeconds;
    private final Set<Location> gatheringLocations = new HashSet<>();

    public BlockBreakListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.delaySeconds = plugin.getConfig().getInt("ffn.GatherDelaySeconds", 30);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!isInWorld(player.getWorld().getName())) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType().isAir()) {
            return;
        }

        event.setCancelled(true);
        Location blockLocation = block.getLocation();

        if (gatheringLocations.contains(blockLocation)) {
            player.playSound(blockLocation, Sound.BLOCK_NOTE_BLOCK_HARP, 1.0F, 1.0F);
        } else {
            this.drop(player, block);
            gatheringLocations.add(blockLocation);
            Bukkit.getScheduler().runTaskLater(plugin, () -> gatheringLocations.remove(blockLocation), delaySeconds * 20L);
        }
    }

    public boolean isInWorld(String worldName) {
        return plugin.getConfig().getStringList("ffn.GatherWorlds").stream()
                .anyMatch(configuredWorld -> configuredWorld != null && configuredWorld.equalsIgnoreCase(worldName));
    }

    public void drop(Player player, Block block) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ffn.GatherList");
        if (section == null) {
            return;
        }

        for (String materialKey : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialKey);
            if (material == null || block.getType() != material) {
                continue;
            }

            for (String commandTemplate : section.getStringList(materialKey)) {
                if (commandTemplate != null && !commandTemplate.isEmpty()) {
                    String command = commandTemplate.replace("%digger%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
        }
    }
}
