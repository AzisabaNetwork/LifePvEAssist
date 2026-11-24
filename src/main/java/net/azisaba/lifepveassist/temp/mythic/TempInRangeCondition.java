package net.azisaba.lifepveassist.temp.mythic;

import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import io.lumine.mythic.bukkit.utils.numbers.RangedDouble;
import org.bukkit.entity.Player;

/**
 * MythicMobs Condition: tempinrange{v=>120}
 * プレイヤーの現在温度が指定された範囲条件に一致するかを判定します。
 * RangedDouble により >120, >=120, <-20, <=-20, 80to120, -20to80, 20 などの記法に対応します。
 */
public class TempInRangeCondition implements IEntityCondition {

    private final TemperatureManager temperatureManager;
    private final PlaceholderString dynamicRange;
    private final RangedDouble staticRange;

    public TempInRangeCondition(TemperatureManager temperatureManager, MythicLineConfig config, String fallbackArgument) {
        this.temperatureManager = temperatureManager;

        String raw = config.getString(new String[]{"value", "v", "range", "r"}, null);
        if ((raw == null || raw.isBlank()) && fallbackArgument != null && !fallbackArgument.isBlank()) {
            raw = fallbackArgument.trim();
        }
        if (raw == null || raw.isBlank()) {
            raw = "20";
        }

        if (raw.contains("<") && raw.contains(">") && raw.indexOf('<') < raw.lastIndexOf('>')) {
            // プレースホルダーを含む可能性が高い場合は評価時に展開する
            this.dynamicRange = PlaceholderString.of(raw);
            this.staticRange = null;
        } else {
            this.dynamicRange = null;
            this.staticRange = parseRangeSafely(raw);
        }
    }

    @Override
    public boolean check(AbstractEntity entity) {
        if (entity == null || !entity.isPlayer()) {
            return false;
        }
        if (!(entity.getBukkitEntity() instanceof Player player)) {
            return false;
        }

        RangedDouble range = (staticRange != null)
                ? staticRange
                : parseRangeSafely(dynamicRange.get(entity));
        if (range == null) {
            return false;
        }

        double temp = temperatureManager.getTemperature(player);
        return range.equals(temp);
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
