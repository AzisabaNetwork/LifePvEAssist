package net.azisaba.lifepveassist.temp.armor;

import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.model.ArmorResistance;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * プレイヤーの装備防具から耐熱・耐冷耐性（PDC / MythicMobs NBT / Config定義）を読み取るクラス。
 */
public class ArmorInspector {

    /**
     * MythicMobs 依存を切り離すための NBT / MythicItemID 読み取りアダプター。
     */
    public interface ExternalItemReader {
        Double readNumericNbt(ItemStack item, String key);
        String getMythicItemId(ItemStack item);
    }

    public record ItemInspectionResult(ArmorResistance resistance, String source) {
    }

    private final ConfigManager config;
    private final NamespacedKey heatBonusKey;
    private final NamespacedKey heatPiecesKey;
    private final NamespacedKey coldBonusKey;
    private final NamespacedKey coldPiecesKey;

    private ExternalItemReader externalReader;

    public ArmorInspector(Plugin plugin, ConfigManager config) {
        this.config = config;
        this.heatBonusKey = new NamespacedKey(plugin, "heat_resistance");
        this.heatPiecesKey = new NamespacedKey(plugin, "heat_pieces");
        this.coldBonusKey = new NamespacedKey(plugin, "cold_resistance");
        this.coldPiecesKey = new NamespacedKey(plugin, "cold_pieces");
    }

    public void setExternalItemReader(ExternalItemReader externalReader) {
        this.externalReader = externalReader;
    }

    /**
     * プレイヤーが現在装備している4部位の防具から耐熱・耐冷の合計を算出します。
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
            ArmorResistance r = inspectItem(item).resistance();
            heatPieces += r.heatPieces();
            heatBonus += r.heatLimitBonus();
            coldPieces += r.coldPieces();
            coldBonus += r.coldLimitBonus();
        }

        return new ArmorResistance(heatPieces, heatBonus, coldPieces, coldBonus);
    }

    /**
     * 単一アイテムの温度耐性情報を解析します。
     * 優先度: 1. PDC (/ltm armor set) -> 2. MythicMobs NBT (NBT: セクション) -> 3. config.yml の MythicItemID 上書き
     */
    public ItemInspectionResult inspectItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new ItemInspectionResult(ArmorResistance.NONE, "なし");
        }

        // 1. PersistentDataContainer (プラグインコマンドまたはBukkit PDC経由)
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            Double pdcHeat = pdc.get(heatBonusKey, PersistentDataType.DOUBLE);
            Double pdcCold = pdc.get(coldBonusKey, PersistentDataType.DOUBLE);
            if ((pdcHeat != null && pdcHeat > 0.0) || (pdcCold != null && pdcCold > 0.0)) {
                int hp = (pdcHeat != null && pdcHeat > 0.0)
                        ? pdc.getOrDefault(heatPiecesKey, PersistentDataType.INTEGER, 1)
                        : 0;
                double hb = (pdcHeat != null && pdcHeat > 0.0) ? pdcHeat : 0.0;

                int cp = (pdcCold != null && pdcCold > 0.0)
                        ? pdc.getOrDefault(coldPiecesKey, PersistentDataType.INTEGER, 1)
                        : 0;
                double cb = (pdcCold != null && pdcCold > 0.0) ? pdcCold : 0.0;

                return new ItemInspectionResult(new ArmorResistance(hp, hb, cp, cb), "PDC");
            }
        }

        // 2. MythicMobs NBT (custom_data トップレベルタグ: 例 NBT: { lifetemp_heat: 50 })
        if (externalReader != null) {
            String heatKey = config.getHeatNbtKey();
            String coldKey = config.getColdNbtKey();
            Double nbtHeat = externalReader.readNumericNbt(item, heatKey);
            Double nbtCold = externalReader.readNumericNbt(item, coldKey);

            if ((nbtHeat != null && nbtHeat > 0.0) || (nbtCold != null && nbtCold > 0.0)) {
                int hp = 0;
                double hb = 0.0;
                if (nbtHeat != null && nbtHeat > 0.0) {
                    hb = nbtHeat;
                    Double hpVal = externalReader.readNumericNbt(item, heatKey + "_pieces");
                    hp = (hpVal != null && hpVal >= 0.0) ? hpVal.intValue() : 1;
                }

                int cp = 0;
                double cb = 0.0;
                if (nbtCold != null && nbtCold > 0.0) {
                    cb = nbtCold;
                    Double cpVal = externalReader.readNumericNbt(item, coldKey + "_pieces");
                    cp = (cpVal != null && cpVal >= 0.0) ? cpVal.intValue() : 1;
                }

                return new ItemInspectionResult(new ArmorResistance(hp, hb, cp, cb), "MythicMobs NBT");
            }

            // 3. Config の MythicItemID フォールバック定義
            String mythicId = externalReader.getMythicItemId(item);
            if (mythicId != null) {
                ConfigManager.MythicItemOverride ov = config.getMythicItemOverrides().get(mythicId);
                if (ov != null) {
                    int hp = 0;
                    double hb = 0.0;
                    int cp = 0;
                    double cb = 0.0;
                    if ("HEAT".equals(ov.type()) || "BOTH".equals(ov.type())) {
                        hp = ov.pieces();
                        hb = ov.bonus();
                    }
                    if ("COLD".equals(ov.type()) || "BOTH".equals(ov.type())) {
                        cp = ov.pieces();
                        cb = ov.bonus();
                    }
                    return new ItemInspectionResult(new ArmorResistance(hp, hb, cp, cb), "Config (" + mythicId + ")");
                }
            }
        }

        return new ItemInspectionResult(ArmorResistance.NONE, "なし");
    }

    /**
     * アイテムに PDC 経由で耐熱または耐冷タグを付与します。
     */
    public boolean applyResistance(ItemStack item, boolean heat, double limitBonus, int pieces) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (heat) {
            pdc.set(heatBonusKey, PersistentDataType.DOUBLE, Math.max(0.0, limitBonus));
            pdc.set(heatPiecesKey, PersistentDataType.INTEGER, Math.max(0, pieces));
        } else {
            pdc.set(coldBonusKey, PersistentDataType.DOUBLE, Math.max(0.0, limitBonus));
            pdc.set(coldPiecesKey, PersistentDataType.INTEGER, Math.max(0, pieces));
        }
        item.setItemMeta(meta);
        return true;
    }

    /**
     * アイテムから PDC の温度耐性タグを削除します。
     */
    public boolean clearResistance(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(heatBonusKey);
        pdc.remove(heatPiecesKey);
        pdc.remove(coldBonusKey);
        pdc.remove(coldPiecesKey);
        item.setItemMeta(meta);
        return true;
    }
}
