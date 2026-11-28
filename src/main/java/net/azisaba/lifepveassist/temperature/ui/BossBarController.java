package net.azisaba.lifepveassist.temperature.ui;

import net.azisaba.lifepveassist.temperature.config.TemperatureConfig;
import net.azisaba.lifepveassist.temperature.model.ArmorResistance;
import net.azisaba.lifepveassist.temperature.model.TemperatureCalculator;
import net.azisaba.lifepveassist.temperature.model.TemperatureEvaluation;
import net.azisaba.lifepveassist.temperature.model.TemperatureSeverity;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとの BossBar を管理し、リアルタイム温度表示を更新するコントローラ。
 */
public class BossBarController {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final DecimalFormat TEMP_FORMAT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    public void update(Player player, TemperatureConfig config, TemperatureEvaluation eval, ArmorResistance resistance) {
        if (!config.bossBarEnabled()) {
            hide(player);
            return;
        }

        if (config.bossBarHideWhenNormal()
                && eval.severity() == TemperatureSeverity.NORMAL
                && Math.abs(eval.temperature() - config.defaultTemp()) < 0.05) {
            hide(player);
            return;
        }

        Component title = buildTitle(config, eval, resistance);
        float progress = TemperatureCalculator.computeBossBarProgress(
                eval.temperature(), eval.minLimit(), eval.maxLimit());
        BossBar.Color color = resolveColor(eval.severity());
        BossBar.Overlay overlay = config.bossBarOverlay();

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

    private Component buildTitle(TemperatureConfig config, TemperatureEvaluation eval, ArmorResistance resistance) {
        String status = switch (eval.severity()) {
            case HOT_STAGE1 -> config.statusHotStage1();
            case HOT_STAGE2, HOT_LETHAL -> config.statusHotStage2();
            case COLD_STAGE1 -> config.statusColdStage1();
            case COLD_STAGE2, COLD_LETHAL -> config.statusColdStage2();
            case NORMAL -> config.statusNormal();
        };

        String tempColor = switch (eval.severity()) {
            case HOT_STAGE1 -> "<yellow>";
            case HOT_STAGE2, HOT_LETHAL -> "<red>";
            case COLD_STAGE1 -> "<aqua>";
            case COLD_STAGE2, COLD_LETHAL -> "<blue>";
            case NORMAL -> "<green>";
        };

        String formatted = config.bossBarTitleFormat()
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
            rounded = 0.0;
        }
        return TEMP_FORMAT.format(rounded);
    }
}
