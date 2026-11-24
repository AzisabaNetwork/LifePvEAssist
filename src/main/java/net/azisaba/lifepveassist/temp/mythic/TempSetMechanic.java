package net.azisaba.lifepveassist.temp.mythic;

import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.placeholders.PlaceholderDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * MythicMobs Mechanic: tempset{v=20} @self / @target
 * プレイヤーの現在温度を指定値に設定し、自然回復カウントをリセットします。
 */
public class TempSetMechanic implements ITargetedEntitySkill {

    private final Plugin plugin;
    private final TemperatureManager temperatureManager;
    private final PlaceholderDouble targetValue;
    private final boolean resetTimer;

    public TempSetMechanic(Plugin plugin, TemperatureManager temperatureManager, MythicLineConfig config) {
        this.plugin = plugin;
        this.temperatureManager = temperatureManager;
        this.targetValue = config.getPlaceholderDouble(new String[]{"value", "v", "amount", "a"}, 20.0);
        this.resetTimer = config.getBoolean(new String[]{"resettimer", "rt"}, true);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (target == null || !target.isPlayer()) {
            return SkillResult.INVALID_TARGET;
        }
        if (!(target.getBukkitEntity() instanceof Player player)) {
            return SkillResult.INVALID_TARGET;
        }

        double value = targetValue.get(data, target);
        if (!Double.isFinite(value)) {
            return SkillResult.CONDITION_FAILED;
        }

        if (Bukkit.isPrimaryThread()) {
            temperatureManager.setTemperature(player, value, resetTimer);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    temperatureManager.setTemperature(player, value, resetTimer);
                }
            });
        }
        return SkillResult.SUCCESS;
    }
}
