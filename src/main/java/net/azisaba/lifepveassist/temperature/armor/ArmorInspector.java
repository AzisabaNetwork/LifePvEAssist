package net.azisaba.lifepveassist.temperature.armor;

import io.papermc.paper.persistence.PersistentDataContainerView;
import net.azisaba.lifepveassist.temperature.model.ArmorResistance;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Paper 1.21+ 標準の ItemStack#getPersistentDataContainer() (minecraft:custom_data コンポーネント内
 * PublicBukkitValues) のみを使用して専用防具の耐熱・耐冷耐性を解析・操作するクラス。
 * 1アイテム＝常に1部位固定、値が 0 より大きいもののみ有効、複合防具は両方独立カウントします。
 */
public class ArmorInspector {

    public static final String HEAT_KEY_NAME = "temp_heat";
    public static final String COLD_KEY_NAME = "temp_cold";

    private final NamespacedKey heatKey;
    private final NamespacedKey coldKey;

    public ArmorInspector(Plugin plugin) {
        this.heatKey = new NamespacedKey(plugin, HEAT_KEY_NAME);
        this.coldKey = new NamespacedKey(plugin, COLD_KEY_NAME);
    }

    /**
     * プレイヤーが現在装備している防具4部位から耐熱・耐冷耐性を集計します。
     */
    public ArmorResistance inspectPlayer(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        int heatPieces = 0;
        double heatBonus = 0.0;
        int coldPieces = 0;
        double coldBonus = 0.0;

        for (ItemStack item : armorContents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ArmorResistance r = inspectItem(item);
            heatPieces += r.heatPieces();
            heatBonus += r.heatLimitBonus();
            coldPieces += r.coldPieces();
            coldBonus += r.coldLimitBonus();
        }
        return new ArmorResistance(heatPieces, heatBonus, coldPieces, coldBonus);
    }

    /**
     * 単一アイテムの PDC コンポーネントから耐性を読み取ります。
     * 1アイテム＝常に1部位固定。Heat と Cold が両方 > 0 の場合は複合耐性防具として両方有効になります。
     */
    public ArmorResistance inspectItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ArmorResistance.NONE;
        }
        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        double heat = readPositiveNumber(pdc, heatKey);
        double cold = readPositiveNumber(pdc, coldKey);

        int hp = heat > 0.0 ? 1 : 0;
        int cp = cold > 0.0 ? 1 : 0;
        if (hp == 0 && cp == 0) {
            return ArmorResistance.NONE;
        }
        return new ArmorResistance(hp, heat, cp, cold);
    }

    /**
     * アイテムに PDC コンポーネントとして耐熱または耐冷の延長値を書き込みます。
     */
    public boolean applyResistance(ItemStack item, boolean heat, double limitBonus) {
        if (item == null || item.getType().isAir() || !Double.isFinite(limitBonus) || limitBonus <= 0.0) {
            return false;
        }
        NamespacedKey targetKey = heat ? heatKey : coldKey;
        return item.editPersistentDataContainer(pdc ->
                pdc.set(targetKey, PersistentDataType.DOUBLE, limitBonus)
        );
    }

    /**
     * アイテムから温度耐性の PDC キーを削除します。
     */
    public boolean clearResistance(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return item.editPersistentDataContainer(pdc -> {
            pdc.remove(heatKey);
            pdc.remove(coldKey);
        });
    }

    /**
     * MythicMobs YAML で整数 (50)、小数 (50.0)、または文字列 ("50") のいずれで記述されていても
     * 例外を出さずに読み取れるよう、必ず {@code pdc.has(key, type)} で型を確認してから取得し、
     * 0 より大きい有限値のみを返します。
     */
    private static double readPositiveNumber(PersistentDataContainerView pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            Double d = pdc.get(key, PersistentDataType.DOUBLE);
            return (d != null && Double.isFinite(d) && d > 0.0) ? d : 0.0;
        }
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            Integer i = pdc.get(key, PersistentDataType.INTEGER);
            return (i != null && i > 0) ? i.doubleValue() : 0.0;
        }
        if (pdc.has(key, PersistentDataType.FLOAT)) {
            Float f = pdc.get(key, PersistentDataType.FLOAT);
            return (f != null && Float.isFinite(f) && f > 0.0f) ? f.doubleValue() : 0.0;
        }
        if (pdc.has(key, PersistentDataType.LONG)) {
            Long l = pdc.get(key, PersistentDataType.LONG);
            return (l != null && l > 0L) ? l.doubleValue() : 0.0;
        }
        if (pdc.has(key, PersistentDataType.SHORT)) {
            Short s = pdc.get(key, PersistentDataType.SHORT);
            return (s != null && s > 0) ? s.doubleValue() : 0.0;
        }
        if (pdc.has(key, PersistentDataType.BYTE)) {
            Byte b = pdc.get(key, PersistentDataType.BYTE);
            return (b != null && b > 0) ? b.doubleValue() : 0.0;
        }
        if (pdc.has(key, PersistentDataType.STRING)) {
            String str = pdc.get(key, PersistentDataType.STRING);
            if (str != null && !str.isBlank()) {
                try {
                    double parsed = Double.parseDouble(str.trim());
                    return (Double.isFinite(parsed) && parsed > 0.0) ? parsed : 0.0;
                } catch (NumberFormatException ignored) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }
}
