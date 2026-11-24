package net.azisaba.lifepveassist.temp.ui;

import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.model.ArmorResistance;
import net.azisaba.lifepveassist.temp.model.TemperatureCalculator;
import net.azisaba.lifepveassist.temp.model.TemperatureEvaluation;
import net.azisaba.lifepveassist.temp.model.TemperatureSeverity;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとの BossBar を管理し、1秒ごとの温度表示を更新するコントローラ。
 */
public class BossBarController {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final DecimalFormat TEMP_FORMAT = new DecimalFormat("0.#");

    private final ConfigManager config;
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    public BossBarController(ConfigManager config) {
        this.config = config;
    }

    public void update(Player player, TemperatureEvaluation eval, ArmorResistance resistance) {
        if (!config.isBossBarEnabled()) {
            hide(player);
            return;
        }

        if (config.isBossBarHideWhenNormal()
                && eval.severity() == TemperatureSeverity.NORMAL
                && Math.abs(eval.temperature() - config.getDefaultTemp()) < 0.05) {
            hide(player);
            return;
        }

        Component title = buildTitle(eval, resistance);
        float progress = TemperatureCalculator.computeBossBarProgress(
                eval.temperature(), eval.minLimit(), eval.maxLimit());
        BossBar.Color color = resolveColor(eval.severity());
        BossBar.Overlay overlay = config.getBossBarOverlay();

        BossBar bar = activeBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, color, overlay);
            activeBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
            bar.overlay(overlay);
        }
    }

    public void hide(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void clearAll() {
        for (Map.Entry<UUID, BossBar> entry : activeBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        activeBars.clear();
    }

    private Component buildTitle(TemperatureEvaluation eval, ArmorResistance resistance) {
        String status = switch (eval.severity()) {
            case HOT_STAGE1 -> config.getStatusHotStage1();
            case HOT_STAGE2, HOT_LETHAL -> config.getStatusHotStage2();
            case COLD_STAGE1 -> config.getStatusColdStage1();
            case COLD_STAGE2, COLD_LETHAL -> config.getStatusColdStage2();
            case NORMAL -> config.getStatusNormal();
        };

        String tempColor = switch (eval.severity()) {
            case HOT_STAGE1 -> "<yellow>";
            case HOT_STAGE2, HOT_LETHAL -> "<red>";
            case COLD_STAGE1 -> "<aqua>";
            case COLD_STAGE2, COLD_LETHAL -> "<blue>";
            case NORMAL -> "<green>";
        };

        String formatted = config.getBossBarTitleFormat()
                .replace("<status>", status)
                .replace("<temp_color>", tempColor)
                .replace("<temp>", formatTemp(eval.temperature()))
                .replace("<min>", formatTemp(eval.minLimit()))
                .replace("<max>", formatTemp(eval.maxLimit()))
                .replace("<heat_pieces>", String.valueOf(resistance.heatPieces()))
                .replace("<cold_pieces>", String.valueOf(resistance.coldPieces()));

        return MINI.deserialize(formatted);
    }

    private BossBar.Color resolveColor(TemperatureSeverity severity) {
        return switch (severity) {
            case HOT_STAGE1 -> BossBar.Color.YELLOW;
            case HOT_STAGE2, HOT_LETHAL -> BossBar.Color.RED;
            case COLD_STAGE1 -> BossBar.Color.BLUE;
            case COLD_STAGE2, COLD_LETHAL -> BossBar.Color.PURPLE;
            case NORMAL -> BossBar.Color.GREEN;
        };
    }

    public static String formatTemp(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == 0.0) {
            rounded = 0.0; // -0.0 を防止
        }
        return TEMP_FORMAT.format(rounded);
    }
}
