package net.azisaba.lifepveassist.ffn.mechanic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import net.azisaba.lifepveassist.ffn.util.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CustomDamage extends SkillMechanic implements ITargetedEntitySkill {

    private final double baseAmount;
    private final List<String> tags;

    public CustomDamage(SkillExecutor manager, MythicLineConfig config) {
        super(manager, null, config.getLine(), config);
        this.baseAmount = config.getDouble(new String[]{"a", "amount"}, 1.0);
        String rawTag = config.getString(new String[]{"type", "t", "tag"}, null);
        if (rawTag != null && !rawTag.isEmpty()) {
            this.tags = Arrays.stream(rawTag.split(","))
                    .map(s -> s.toLowerCase(Locale.ROOT).trim())
                    .filter(s -> !s.isEmpty())
                    .toList();
        } else {
            this.tags = Collections.emptyList();
        }
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata skillMetadata, AbstractEntity target) {
        if (target == null || target.isDead()) {
            return SkillResult.INVALID_TARGET;
        }

        Entity targetEntity = BukkitAdapter.adapt(target);
        if (!(targetEntity instanceof LivingEntity livingTarget)) {
            return SkillResult.INVALID_TARGET;
        }

        if (skillMetadata.getCaster() == null || skillMetadata.getCaster().getEntity() == null) {
            return SkillResult.INVALID_TARGET;
        }

        Entity casterEntity = BukkitAdapter.adapt(skillMetadata.getCaster().getEntity());
        if (!(casterEntity instanceof LivingEntity caster)) {
            return SkillResult.INVALID_TARGET;
        }

        double damage = this.baseAmount;
        for (String tag : this.tags) {
            if (Tag.hasWeaknessTag(livingTarget, tag)) {
                damage = Tag.getWeakDamage(livingTarget, tag, this.baseAmount);
                break;
            } else if (Tag.hasResistanceTag(livingTarget, tag)) {
                damage = Tag.getProtectDamage(livingTarget, tag, this.baseAmount);
                break;
            }
        }

        applyDamage(livingTarget, damage, caster);
        return SkillResult.SUCCESS;
    }

    public void applyDamage(LivingEntity target, double damage, LivingEntity caster) {
        int previousNoDamageTicks = target.getNoDamageTicks();
        target.setNoDamageTicks(0);
        target.damage(damage, caster);
        target.setNoDamageTicks(previousNoDamageTicks);
    }

    @Deprecated
    public void setDamage(LivingEntity living, double damage, LivingEntity caster) {
        applyDamage(living, damage, caster);
    }

    public List<String> getTags() {
        return this.tags;
    }
}
