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
 * MythicMobs Mechanic: tempadd{v=20} @self / @target
 * プレイヤーの現在温度を加算・減算し、自然回復カウントをリセットします。
 */
public class TempAddMechanic implements ITargetedEntitySkill {

    private final Plugin plugin;
    private final TemperatureManager temperatureManager;
    private final PlaceholderDouble amount;
    private final boolean resetTimer;

    public TempAddMechanic(Plugin plugin, TemperatureManager temperatureManager, MythicLineConfig config) {
        this.plugin = plugin;
        this.temperatureManager = temperatureManager;
        this.amount = config.getPlaceholderDouble(new String[]{"value", "v", "amount", "a"}, 0.0);
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

        double delta = amount.get(data, target);
        if (!Double.isFinite(delta)) {
            return SkillResult.CONDITION_FAILED;
        }

        if (Bukkit.isPrimaryThread()) {
            temperatureManager.addTemperature(player, delta, resetTimer);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    temperatureManager.addTemperature(player, delta, resetTimer);
                }
            });
        }
        return SkillResult.SUCCESS;
    }
}
