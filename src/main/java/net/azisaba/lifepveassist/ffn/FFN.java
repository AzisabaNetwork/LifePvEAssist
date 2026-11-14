package net.azisaba.lifepveassist.ffn;

import net.azisaba.lifepveassist.ffn.listener.BlockBreakListener;
import net.azisaba.lifepveassist.ffn.listener.MythicLoadListener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * FFN 由来の弱点・耐性タグ管理と、MythicMobs メカニクスの登録を行う。
 * 旧 FFN プラグインの {@code onEnable} 相当の処理は {@link #register(JavaPlugin)} が担う。
 */
public final class FFN {

    public static final Map<LivingEntity, Map<String, Double>> weaknessTags = Collections.synchronizedMap(new WeakHashMap<>());
    public static final Map<LivingEntity, Map<String, Double>> resistanceTags = Collections.synchronizedMap(new WeakHashMap<>());

    @Deprecated
    public static final Map<LivingEntity, Map<String, Double>> damageTag = weaknessTags;
    @Deprecated
    public static final Map<LivingEntity, Map<String, Double>> protectTag = resistanceTags;

    private FFN() {}

    public static void register(JavaPlugin plugin) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new MythicLoadListener(), plugin);
        pluginManager.registerEvents(new BlockBreakListener(plugin), plugin);
    }

    public static void clear() {
        weaknessTags.clear();
        resistanceTags.clear();
    }
}
