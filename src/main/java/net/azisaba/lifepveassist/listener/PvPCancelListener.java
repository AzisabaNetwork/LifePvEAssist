package net.azisaba.lifepveassist.listener;

import net.azisaba.lifepveassist.LifePvEAssist;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

public class PvPCancelListener implements Listener {

    private final LifePvEAssist plugin;

    public PvPCancelListener(LifePvEAssist plugin) {
        this.plugin = plugin;
    }

    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player entity = (Player) event.getEntity();
            if (damager.getUniqueId().equals(entity.getUniqueId())) return;
            List<String> enabledWorlds = plugin.getConfig().getStringList("pvp-enabled-worlds");
            String currentWorldName = damager.getWorld().getName();
            boolean isPvPEnabled = enabledWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(currentWorldName));
            if (isPvPEnabled) return;
            event.setCancelled(true);
        }
    }
}
