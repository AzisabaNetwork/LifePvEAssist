package net.azisaba.lifepveassist.temp.mythic;

import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * MythicMobs Mechanic: tempreset @self / @target
 * プレイヤーの現在温度をデフォルト温度（20℃）へリセットします。
 */
public class TempResetMechanic implements ITargetedEntitySkill {

    private final Plugin plugin;
    private final TemperatureManager temperatureManager;

    public TempResetMechanic(Plugin plugin, TemperatureManager temperatureManager) {
        this.plugin = plugin;
        this.temperatureManager = temperatureManager;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (target == null || !target.isPlayer()) {
            return SkillResult.INVALID_TARGET;
        }
        if (!(target.getBukkitEntity() instanceof Player player)) {
            return SkillResult.INVALID_TARGET;
        }

        if (Bukkit.isPrimaryThread()) {
            temperatureManager.resetTemperature(player);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    temperatureManager.resetTemperature(player);
                }
            });
        }
        return SkillResult.SUCCESS;
    }
}
