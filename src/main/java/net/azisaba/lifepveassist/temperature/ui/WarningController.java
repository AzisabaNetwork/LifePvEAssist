package net.azisaba.lifepveassist.temperature.ui;

import net.azisaba.lifepveassist.temperature.config.TemperatureConfig;
import net.azisaba.lifepveassist.temperature.model.PlayerTemperatureState;
import net.azisaba.lifepveassist.temperature.model.TemperatureEvaluation;
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

    public void update(
            Player player,
            TemperatureConfig config,
            PlayerTemperatureState state,
            TemperatureEvaluation eval,
            long nowMillis
    ) {
        if (!config.warningEnabled()) {
            return;
        }

        if (!eval.inWarningZone()) {
            state.setLastWarningMillis(0L);
            return;
        }

        long intervalMillis = config.warningIntervalSeconds() * 1000L;
        if (state.getLastWarningMillis() != 0L && (nowMillis - state.getLastWarningMillis()) < intervalMillis) {
            return;
        }

        state.setLastWarningMillis(nowMillis);

        boolean hot = eval.hotWarning();
        String rawTitle = hot ? config.heatWarningTitle() : config.coldWarningTitle();
        String rawSubtitle = hot ? config.heatWarningSubtitle() : config.coldWarningSubtitle();

        Component titleComp = MINI.deserialize(replacePlaceholders(rawTitle, eval));
        Component subtitleComp = MINI.deserialize(replacePlaceholders(rawSubtitle, eval));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(config.warningFadeInTicks() * 50L),
                Duration.ofMillis(config.warningStayTicks() * 50L),
                Duration.ofMillis(config.warningFadeOutTicks() * 50L)
        );
        player.showTitle(Title.title(titleComp, subtitleComp, times));

        String soundName = hot ? config.heatWarningSound() : config.coldWarningSound();
        float volume = hot ? config.heatWarningSoundVolume() : config.coldWarningSoundVolume();
        float pitch = hot ? config.heatWarningSoundPitch() : config.coldWarningSoundPitch();
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
        }
    }
}
