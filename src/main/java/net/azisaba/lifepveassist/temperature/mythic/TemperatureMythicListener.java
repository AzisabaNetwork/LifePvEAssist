package net.azisaba.lifepveassist.temperature.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.api.skills.placeholders.PlaceholderDouble;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.utils.numbers.RangedDouble;
import net.azisaba.lifepveassist.temperature.core.TemperatureService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * MythicMobs 5.12+ / 5.14+ 向けの温度 Mechanic / Condition 登録リスナー。
 * 対象外ワールドでは Mechanic 無効、Condition は常に false を返します。
 */
public class TemperatureMythicListener implements Listener {

    private final Plugin plugin;
    private final TemperatureService service;

    public TemperatureMythicListener(Plugin plugin, TemperatureService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String name = event.getMechanicName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "tempadd", "addtemp", "temperatureadd" ->
                    event.register(new TempAddMechanic(plugin, service, event.getConfig()));
            case "tempset", "settemp", "temperatureset" ->
                    event.register(new TempSetMechanic(plugin, service, event.getConfig()));
            case "tempreset", "resettemp", "temperaturereset" ->
                    event.register(new TempResetMechanic(plugin, service));
            default -> {
            }
        }
    }

    @EventHandler
    public void onConditionLoad(MythicConditionLoadEvent event) {
        String name = event.getConditionName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "tempinrange", "temperatureinrange", "temprange" ->
                    event.register(new TempInRangeCondition(service, event.getConfig(), event.getArgument()));
            default -> {
            }
        }
    }

    private static final class TempAddMechanic implements ITargetedEntitySkill {
        private final Plugin plugin;
        private final TemperatureService service;
        private final PlaceholderDouble amount;
        private final boolean resetTimer;

        TempAddMechanic(Plugin plugin, TemperatureService service, MythicLineConfig cfg) {
            this.plugin = plugin;
            this.service = service;
            this.amount = cfg.getPlaceholderDouble(new String[]{"value", "v", "amount", "a"}, 0.0);
            this.resetTimer = cfg.getBoolean(new String[]{"resettimer", "rt"}, true);
        }

        @Override
        @SuppressWarnings("deprecation")
        public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
            if (target == null || !target.isPlayer() || !(target.getBukkitEntity() instanceof Player player)) {
                return SkillResult.INVALID_TARGET;
            }
            double delta = amount.get(data, target);
            if (!Double.isFinite(delta)) {
                return SkillResult.CONDITION_FAILED;
            }
            runOnMain(plugin, player, () -> service.addTemperatureFromMythic(player, delta, resetTimer));
            return SkillResult.SUCCESS;
        }
    }

    private static final class TempSetMechanic implements ITargetedEntitySkill {
        private final Plugin plugin;
        private final TemperatureService service;
        private final PlaceholderDouble targetValue;
        private final boolean resetTimer;

        TempSetMechanic(Plugin plugin, TemperatureService service, MythicLineConfig cfg) {
            this.plugin = plugin;
            this.service = service;
            this.targetValue = cfg.getPlaceholderDouble(new String[]{"value", "v", "amount", "a"}, 20.0);
            this.resetTimer = cfg.getBoolean(new String[]{"resettimer", "rt"}, true);
        }

        @Override
        @SuppressWarnings("deprecation")
        public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
            if (target == null || !target.isPlayer() || !(target.getBukkitEntity() instanceof Player player)) {
                return SkillResult.INVALID_TARGET;
            }
            double value = targetValue.get(data, target);
            if (!Double.isFinite(value)) {
                return SkillResult.CONDITION_FAILED;
            }
            runOnMain(plugin, player, () -> service.setTemperatureFromMythic(player, value, resetTimer));
            return SkillResult.SUCCESS;
        }
    }

    private static final class TempResetMechanic implements ITargetedEntitySkill {
        private final Plugin plugin;
        private final TemperatureService service;

        TempResetMechanic(Plugin plugin, TemperatureService service) {
            this.plugin = plugin;
            this.service = service;
        }

        @Override
        public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
            if (target == null || !target.isPlayer() || !(target.getBukkitEntity() instanceof Player player)) {
                return SkillResult.INVALID_TARGET;
            }
            runOnMain(plugin, player, () -> service.resetTemperatureFromMythic(player));
            return SkillResult.SUCCESS;
        }
    }

    private static final class TempInRangeCondition implements IEntityCondition {
        private final TemperatureService service;
        private final PlaceholderString dynamicRange;
        private final RangedDouble staticRange;

        TempInRangeCondition(TemperatureService service, MythicLineConfig cfg, String fallbackArgument) {
            this.service = service;
            String raw = cfg.getString(new String[]{"value", "v", "range", "r"}, null);
            if ((raw == null || raw.isBlank()) && fallbackArgument != null && !fallbackArgument.isBlank()) {
                raw = fallbackArgument.trim();
            }
            if (raw == null || raw.isBlank()) {
                raw = "20";
            }
            if (raw.contains("<") && raw.contains(">") && raw.indexOf('<') < raw.lastIndexOf('>')) {
                this.dynamicRange = PlaceholderString.of(raw);
                this.staticRange = null;
            } else {
                this.dynamicRange = null;
                this.staticRange = parseRangeSafely(raw);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean check(AbstractEntity entity) {
            if (entity == null || !entity.isPlayer() || !(entity.getBukkitEntity() instanceof Player player)) {
                return false;
            }
            if (!service.getConfig().isEnabledWorld(player.getWorld())) {
                return false;
            }
            RangedDouble range = (staticRange != null)
                    ? staticRange
                    : parseRangeSafely(dynamicRange.get(entity));
            if (range == null) {
                return false;
            }
            return range.equals(service.getTemperature(player));
        }

        private static RangedDouble parseRangeSafely(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return new RangedDouble(raw.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void runOnMain(Plugin plugin, Player player, Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    action.run();
                }
            });
        }
    }
}
