package net.azisaba.lifepveassist.temperature.core;

import net.azisaba.lifepveassist.temperature.armor.ArmorInspector;
import net.azisaba.lifepveassist.temperature.config.TemperatureConfig;
import net.azisaba.lifepveassist.temperature.model.ArmorResistance;
import net.azisaba.lifepveassist.temperature.model.PlayerTemperatureState;
import net.azisaba.lifepveassist.temperature.model.TemperatureCalculator;
import net.azisaba.lifepveassist.temperature.model.TemperatureEvaluation;
import net.azisaba.lifepveassist.temperature.model.TemperatureSeverity;
import net.azisaba.lifepveassist.temperature.storage.TemperatureRepository;
import net.azisaba.lifepveassist.temperature.ui.BossBarController;
import net.azisaba.lifepveassist.temperature.ui.WarningController;
import net.azisaba.lifepveassist.temperature.zone.TemperatureZone;
import net.azisaba.lifepveassist.temperature.zone.ZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * 温度管理機能のコアサービス。
 * 毎秒 (20tick) の5ステップ固定パイプライン、Join後リトライ読込＋オフライン回復、
 * メモリ＆dirtyフラグ定期保存、免除処理、死亡処理を統括します。
 */
public class TemperatureService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final String lockOwner;
    private final ArmorInspector armorInspector;
    private final TemperatureRepository repository;
    private final ZoneManager zoneManager;
    private final BossBarController bossBarController;
    private final WarningController warningController;
    private final ExecutorService dbExecutor;

    private volatile TemperatureConfig config;
    private final Map<UUID, PlayerTemperatureState> states = new ConcurrentHashMap<>();

    private BukkitTask mainTickTask;
    private BukkitTask autoSaveTask;

    public TemperatureService(
            Plugin plugin,
            TemperatureConfig initialConfig,
            String lockOwner,
            ArmorInspector armorInspector,
            TemperatureRepository repository,
            ZoneManager zoneManager,
            BossBarController bossBarController,
            WarningController warningController,
            ExecutorService dbExecutor
    ) {
        this.plugin = plugin;
        this.config = initialConfig;
        this.lockOwner = lockOwner;
        this.armorInspector = armorInspector;
        this.repository = repository;
        this.zoneManager = zoneManager;
        this.bossBarController = bossBarController;
        this.warningController = warningController;
        this.dbExecutor = dbExecutor;
    }

    public TemperatureConfig getConfig() {
        return config;
    }

    public void reloadConfig(TemperatureConfig nextConfig) {
        this.config = nextConfig;
        restartAutoSave();
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(player);
        }
        this.mainTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllPlayers, 20L, 20L);
        restartAutoSave();
    }

    private void restartAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        long periodTicks = Math.max(10, config.saveIntervalSeconds()) * 20L;
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirtyPlayersAsync, periodTicks, periodTicks);
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
            PlayerTemperatureState state = states.get(player.getUniqueId());
            if (state != null) {
                clearVisualEffects(player, state);
                if (state.isLoaded()) {
                    snapshot.put(player.getUniqueId(), state.getTemperature());
                }
            }
        }
        for (Map.Entry<UUID, PlayerTemperatureState> entry : states.entrySet()) {
            if (entry.getValue().isLoaded()) {
                snapshot.putIfAbsent(entry.getKey(), entry.getValue().getTemperature());
            }
        }

        bossBarController.clearAll();
        zoneManager.clearAllPreviews();
        states.clear();

        if (!snapshot.isEmpty()) {
            try {
                repository.savePlayersBatch(lockOwner, snapshot, false);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Temperature] サーバー停止時の温度一括保存に失敗しました。", e);
            }
        }
    }

    /**
     * Join 後に少し待ってから DB の最新温度を読み込む（A案）。
     * 読み込み完了までは温度処理を一時保留し、失敗時はデータ破損防止のためキックします。
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        TemperatureConfig cfg = this.config;
        long now = System.currentTimeMillis();

        PlayerTemperatureState state = new PlayerTemperatureState(uuid, cfg.defaultTemp(), now);
        states.put(uuid, state);

        dbExecutor.execute(() -> {
            try {
                TemperatureRepository.LoadedPlayerRow row = repository.loadAndAcquireLock(
                        uuid,
                        lockOwner,
                        cfg.defaultTemp(),
                        cfg.joinLoadMaxWaitMillis(),
                        cfg.lockTimeoutMillis()
                );

                long loadedAt = System.currentTimeMillis();
                long elapsedSec = Math.max(0L, (loadedAt - row.previousUpdatedAtMillis()) / 1000L);
                double effectiveTemp = row.existed()
                        ? TemperatureCalculator.applyOfflineRecovery(
                                row.temperature(),
                                cfg.defaultTemp(),
                                elapsedSec,
                                cfg.recoveryDelaySeconds(),
                                cfg.recoveryRatePerSecond())
                        : cfg.defaultTemp();
                boolean recoveredOffline = row.existed() && Math.abs(effectiveTemp - row.temperature()) > 1e-6;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        // 読み込み中に切断された場合はロックを即解放
                        dbExecutor.execute(() -> {
                            try {
                                repository.savePlayerAndReleaseLock(uuid, lockOwner, effectiveTemp);
                            } catch (SQLException ignored) {
                            }
                        });
                        states.remove(uuid);
                        return;
                    }
                    PlayerTemperatureState current = states.get(uuid);
                    if (current == null) {
                        return;
                    }
                    current.setTemperature(effectiveTemp);
                    // サーバー移動時の自然回復タイマーは移動先に入った時点でリセット
                    current.setLastExternalChangeMillis(System.currentTimeMillis());
                    current.setDirty(recoveredOffline);
                    current.setLoaded(true);

                    if (this.config.isEnabledWorld(player.getWorld())) {
                        evaluateAndApply(player, current, this.config, System.currentTimeMillis(), false);
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Temperature] プレイヤー温度の DB 読み込みに失敗しました: " + uuid, e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    states.remove(uuid);
                    if (player.isOnline()) {
                        player.kick(MINI.deserialize("<red>温度データの読み込みに失敗しました。時間をおいて再接続してください。"));
                    }
                });
            }
        });
    }

    /**
     * Quit 時に最終温度（絶対値）を保存し、locked=false に解放します。
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerTemperatureState state = states.remove(uuid);
        bossBarController.hide(player);
        zoneManager.clearPreview(uuid);

        if (state != null) {
            clearVisualEffects(player, state);
            state.setBypassEnabled(false);
            if (state.isLoaded()) {
                double finalTemp = state.getTemperature();
                dbExecutor.execute(() -> {
                    try {
                        repository.savePlayerAndReleaseLock(uuid, lockOwner, finalTemp);
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "[Temperature] Quit 時の温度保存に失敗しました: " + uuid, e);
                    }
                });
            }
        }
    }

    public void onWorldChange(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        TemperatureConfig cfg = this.config;
        if (!cfg.isEnabledWorld(player.getWorld())) {
            if (state != null) {
                clearVisualEffects(player, state);
                state.resetZoneStay();
            }
            bossBarController.hide(player);
        } else if (state != null && state.isLoaded()) {
            // 対象外 → 対象ワールド: 自然回復タイマーをリセットして再開
            long now = System.currentTimeMillis();
            state.setLastExternalChangeMillis(now);
            state.resetZoneStay();
            evaluateAndApply(player, state, cfg, now, false);
        }
    }

    /**
     * PlayerDeathEvent (HIGH) から呼ばれ、温度由来死亡時のグローバル死亡メッセージ差し替えと
     * 死亡時 20℃ リセットを実行します。
     */
    public Component handlePlayerDeath(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        TemperatureConfig cfg = this.config;
        if (state == null) {
            return null;
        }

        Component customDeathMessage = null;
        if (state.isLethalPending() && state.getPendingDeathCause() != PlayerTemperatureState.DeathCause.NONE) {
            String template = (state.getPendingDeathCause() == PlayerTemperatureState.DeathCause.HEAT)
                    ? cfg.heatDeathMessage()
                    : cfg.coldDeathMessage();
            if (template != null && !template.isBlank()) {
                String formatted = template
                        .replace("<player>", player.getName())
                        .replace("<temp>", BossBarController.formatTemp(state.getPendingDeathTemperature()));
                customDeathMessage = MINI.deserialize(formatted);
            }
        }

        clearVisualEffects(player, state);
        state.clearPendingDeath();
        state.resetZoneStay();
        state.setTemperature(cfg.defaultTemp());
        state.setLastExternalChangeMillis(System.currentTimeMillis());
        state.setLastWarningMillis(0L);
        state.setLastDamageMillis(0L);
        state.setDirty(true);

        return customDeathMessage;
    }

    public boolean isLethalPending(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        return state != null && state.isLethalPending();
    }

    public boolean isFreezeOverlayManaged(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        return state != null && state.isFreezeOverlayApplied();
    }

    public boolean toggleBypass(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        boolean next = !state.isBypassEnabled();
        state.setBypassEnabled(next);
        if (next) {
            clearVisualEffects(player, state);
            state.resetZoneStay();
        }
        return next;
    }

    public boolean isBypassEnabled(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        return state != null && state.isBypassEnabled();
    }

    public boolean isExempt(Player player, PlayerTemperatureState state) {
        if (state != null && state.isBypassEnabled()) {
            return true;
        }
        GameMode gm = player.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    public double getTemperature(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        return state != null ? state.getTemperature() : config.defaultTemp();
    }

    public boolean isLoaded(Player player) {
        PlayerTemperatureState state = states.get(player.getUniqueId());
        return state != null && state.isLoaded();
    }

    /**
     * MythicMobs 経由の温度加算・減算（対象ワールド内・非免除・読み込み済みプレイヤーのみ有効）。
     */
    public void addTemperatureFromMythic(Player player, double delta, boolean resetRecoveryTimer) {
        if (!Double.isFinite(delta)) {
            return;
        }
        TemperatureConfig cfg = this.config;
        if (!cfg.isEnabledWorld(player.getWorld())) {
            return;
        }
        PlayerTemperatureState state = states.get(player.getUniqueId());
        if (state == null || !state.isLoaded() || isExempt(player, state)) {
            return;
        }
        applyDirectTemperatureChange(player, state, cfg, state.getTemperature() + delta, resetRecoveryTimer);
    }

    /**
     * MythicMobs 経由の温度設定（対象ワールド内・非免除・読み込み済みプレイヤーのみ有効）。
     */
    public void setTemperatureFromMythic(Player player, double value, boolean resetRecoveryTimer) {
        if (!Double.isFinite(value)) {
            return;
        }
        TemperatureConfig cfg = this.config;
        if (!cfg.isEnabledWorld(player.getWorld())) {
            return;
        }
        PlayerTemperatureState state = states.get(player.getUniqueId());
        if (state == null || !state.isLoaded() || isExempt(player, state)) {
            return;
        }
        applyDirectTemperatureChange(player, state, cfg, value, resetRecoveryTimer);
    }

    /**
     * MythicMobs 経由の温度リセット（対象ワールド内・非免除・読み込み済みプレイヤーのみ有効）。
     */
    public void resetTemperatureFromMythic(Player player) {
        setTemperatureFromMythic(player, config.defaultTemp(), true);
    }

    /**
     * 管理コマンド経由の温度設定（救済・デバッグ用に対象外ワールドでも実行可能）。
     */
    public boolean setTemperatureByAdmin(Player player, double value) {
        if (!Double.isFinite(value)) {
            return false;
        }
        PlayerTemperatureState state = states.get(player.getUniqueId());
        if (state == null || !state.isLoaded()) {
            return false;
        }
        TemperatureConfig cfg = this.config;
        double clamped = TemperatureCalculator.clampTemperature(value, cfg.defaultTemp());
        long now = System.currentTimeMillis();
        state.setTemperature(clamped);
        state.setLastExternalChangeMillis(now);
        state.setDirty(true);
        if (cfg.isEnabledWorld(player.getWorld())) {
            evaluateAndApply(player, state, cfg, now, false);
        }
        return true;
    }

    public boolean addTemperatureByAdmin(Player player, double delta) {
        if (!Double.isFinite(delta)) {
            return false;
        }
        PlayerTemperatureState state = states.get(player.getUniqueId());
        if (state == null || !state.isLoaded()) {
            return false;
        }
        return setTemperatureByAdmin(player, state.getTemperature() + delta);
    }

    public boolean resetTemperatureByAdmin(Player player) {
        return setTemperatureByAdmin(player, config.defaultTemp());
    }

    public TemperatureEvaluation evaluateCurrent(Player player) {
        TemperatureConfig cfg = this.config;
        double temp = getTemperature(player);
        ArmorResistance res = armorInspector.inspectPlayer(player);
        return TemperatureCalculator.evaluate(
                temp,
                cfg.defaultTemp(),
                cfg.baseMinLimit(),
                cfg.baseMaxLimit(),
                res,
                cfg.stage1Offset(),
                cfg.stage2Offset(),
                cfg.warningOffset(),
                cfg.stage1ImmunityPieces(),
                cfg.stage2DamageImmunityPieces()
        );
    }

    private void applyDirectTemperatureChange(
            Player player,
            PlayerTemperatureState state,
            TemperatureConfig cfg,
            double newTemp,
            boolean resetRecoveryTimer
    ) {
        double clamped = TemperatureCalculator.clampTemperature(newTemp, cfg.defaultTemp());
        long now = System.currentTimeMillis();
        state.setTemperature(clamped);
        if (resetRecoveryTimer) {
            state.setLastExternalChangeMillis(now);
        }
        state.setDirty(true);
        evaluateAndApply(player, state, cfg, now, false);
    }

    /**
     * 毎秒 (20tick) の5ステップ固定パイプライン。
     */
    private void tickAllPlayers() {
        long now = System.currentTimeMillis();
        TemperatureConfig cfg = this.config;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerTemperatureState state = states.get(player.getUniqueId());

            // Step 1: 有効性チェック（対象外ワールド・未読込・死亡中）
            if (!cfg.isEnabledWorld(player.getWorld())) {
                if (state != null) {
                    clearVisualEffects(player, state);
                    state.resetZoneStay();
                }
                bossBarController.hide(player);
                continue;
            }
            if (state == null || !state.isLoaded()) {
                continue;
            }
            if (player.isDead()) {
                clearVisualEffects(player, state);
                state.resetZoneStay();
                continue;
            }

            // 免除中（クリエイティブ・スペクテイター・バイパス ON）は温度変化・デメリットすべて停止し BossBar のみ維持
            if (isExempt(player, state)) {
                clearVisualEffects(player, state);
                state.resetZoneStay();
                ArmorResistance res = armorInspector.inspectPlayer(player);
                TemperatureEvaluation eval = evaluateWithConfig(state.getTemperature(), cfg, res);
                bossBarController.update(player, cfg, eval, res);
                continue;
            }

            // Step 2: Zone 判定・適用（プレイヤーごとの個別滞在秒数カウント）
            TemperatureZone activeZone = zoneManager.findStrongestZoneAt(player.getLocation());
            if (activeZone != null) {
                int stayed = state.tickZoneStay(activeZone.name().toLowerCase());
                if (stayed % activeZone.intervalSeconds() == 0) {
                    double next = TemperatureCalculator.clampTemperature(
                            state.getTemperature() + activeZone.delta(), cfg.defaultTemp());
                    state.setTemperature(next);
                    state.setLastExternalChangeMillis(now);
                    state.setDirty(true);
                }
            } else {
                // ゾーンを出た時点でタイマーリセット（ゾーン内は自然回復なし）
                if (state.getCurrentZoneKey() != null) {
                    state.resetZoneStay();
                    state.setLastExternalChangeMillis(now);
                }

                // Step 3: 自然回復（ゾーン外かつ最後の外部変化から delay 秒経過で回復。自然回復ではタイマーをリセットしない）
                long delayMillis = cfg.recoveryDelaySeconds() * 1000L;
                if ((now - state.getLastExternalChangeMillis()) >= delayMillis) {
                    double current = state.getTemperature();
                    if (Math.abs(current - cfg.defaultTemp()) > 1e-6) {
                        double next = TemperatureCalculator.stepRecovery(
                                current, cfg.defaultTemp(), cfg.recoveryRatePerSecond());
                        state.setTemperature(next);
                        state.setDirty(true);
                    }
                }
            }

            // Step 4 & 5: 防具再集計 → 評価 & 適用
            evaluateAndApply(player, state, cfg, now, true);
        }
    }

    private void evaluateAndApply(
            Player player,
            PlayerTemperatureState state,
            TemperatureConfig cfg,
            long now,
            boolean allowPeriodicDamage
    ) {
        if (player.isDead() || !player.isOnline()) {
            return;
        }

        // Step 4: 防具耐性の再集計
        ArmorResistance resistance = armorInspector.inspectPlayer(player).capped(cfg.armorMaxBonus());
        TemperatureEvaluation eval = evaluateWithConfig(state.getTemperature(), cfg, resistance);

        // BossBar 更新
        bossBarController.update(player, cfg, eval, resistance);

        if (isExempt(player, state)) {
            clearVisualEffects(player, state);
            return;
        }

        // Step 5-1: 即死判定（二重実行防止 + トーテム無効化）
        if (eval.triggerLethal()) {
            if (!state.isLethalPending()) {
                PlayerTemperatureState.DeathCause cause = (eval.severity() == TemperatureSeverity.HOT_LETHAL)
                        ? PlayerTemperatureState.DeathCause.HEAT
                        : PlayerTemperatureState.DeathCause.COLD;
                executeTemperatureKill(player, state, cause, eval.temperature());
            }
            return;
        }

        // Step 5-2: 第1段階（鈍足・盲目）
        if (eval.applySlownessBlindness()) {
            int duration = cfg.effectDurationTicks();
            if (cfg.slownessEnabled()) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, cfg.slownessAmplifier(), true, false, true));
            }
            if (cfg.blindnessEnabled()) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, duration, cfg.blindnessAmplifier(), true, false, true));
            }
        }

        // Step 5-3: 第2段階（継続ダメージ）
        if (allowPeriodicDamage && eval.applyContinuousDamage() && cfg.damageEnabled() && cfg.damageAmount() > 0.0) {
            long dmgIntervalMillis = cfg.damageIntervalSeconds() * 1000L;
            if ((now - state.getLastDamageMillis()) >= dmgIntervalMillis) {
                state.setLastDamageMillis(now);
                PlayerTemperatureState.DeathCause cause = eval.severity().isHot()
                        ? PlayerTemperatureState.DeathCause.HEAT
                        : PlayerTemperatureState.DeathCause.COLD;
                applyContinuousDamage(player, state, cfg.damageAmount(), cfg.damageBypassArmor(), cause, eval.temperature());
                if (player.isDead()) {
                    return;
                }
            }
        }

        // Step 5-4: 視界エフェクト（4部位でも維持）
        updateVisualEffects(player, state, cfg, eval);

        // Step 5-5: 警告 Title
        warningController.update(player, cfg, state, eval, now);
    }

    private void executeTemperatureKill(
            Player player,
            PlayerTemperatureState state,
            PlayerTemperatureState.DeathCause cause,
            double deathTemp
    ) {
        clearVisualEffects(player, state);
        state.setLethalPending(true);
        state.setPendingDeathCause(cause);
        state.setPendingDeathTemperature(deathTemp);
        try {
            player.setHealth(0.0);
        } finally {
            // PlayerDeathEvent で処理されなかった場合でもフラグが残留しないよう確実にクリア
            state.clearPendingDeath();
        }
    }

    /**
     * 継続ダメージを適用します。
     * bypassArmor=true の場合は衝撃吸収ハートを先に消費し、残余を HP から直接減算します。
     * 継続ダメージによって HP が 0 になった場合も温度由来のカスタム死亡メッセージを出力します。
     */
    private void applyContinuousDamage(
            Player player,
            PlayerTemperatureState state,
            double amount,
            boolean bypassArmor,
            PlayerTemperatureState.DeathCause cause,
            double currentTemp
    ) {
        if (player.isDead() || player.getHealth() <= 0.0) {
            return;
        }
        if (bypassArmor) {
            double remaining = amount;
            double absorption = player.getAbsorptionAmount();
            if (absorption > 0.0) {
                double absorbed = Math.min(absorption, remaining);
                player.setAbsorptionAmount(absorption - absorbed);
                remaining -= absorbed;
            }
            if (remaining <= 0.0) {
                return;
            }
            double nextHealth = Math.max(0.0, player.getHealth() - remaining);
            if (nextHealth <= 0.0) {
                executeTemperatureKill(player, state, cause, currentTemp);
            } else {
                player.setHealth(nextHealth);
            }
        } else {
            state.setLethalPending(true);
            state.setPendingDeathCause(cause);
            state.setPendingDeathTemperature(currentTemp);
            try {
                player.damage(amount);
            } finally {
                state.clearPendingDeath();
            }
        }
    }

    private void updateVisualEffects(
            Player player,
            PlayerTemperatureState state,
            TemperatureConfig cfg,
            TemperatureEvaluation eval
    ) {
        if (eval.applyHeatVisual() && cfg.heatFireOverlay()) {
            org.bukkit.WorldBorder realBorder = player.getWorld().getWorldBorder();
            if (!state.isVisualFireApplied()) {
                state.setPreviousWorldBorder(player.getWorldBorder());
                org.bukkit.WorldBorder redBorder = Bukkit.createWorldBorder();
                redBorder.setCenter(realBorder.getCenter());
                redBorder.setSize(realBorder.getSize());
                redBorder.setDamageAmount(0.0);
                redBorder.setDamageBuffer(0.0);
                redBorder.setWarningDistance(Integer.MAX_VALUE);
                player.setWorldBorder(redBorder);
                state.setVisualFireApplied(true);
            } else {
                org.bukkit.WorldBorder active = player.getWorldBorder();
                if (active != null) {
                    active.setCenter(realBorder.getCenter());
                    active.setSize(realBorder.getSize());
                }
            }
        } else if (state.isVisualFireApplied()) {
            player.setWorldBorder(state.getPreviousWorldBorder());
            state.setPreviousWorldBorder(null);
            state.setVisualFireApplied(false);
        }

        if (eval.applyColdVisual() && cfg.coldFreezeOverlay()) {
            player.lockFreezeTicks(true);
            player.setFreezeTicks(cfg.coldFreezeTicks());
            state.setFreezeOverlayApplied(true);
        } else if (state.isFreezeOverlayApplied()) {
            player.lockFreezeTicks(false);
            player.setFreezeTicks(0);
            state.setFreezeOverlayApplied(false);
        }
    }

    private void clearVisualEffects(Player player, PlayerTemperatureState state) {
        if (state.isVisualFireApplied()) {
            player.setWorldBorder(state.getPreviousWorldBorder());
            state.setPreviousWorldBorder(null);
            state.setVisualFireApplied(false);
        }
        if (state.isFreezeOverlayApplied()) {
            player.lockFreezeTicks(false);
            player.setFreezeTicks(0);
            state.setFreezeOverlayApplied(false);
        }
    }

    private TemperatureEvaluation evaluateWithConfig(double temp, TemperatureConfig cfg, ArmorResistance resistance) {
        return TemperatureCalculator.evaluate(
                temp,
                cfg.defaultTemp(),
                cfg.baseMinLimit(),
                cfg.baseMaxLimit(),
                resistance,
                cfg.stage1Offset(),
                cfg.stage2Offset(),
                cfg.warningOffset(),
                cfg.stage1ImmunityPieces(),
                cfg.stage2DamageImmunityPieces()
        );
    }

    /**
     * dirty フラグが立っているプレイヤーのみを非同期バッチで DB へ定期保存します。
     * 保存失敗時は dirty フラグを戻し、次回保存時にリトライします。
     */
    private void flushDirtyPlayersAsync() {
        Map<UUID, Double> dirtySnapshot = new HashMap<>();
        java.util.List<UUID> loadedUuids = new java.util.ArrayList<>();
        for (Map.Entry<UUID, PlayerTemperatureState> entry : states.entrySet()) {
            PlayerTemperatureState state = entry.getValue();
            if (state.isLoaded()) {
                loadedUuids.add(entry.getKey());
                if (state.isDirty()) {
                    dirtySnapshot.put(entry.getKey(), state.getTemperature());
                    state.setDirty(false);
                }
            }
        }
        if (dirtySnapshot.isEmpty() && loadedUuids.isEmpty()) {
            return;
        }
        dbExecutor.execute(() -> {
            if (!dirtySnapshot.isEmpty()) {
                try {
                    repository.savePlayersBatch(lockOwner, dirtySnapshot, true);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "[Temperature] 定期オートセーブに失敗しました。次回リトライします。", e);
                    for (UUID uuid : dirtySnapshot.keySet()) {
                        PlayerTemperatureState s = states.get(uuid);
                        if (s != null) {
                            s.setDirty(true);
                        }
                    }
                }
            }
            java.util.List<UUID> renewOnly = loadedUuids.stream()
                    .filter(u -> !dirtySnapshot.containsKey(u))
                    .toList();
            if (!renewOnly.isEmpty()) {
                try {
                    repository.renewLocksBatch(lockOwner, renewOnly);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "[Temperature] ロックハートビート更新に失敗しました。", e);
                }
            }
        });
    }
}
