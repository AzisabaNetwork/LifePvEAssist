package net.azisaba.lifepveassist.temp.core;

import net.azisaba.lifepveassist.temp.armor.ArmorInspector;
import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.model.ArmorResistance;
import net.azisaba.lifepveassist.temp.model.PlayerTemperatureData;
import net.azisaba.lifepveassist.temp.model.TemperatureCalculator;
import net.azisaba.lifepveassist.temp.model.TemperatureEvaluation;
import net.azisaba.lifepveassist.temp.model.TemperatureSeverity;
import net.azisaba.lifepveassist.temp.storage.TemperatureStorage;
import net.azisaba.lifepveassist.temp.ui.BossBarController;
import net.azisaba.lifepveassist.temp.ui.WarningController;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全プレイヤーの温度状態・自然回復・デメリット・視界エフェクト・即死を統括するコアマネージャ。
 */
public class TemperatureManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager config;
    private final ArmorInspector armorInspector;
    private final TemperatureStorage storage;
    private final BossBarController bossBarController;
    private final WarningController warningController;

    private final Map<UUID, PlayerTemperatureData> playerDataMap = new ConcurrentHashMap<>();
    private BukkitTask mainTickTask;
    private BukkitTask autoSaveTask;

    public TemperatureManager(
            Plugin plugin,
            ConfigManager config,
            ArmorInspector armorInspector,
            TemperatureStorage storage,
            BossBarController bossBarController,
            WarningController warningController
    ) {
        this.plugin = plugin;
        this.config = config;
        this.armorInspector = armorInspector;
        this.storage = storage;
        this.bossBarController = bossBarController;
        this.warningController = warningController;
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player, now);
        }

        // 1秒 (20tick) ごとのメイン更新タスク
        this.mainTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllPlayers, 20L, 20L);

        // 定期保存タスク
        scheduleAutoSave();
    }

    public void restartAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        scheduleAutoSave();
    }

    private void scheduleAutoSave() {
        long periodTicks = config.getSaveIntervalSeconds() * 20L;
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAllPlayers, periodTicks, periodTicks);
    }

    public void shutdown() {
        if (mainTickTask != null) {
            mainTickTask.cancel();
            mainTickTask = null;
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        Map<UUID, Double> snapshot = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerTemperatureData data = playerDataMap.get(player.getUniqueId());
            if (data != null) {
                clearVisualEffects(player, data);
                snapshot.put(player.getUniqueId(), data.getTemperature());
            }
        }
        for (Map.Entry<UUID, PlayerTemperatureData> entry : playerDataMap.entrySet()) {
            snapshot.putIfAbsent(entry.getKey(), entry.getValue().getTemperature());
        }
        storage.flushAllSyncAndClose(snapshot);
        bossBarController.clearAll();
        playerDataMap.clear();
    }

    public PlayerTemperatureData loadPlayer(Player player) {
        return loadPlayer(player, System.currentTimeMillis());
    }

    private PlayerTemperatureData loadPlayer(Player player, long now) {
        UUID uuid = player.getUniqueId();
        PlayerTemperatureData data = new PlayerTemperatureData(uuid, config.getDefaultTemp(), now);
        playerDataMap.put(uuid, data);

        storage.loadAsync(uuid, config.getDefaultTemp()).thenAccept(savedTemp ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    PlayerTemperatureData current = playerDataMap.get(uuid);
                    if (current == null) {
                        return;
                    }
                    current.setTemperature(savedTemp);
                    if (config.isEnabledWorld(player.getWorld())) {
                        evaluateAndApply(player, current, System.currentTimeMillis(), false);
                    }
                })
        );
        return data;
    }

    public void unloadPlayer(Player player) {
        PlayerTemperatureData data = playerDataMap.remove(player.getUniqueId());
        if (data != null) {
            clearVisualEffects(player, data);
            storage.saveAsync(player.getUniqueId(), data.getTemperature());
        }
        bossBarController.hide(player);
    }

    public void handleWorldChange(Player player) {
        if (!config.isEnabledWorld(player.getWorld())) {
            PlayerTemperatureData data = playerDataMap.get(player.getUniqueId());
            if (data != null) {
                clearVisualEffects(player, data);
            }
            bossBarController.hide(player);
        } else {
            PlayerTemperatureData data = getOrCreateData(player);
            long now = System.currentTimeMillis();
            // 有効ワールドに入った直後にすぐ UI を更新
            evaluateAndApply(player, data, now, false);
        }
    }

    public void handlePlayerDeath(Player player) {
        PlayerTemperatureData data = getOrCreateData(player);
        clearVisualEffects(player, data);
        if (config.isResetOnDeath()) {
            data.setTemperature(config.getDefaultTemp());
            data.setLastExternalChangeMillis(System.currentTimeMillis());
            data.setLastWarningMillis(0L);
            data.setLastDamageMillis(0L);
            storage.saveAsync(player.getUniqueId(), data.getTemperature());
        }
    }

    public boolean isFreezeOverlayManaged(Player player) {
        PlayerTemperatureData data = playerDataMap.get(player.getUniqueId());
        return data != null && data.isFreezeOverlayApplied();
    }

    public PlayerTemperatureData getOrCreateData(Player player) {
        return playerDataMap.computeIfAbsent(
                player.getUniqueId(),
                uuid -> new PlayerTemperatureData(uuid, config.getDefaultTemp(), System.currentTimeMillis())
        );
    }

    public double getTemperature(Player player) {
        return getOrCreateData(player).getTemperature();
    }

    public void addTemperature(Player player, double delta, boolean resetRecoveryTimer) {
        if (!Double.isFinite(delta)) {
            return;
        }
        if (!config.isEnabledWorld(player.getWorld())) {
            return;
        }
        PlayerTemperatureData data = getOrCreateData(player);
        long now = System.currentTimeMillis();
        data.setTemperature(data.getTemperature() + delta);
        if (resetRecoveryTimer) {
            data.setLastExternalChangeMillis(now);
        }
        storage.saveAsync(player.getUniqueId(), data.getTemperature());
        evaluateAndApply(player, data, now, false);
    }

    public void setTemperature(Player player, double value, boolean resetRecoveryTimer) {
        if (!Double.isFinite(value)) {
            return;
        }
        PlayerTemperatureData data = getOrCreateData(player);
        long now = System.currentTimeMillis();
        data.setTemperature(value);
        if (resetRecoveryTimer) {
            data.setLastExternalChangeMillis(now);
        }
        storage.saveAsync(player.getUniqueId(), data.getTemperature());
        if (config.isEnabledWorld(player.getWorld())) {
            evaluateAndApply(player, data, now, false);
        }
    }

    public void resetTemperature(Player player) {
        setTemperature(player, config.getDefaultTemp(), true);
    }

    public TemperatureEvaluation evaluateCurrent(Player player) {
        PlayerTemperatureData data = getOrCreateData(player);
        ArmorResistance resistance = armorInspector.inspectPlayer(player);
        return TemperatureCalculator.evaluate(
                data.getTemperature(),
                config.getDefaultTemp(),
                config.getBaseMinLimit(),
                config.getBaseMaxLimit(),
                resistance,
                config.getStage1Offset(),
                config.getStage2Offset(),
                config.getWarningOffset(),
                config.getStage1ImmunityPieces(),
                config.getStage2DamageImmunityPieces()
        );
    }

    public void saveAllPlayers() {
        Map<UUID, Double> snapshot = new HashMap<>();
        for (Map.Entry<UUID, PlayerTemperatureData> entry : playerDataMap.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().getTemperature());
        }
        storage.flushAllAsync(snapshot);
    }

    private void tickAllPlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!config.isEnabledWorld(player.getWorld())) {
                PlayerTemperatureData existing = playerDataMap.get(player.getUniqueId());
                if (existing != null) {
                    clearVisualEffects(player, existing);
                }
                bossBarController.hide(player);
                continue;
            }

            PlayerTemperatureData data = getOrCreateData(player);
            if (player.isDead()) {
                clearVisualEffects(player, data);
                continue;
            }

            // 自然回復処理: 最後の外部温度変更から delay-seconds 秒経過していたら 1秒ごとに rate ずつ戻る
            long delayMillis = config.getRecoveryDelaySeconds() * 1000L;
            if ((now - data.getLastExternalChangeMillis()) >= delayMillis) {
                double current = data.getTemperature();
                double defaultTemp = config.getDefaultTemp();
                if (Math.abs(current - defaultTemp) > 1e-6) {
                    double next = TemperatureCalculator.stepRecovery(
                            current, defaultTemp, config.getRecoveryRatePerSecond());
                    data.setTemperature(next);
                    storage.saveAsync(player.getUniqueId(), next);
                }
            }

            evaluateAndApply(player, data, now, true);
        }
    }

    private void evaluateAndApply(Player player, PlayerTemperatureData data, long now, boolean allowPeriodicDamage) {
        if (player.isDead() || !player.isOnline()) {
            return;
        }

        ArmorResistance resistance = armorInspector.inspectPlayer(player);
        TemperatureEvaluation eval = TemperatureCalculator.evaluate(
                data.getTemperature(),
                config.getDefaultTemp(),
                config.getBaseMinLimit(),
                config.getBaseMaxLimit(),
                resistance,
                config.getStage1Offset(),
                config.getStage2Offset(),
                config.getWarningOffset(),
                config.getStage1ImmunityPieces(),
                config.getStage2DamageImmunityPieces()
        );

        // BossBar 更新（毎秒および変更時）
        bossBarController.update(player, eval, resistance);

        // クリエイティブ / スペクテイター / bypass権限の場合はデメリットを免除
        if (isExemptFromPenalties(player)) {
            clearVisualEffects(player, data);
            return;
        }

        // 第3段階: 即死
        if (eval.triggerLethal()) {
            String deathMsg = (eval.severity() == TemperatureSeverity.HOT_LETHAL)
                    ? config.getHeatDeathMessage()
                    : config.getColdDeathMessage();
            if (deathMsg != null && !deathMsg.isBlank()) {
                player.sendMessage(MINI.deserialize(
                        deathMsg.replace("<temp>", BossBarController.formatTemp(eval.temperature()))
                ));
            }
            clearVisualEffects(player, data);
            // 死亡前にあらかじめリセット処理が走る PlayerDeathEvent と整合するよう、ここで即死させる
            player.setHealth(0.0);
            return;
        }

        // 第1段階: 鈍足・盲目（2部位で無効化）
        if (eval.applySlownessBlindness()) {
            int duration = config.getEffectDurationTicks();
            if (config.isSlownessEnabled()) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, config.getSlownessAmplifier(), true, false, true));
            }
            if (config.isBlindnessEnabled()) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, duration, config.getBlindnessAmplifier(), true, false, true));
            }
        }

        // 第2段階: 継続ダメージ（4部位で無効化）
        if (allowPeriodicDamage && eval.applyContinuousDamage() && config.isDamageEnabled() && config.getDamageAmount() > 0.0) {
            long dmgIntervalMillis = config.getDamageIntervalSeconds() * 1000L;
            if ((now - data.getLastDamageMillis()) >= dmgIntervalMillis) {
                data.setLastDamageMillis(now);
                applyContinuousDamage(player, config.getDamageAmount(), config.isDamageBypassArmor());
            }
        }

        // 視界エフェクト（炎上・粉雪）: 4部位でも維持される
        updateVisualEffects(player, data, eval);

        // 警告 Title
        warningController.update(player, data, eval, now);
    }

    private void applyContinuousDamage(Player player, double amount, boolean bypassArmor) {
        if (player.isDead() || player.getHealth() <= 0.0) {
            return;
        }
        if (bypassArmor) {
            double nextHealth = Math.max(0.0, player.getHealth() - amount);
            player.setHealth(nextHealth);
        } else {
            player.damage(amount);
        }
    }

    private void updateVisualEffects(Player player, PlayerTemperatureData data, TemperatureEvaluation eval) {
        // 高温時: 見た目のみの炎上エフェクト
        if (eval.applyHeatVisual() && config.isHeatFireOverlay()) {
            player.setVisualFire(TriState.TRUE);
            data.setVisualFireApplied(true);
        } else if (data.isVisualFireApplied()) {
            player.setVisualFire(TriState.NOT_SET);
            data.setVisualFireApplied(false);
        }

        // 低温時: 粉雪の画面端凍結エフェクト（バニラ凍結ダメージは Listener 側でキャンセル）
        if (eval.applyColdVisual() && config.isColdFreezeOverlay()) {
            player.lockFreezeTicks(true);
            player.setFreezeTicks(config.getColdFreezeTicks());
            data.setFreezeOverlayApplied(true);
        } else if (data.isFreezeOverlayApplied()) {
            player.lockFreezeTicks(false);
            player.setFreezeTicks(0);
            data.setFreezeOverlayApplied(false);
        }
    }

    public void clearVisualEffects(Player player, PlayerTemperatureData data) {
        if (data.isVisualFireApplied()) {
            player.setVisualFire(TriState.NOT_SET);
            data.setVisualFireApplied(false);
        }
        if (data.isFreezeOverlayApplied()) {
            player.lockFreezeTicks(false);
            player.setFreezeTicks(0);
            data.setFreezeOverlayApplied(false);
        }
    }

    private boolean isExemptFromPenalties(Player player) {
        if (config.isBypass(player)) {
            return true;
        }
        if (config.isIgnoreCreativeSpectator()) {
            GameMode gm = player.getGameMode();
            return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
        }
        return false;
    }
}
