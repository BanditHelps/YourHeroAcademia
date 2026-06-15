package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipAnchor;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.entities.BlackwhipStyle;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

/**
 * Creative addition - "Yank": context-sensitive reel. If aiming at a living entity, it is snapped toward
 * the user (with a small impact hit); otherwise the user is pulled toward the targeted surface. Pull
 * strength scales with quirk factor.
 */
public class BlackwhipYankAbility extends Ability {

    public static final MapCodec<BlackwhipYankAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(20.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("target_pull_power", new StaticValue(1.6f)).forGetter((ab) -> ab.targetPullPower),
                    Value.CODEC.optionalFieldOf("self_pull_power", new StaticValue(1.8f)).forGetter((ab) -> ab.selfPullPower),
                    Value.CODEC.optionalFieldOf("impact_damage", new StaticValue(2.0f)).forGetter((ab) -> ab.impactDamage),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(0.45f)).forGetter((ab) -> ab.curve),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipYankAbility::new));

    public final Value range;
    public final Value targetPullPower;
    public final Value selfPullPower;
    public final Value impactDamage;
    public final Value thickness;
    public final Value curve;

    public BlackwhipYankAbility(Value range, Value targetPullPower, Value selfPullPower, Value impactDamage, Value thickness,
                               Value curve, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.targetPullPower = targetPullPower;
        this.selfPullPower = selfPullPower;
        this.impactDamage = impactDamage;
        this.thickness = thickness;
        this.curve = curve;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double range = this.range.getAsFloat(context);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        float thickness = this.thickness.getAsFloat(context);
        float curve = this.curve.getAsFloat(context);

        LivingEntity target = BlackwhipTargeting.raycastLiving(player, range);
        if (target != null) {
            yankTarget(player, level, context, target, qf, thickness, curve);
        } else {
            yankSelf(player, level, context, range, qf, thickness, curve);
        }
    }

    private void yankTarget(ServerPlayer player, ServerLevel level, DataContext context, LivingEntity target,
                            double qf, float thickness, float curve) {
        Vec3 toPlayer = player.position().add(0, player.getBbHeight() * 0.5, 0).subtract(target.position());
        double dist = toPlayer.length();
        Vec3 dir = dist > 1.0e-3 ? toPlayer.scale(1.0 / dist) : new Vec3(0, 0.2, 0);
        double power = this.targetPullPower.getAsFloat(context) * (1.0 + 0.05 * qf);
        target.setDeltaMovement(dir.scale(power).add(0, 0.25, 0));
        target.hurtMarked = true;

        float damage = this.impactDamage.getAsFloat(context) * (float) (1.0 + 0.1 * qf);
        if (damage > 0) {
            target.hurt(level.damageSources().mobAttack(player), damage);
        }

        BlackwhipEntity whip = BlackwhipHelper.spawnTether(player, target, BlackwhipAnchor.HAND, thickness, curve, 2);
        whip.setLifetime(8);
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    private void yankSelf(ServerPlayer player, ServerLevel level, DataContext context, double range,
                          double qf, float thickness, float curve) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Vec3 anchor = hit.getLocation();
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        Vec3 dir = dist > 1.0e-3 ? toAnchor.scale(1.0 / dist) : new Vec3(0, 1, 0);
        double power = this.selfPullPower.getAsFloat(context) * (1.0 + 0.05 * qf);
        player.setDeltaMovement(dir.scale(power).add(0, 0.2, 0));
        player.hurtMarked = true;
        player.fallDistance = 0.0f;

        BlackwhipEntity whip = BlackwhipHelper.spawnAnchorRope(player, anchor, BlackwhipStyle.ANCHOR_ROPE, thickness, curve, 2);
        whip.setLifetime(10);
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_YANK.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipYankAbility> {
        public MapCodec<BlackwhipYankAbility> codec() {
            return BlackwhipYankAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipYankAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Context-sensitive reel: snaps an aimed-at entity toward you (with an impact hit), or pulls you toward the targeted surface. Quirk-factor scaled.")
                    .add("range", TYPE_VALUE, "How far the yank reaches.")
                    .add("target_pull_power", TYPE_VALUE, "Velocity applied when reeling an entity in.")
                    .add("self_pull_power", TYPE_VALUE, "Velocity applied when reeling yourself to a surface.")
                    .add("impact_damage", TYPE_VALUE, "Damage dealt to a reeled-in entity.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("curve", TYPE_VALUE, "Visual whip curve amount.")
                    .addExampleObject(new BlackwhipYankAbility(new StaticValue(20.0f), new StaticValue(1.6f), new StaticValue(1.8f),
                            new StaticValue(2.0f), new StaticValue(1.0f), new StaticValue(0.45f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
