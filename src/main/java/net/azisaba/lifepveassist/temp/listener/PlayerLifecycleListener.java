package net.azisaba.lifepveassist.temp.listener;

import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * プレイヤーの参加・退出・死亡・ワールド移動およびバニラ凍結ダメージ抑制を扱うリスナー。
 */
public class PlayerLifecycleListener implements Listener {

    private final TemperatureManager temperatureManager;

    public PlayerLifecycleListener(TemperatureManager temperatureManager) {
        this.temperatureManager = temperatureManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        temperatureManager.loadPlayer(player);
        temperatureManager.handleWorldChange(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        temperatureManager.unloadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        temperatureManager.handleWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        temperatureManager.handlePlayerDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // リスポーン後に BossBar とエフェクト状態を同期
        temperatureManager.handleWorldChange(event.getPlayer());
    }

    /**
     * 低温視界エフェクト用に freezeTicks を最大値でロックしているため、
     * バニラ由来の凍結ダメージ (DamageCause.FREEZE) が勝手に発生しないようにキャンセルする。
     * （継続ダメージはプラグイン側の設定・防具4部位無効化ロジックで制御する）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) {
            return;
        }
        if (event.getEntity() instanceof Player player && temperatureManager.isFreezeOverlayManaged(player)) {
            event.setCancelled(true);
        }
    }
}
