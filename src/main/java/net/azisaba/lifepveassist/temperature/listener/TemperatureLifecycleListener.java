package net.azisaba.lifepveassist.temperature.listener;

import net.azisaba.lifepveassist.temperature.core.TemperatureService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 温度管理のプレイヤーライフサイクル・トーテム無効化・死亡ログ差し替え・バニラ凍結ダメージ抑止リスナー。
 */
public class TemperatureLifecycleListener implements Listener {

    private final TemperatureService service;

    public TemperatureLifecycleListener(TemperatureService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.onWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Component customMessage = service.handlePlayerDeath(event.getEntity());
        if (customMessage != null) {
            event.deathMessage(customMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.onWorldChange(event.getPlayer());
    }

    /**
     * 温度由来の死亡時に不死のトーテム等による蘇生を確実に無効化する。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && service.isLethalPending(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * 低温視界エフェクト用に freezeTicks をロックしているため、バニラ由来の凍結ダメージを抑止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) {
            return;
        }
        if (event.getEntity() instanceof Player player && service.isFreezeOverlayManaged(player)) {
            event.setCancelled(true);
        }
    }
}
