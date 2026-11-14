package net.azisaba.lifepveassist.ffn.mechanic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import net.azisaba.lifepveassist.ffn.FFN;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Body extends SkillMechanic implements ITargetedEntitySkill {

    private final boolean isWeakness;
    private final double multiple;
    private final String tag;

    public Body(SkillExecutor manager, MythicLineConfig config) {
        super(manager, null, config.getLine(), config);
        this.isWeakness = config.getBoolean(new String[]{"w", "weak"}, true);
        this.multiple = config.getDouble(new String[]{"m", "multiple"}, 1.0);
        String configuredTag = config.getString(new String[]{"t", "type", "tag"}, null);
        this.tag = configuredTag != null ? configuredTag.toLowerCase(Locale.ROOT).trim() : null;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata skillMetadata, AbstractEntity target) {
        if (target == null || target.isDead()) {
            return SkillResult.INVALID_TARGET;
        }

        if (this.tag == null || this.tag.isEmpty()) {
            return SkillResult.INVALID_CONFIG;
        }

        Entity entity = BukkitAdapter.adapt(target);
        if (!(entity instanceof LivingEntity living)) {
            return SkillResult.INVALID_TARGET;
        }

        Map<LivingEntity, Map<String, Double>> targetMap = this.isWeakness ? FFN.weaknessTags : FFN.resistanceTags;
        targetMap.computeIfAbsent(living, k -> new ConcurrentHashMap<>()).put(this.tag, this.multiple);

        return SkillResult.SUCCESS;
    }
}
