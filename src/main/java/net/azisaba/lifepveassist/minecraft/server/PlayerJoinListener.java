package net.azisaba.lifepveassist.minecraft.server;

import net.azisaba.lifepveassist.LifePvEAssist;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final LifePvEAssist plugin;

    public PlayerJoinListener(LifePvEAssist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("Packet listener automatically bound to player: " + player.getName());
    }
}
