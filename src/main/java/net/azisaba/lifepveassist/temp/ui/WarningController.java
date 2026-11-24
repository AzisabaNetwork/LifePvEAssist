package net.azisaba.lifepveassist.temp.ui;

import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.model.PlayerTemperatureData;
import net.azisaba.lifepveassist.temp.model.TemperatureEvaluation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * 即死ライン前の警告 Title・サウンドを config 指定間隔で表示するコントローラ。
 */
public class WarningController {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ConfigManager config;

    public WarningController(ConfigManager config) {
        this.config = config;
    }

    public void update(Player player, PlayerTemperatureData data, TemperatureEvaluation eval, long nowMillis) {
        if (!config.isWarningEnabled()) {
            return;
        }

        if (!eval.inWarningZone()) {
            // 危険域を抜けたらタイマーをリセットし、再突入時に即時警告が出るようにする
            data.setLastWarningMillis(0L);
            return;
        }

        long intervalMillis = config.getWarningIntervalSeconds() * 1000L;
        if (data.getLastWarningMillis() != 0L && (nowMillis - data.getLastWarningMillis()) < intervalMillis) {
            return;
        }

        data.setLastWarningMillis(nowMillis);

        boolean hot = eval.hotWarning();
        String rawTitle = hot ? config.getHeatWarningTitle() : config.getColdWarningTitle();
        String rawSubtitle = hot ? config.getHeatWarningSubtitle() : config.getColdWarningSubtitle();

        Component titleComp = MINI.deserialize(replacePlaceholders(rawTitle, eval));
        Component subtitleComp = MINI.deserialize(replacePlaceholders(rawSubtitle, eval));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(config.getWarningFadeInTicks() * 50L),
                Duration.ofMillis(config.getWarningStayTicks() * 50L),
                Duration.ofMillis(config.getWarningFadeOutTicks() * 50L)
        );
        player.showTitle(Title.title(titleComp, subtitleComp, times));

        String soundName = hot ? config.getHeatWarningSound() : config.getColdWarningSound();
        float volume = hot ? config.getHeatWarningSoundVolume() : config.getColdWarningSoundVolume();
        float pitch = hot ? config.getHeatWarningSoundPitch() : config.getColdWarningSoundPitch();
        playSoundSafely(player, soundName, volume, pitch);
    }

    private String replacePlaceholders(String template, TemperatureEvaluation eval) {
        return template
                .replace("<temp>", BossBarController.formatTemp(eval.temperature()))
                .replace("<min>", BossBarController.formatTemp(eval.minLimit()))
                .replace("<max>", BossBarController.formatTemp(eval.maxLimit()));
    }

    private void playSoundSafely(Player player, String soundKey, float volume, float pitch) {
        if (soundKey == null || soundKey.isBlank()) {
            return;
        }
        try {
            Key key = Key.key(soundKey.trim());
            player.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
        } catch (Exception ignored) {
            // 無効なサウンドキー設定でも処理を止めない
        }
    }
}
