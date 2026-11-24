package net.azisaba.lifepveassist.temp.mythic;

import net.azisaba.lifepveassist.temp.armor.ArmorInspector;
import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.utils.jnbt.CompoundTag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * MythicMobs 5.14+ との連携を担うフッククラス。
 * カスタム Mechanic / Condition の登録と、MythicItem の NBT / ID 読み取りを提供します。
 */
public class MythicMobsHook implements Listener, ArmorInspector.ExternalItemReader {

    private final Plugin plugin;
    private final ConfigManager config;
    private final TemperatureManager temperatureManager;

    public MythicMobsHook(Plugin plugin, ConfigManager config, TemperatureManager temperatureManager) {
        this.plugin = plugin;
        this.config = config;
        this.temperatureManager = temperatureManager;
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String name = event.getMechanicName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "tempadd", "addtemp", "temperatureadd" ->
                    event.register(new TempAddMechanic(plugin, temperatureManager, event.getConfig()));
            case "tempset", "settemp", "temperatureset" ->
                    event.register(new TempSetMechanic(plugin, temperatureManager, event.getConfig()));
            case "tempreset", "resettemp", "temperaturereset" ->
                    event.register(new TempResetMechanic(plugin, temperatureManager));
            default -> {
            }
        }
    }

    @EventHandler
    public void onConditionLoad(MythicConditionLoadEvent event) {
        String name = event.getConditionName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "tempinrange", "temperatureinrange", "temprange" ->
                    event.register(new TempInRangeCondition(temperatureManager, event.getConfig(), event.getArgument()));
            default -> {
            }
        }
    }

    @Override
    public Double readNumericNbt(ItemStack item, String key) {
        if (item == null || item.getType().isAir() || key == null || key.isBlank()) {
            return null;
        }
        try {
            MythicBukkit mythic = MythicBukkit.inst();
            if (mythic == null || mythic.getVolatileCodeHandler() == null) {
                return null;
            }
            CompoundTag tag = mythic.getVolatileCodeHandler().getItemHandler().getNBTData(item);
            if (tag == null || !tag.containsKey(key)) {
                return null;
            }

            // 数値型タグ (int, double, float, short, long, byte)
            double numeric = tag.asDouble(key);
            if (Double.isFinite(numeric) && numeric != 0.0) {
                return numeric;
            }

            // boolean true で指定された場合は基本延長値 (デフォルト50.0) を返す
            if (tag.getBoolean(key)) {
                return config.getDefaultLimitBonusPerPiece();
            }

            // 文字列で書かれていた場合 ("50" 等)
            String str = tag.getString(key);
            if (str != null && !str.isBlank()) {
                if ("true".equalsIgnoreCase(str.trim())) {
                    return config.getDefaultLimitBonusPerPiece();
                }
                return Double.parseDouble(str.trim());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    @Override
    public String getMythicItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        try {
            MythicBukkit mythic = MythicBukkit.inst();
            if (mythic == null || mythic.getItemManager() == null) {
                return null;
            }
            return mythic.getItemManager().getMythicTypeFromItem(item);
        } catch (Exception ignored) {
            return null;
        }
    }
}
