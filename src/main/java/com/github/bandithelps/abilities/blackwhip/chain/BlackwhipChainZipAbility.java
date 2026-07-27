package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainSwingPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whip Zip: short press = Simple Zip (instant pull); hold past threshold = Charge Zip
 * (multi-block side anchors, pullback charge, forward launch).
 */
public class BlackwhipChainZipAbility extends Ability {

    private static final float LAUNCH_UP_BIAS = 0.32f;
    private static final float TENSION_PULL = 0.08f;
    private static final float HIT_RADIUS = 1.5f;
    private static final float THICKNESS = 0.9f;
    private static final float LINK_LENGTH = 0.85f;
    private static final float CHAIN_HP = 18.0f;
    private static final int SEGMENT_COUNT = 8;
    private static final int MAX_KEEP = 6;

    private static final Map<UUID, ZipSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class ZipSession {
        int heldTicks;
        boolean chargeArmed;
        float pullbackCharge;
        float initialAvgRope;
        final List<Integer> chargeChainIds = new ArrayList<>();
        final List<Vec3> anchors = new ArrayList<>();
    }

    public static final MapCodec<BlackwhipChainZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(22.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("simple_pull_power", new StaticValue(1.85f)).forGetter((ab) -> ab.simplePullPower),
                    Value.CODEC.optionalFieldOf("qf_pull_bonus", new StaticValue(0.06f)).forGetter((ab) -> ab.qfPullBonus),
                    Value.CODEC.optionalFieldOf("simple_visual_ticks", new StaticValue(10.0f)).forGetter((ab) -> ab.simpleVisualTicks),
                    Value.CODEC.optionalFieldOf("charge_threshold_ticks", new StaticValue(7.0f)).forGetter((ab) -> ab.chargeThresholdTicks),
                    Value.CODEC.optionalFieldOf("max_charge_ticks", new StaticValue(28.0f)).forGetter((ab) -> ab.maxChargeTicks),
                    Value.CODEC.optionalFieldOf("base_launch_power", new StaticValue(0.95f)).forGetter((ab) -> ab.baseLaunchPower),
                    Value.CODEC.optionalFieldOf("max_launch_power", new StaticValue(2.55f)).forGetter((ab) -> ab.maxLaunchPower),
                    Value.CODEC.optionalFieldOf("qf_launch_bonus", new StaticValue(0.08f)).forGetter((ab) -> ab.qfLaunchBonus),
                    Value.CODEC.optionalFieldOf("side_count", new StaticValue(4.0f)).forGetter((ab) -> ab.sideCount),
                    Value.CODEC.optionalFieldOf("side_angle", new StaticValue(32.0f)).forGetter((ab) -> ab.sideAngle),
                    Value.CODEC.optionalFieldOf("pullback_charge_rate", new StaticValue(0.035f)).forGetter((ab) -> ab.pullbackChargeRate),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(5.0f)).forGetter((ab) -> ab.damage),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainZipAbility::new));

    public final Value range;
    public final Value simplePullPower;
    public final Value qfPullBonus;
    public final Value simpleVisualTicks;
    public final Value chargeThresholdTicks;
    public final Value maxChargeTicks;
    public final Value baseLaunchPower;
    public final Value maxLaunchPower;
    public final Value qfLaunchBonus;
    public final Value sideCount;
    public final Value sideAngle;
    public final Value pullbackChargeRate;
    public final Value damage;

    public BlackwhipChainZipAbility(Value range, Value simplePullPower, Value qfPullBonus, Value simpleVisualTicks,
                                    Value chargeThresholdTicks, Value maxChargeTicks, Value baseLaunchPower,
                                    Value maxLaunchPower, Value qfLaunchBonus, Value sideCount, Value sideAngle,
                                    Value pullbackChargeRate, Value damage, AbilityProperties properties,
                                    AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.simplePullPower = simplePullPower;
        this.qfPullBonus = qfPullBonus;
        this.simpleVisualTicks = simpleVisualTicks;
        this.chargeThresholdTicks = chargeThresholdTicks;
        this.maxChargeTicks = maxChargeTicks;
        this.baseLaunchPower = baseLaunchPower;
        this.maxLaunchPower = maxLaunchPower;
        this.qfLaunchBonus = qfLaunchBonus;
        this.sideCount = sideCount;
        this.sideAngle = sideAngle;
        this.pullbackChargeRate = pullbackChargeRate;
        this.damage = damage;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlackwhipChainSwingAbility.forceStop(player);
        clearSession(player, level);

        ZipSession session = new ZipSession();
        session.heldTicks = 0;
        SESSIONS.put(player.getUUID(), session);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4f, 1.35f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!enabled || !(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return super.tick(entity, abilityInstance, enabled);
        }
        ZipSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return super.tick(entity, abilityInstance, enabled);
        }

        DataContext context = DataContext.forEntity(entity);
        session.heldTicks++;
        int threshold = Math.max(1, this.chargeThresholdTicks.getAsInt(context));

        if (!session.chargeArmed && session.heldTicks >= threshold) {
            armCharge(player, level, context, session);
        }

        if (session.chargeArmed) {
            tickCharge(player, context, session);
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }

        DataContext context = DataContext.forEntity(entity);
        int threshold = Math.max(1, this.chargeThresholdTicks.getAsInt(context));

        if (!session.chargeArmed || session.heldTicks < threshold || session.anchors.size() < 2) {
            retractChargeChains(level, session);
            performSimpleZip(player, level, context);
            return;
        }

        performChargeLaunch(player, level, context, session);
    }

    private void armCharge(ServerPlayer player, ServerLevel level, DataContext context, ZipSession session) {
        session.chargeArmed = true;
        PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());

        double range = this.range.getAsFloat(context);
        int count = Math.max(2, this.sideCount.getAsInt(context));
        float angleDeg = this.sideAngle.getAsFloat(context);

        Vec3 look = player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = worldUp.cross(look);
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = look.cross(right).normalize();
        Vec3 eye = player.getEyePosition();
        double cone = Math.toRadians(angleDeg);

        for (int i = 0; i < count; i++) {
            double ring = (2.0 * Math.PI * i) / count;
            Vec3 offset = right.scale(Math.cos(ring)).add(up.scale(Math.sin(ring))).normalize();
            Vec3 dir = look.scale(Math.cos(cone)).add(offset.scale(Math.sin(cone))).normalize();
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            Vec3 anchor = hit.getLocation();
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnAnchoredChain(
                    player, anchor, hit.getBlockPos(), BlackwhipChainEntity.PURPOSE_ZIP_CHARGE,
                    SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS, range * 1.35, MAX_KEEP, 0);
            if (chain != null) {
                session.chargeChainIds.add(chain.getId());
                session.anchors.add(anchor);
            }
        }

        if (session.anchors.isEmpty()) {
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.4f, 0.5f);
            return;
        }

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        double sum = 0.0;
        for (Vec3 a : session.anchors) {
            sum += center.distanceTo(a);
        }
        session.initialAvgRope = (float) (sum / session.anchors.size());
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.35f, 1.55f);
    }

    private void tickCharge(ServerPlayer player, DataContext context, ZipSession session) {
        if (session.anchors.isEmpty()) {
            return;
        }

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 avg = Vec3.ZERO;
        for (Vec3 a : session.anchors) {
            avg = avg.add(a);
        }
        avg = avg.scale(1.0 / session.anchors.size());
        Vec3 toAvg = avg.subtract(center);
        double dist = toAvg.length();
        Vec3 awayDir = dist > 1.0e-4 ? toAvg.scale(-1.0 / dist) : player.getLookAngle().scale(-1.0);

        Vec3 vel = player.getDeltaMovement();
        double awaySpeed = vel.dot(awayDir);
        float rate = this.pullbackChargeRate.getAsFloat(context);
        if (awaySpeed > 0.02 || player.isShiftKeyDown()) {
            session.pullbackCharge = Math.min(0.45f, session.pullbackCharge + rate);
        }

        if (dist > session.initialAvgRope + 0.35 && dist > 1.0e-4) {
            Vec3 toward = toAvg.scale(1.0 / dist).scale(TENSION_PULL);
            player.setDeltaMovement(vel.add(toward));
            session.pullbackCharge = Math.min(0.45f, session.pullbackCharge + rate * 0.5f);
        }
    }

    private void performSimpleZip(ServerPlayer player, ServerLevel level, DataContext context) {
        double range = this.range.getAsFloat(context);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        BlockHitResult hit = level.clip(new ClipContext(
                eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.4f, 0.55f);
            return;
        }

        Vec3 anchor = hit.getLocation();
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int visualTicks = Math.max(4, this.simpleVisualTicks.getAsInt(context));
        BlackwhipChainHelper.spawnAnchoredChain(
                player, anchor, hit.getBlockPos(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS, range * 1.25, MAX_KEEP, visualTicks);

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        Vec3 dir = dist > 1.0e-3 ? toAnchor.scale(1.0 / dist) : look;
        double power = this.simplePullPower.getAsFloat(context) * (1.0 + qf * this.qfPullBonus.getAsFloat(context));
        player.setDeltaMovement(dir.scale(power).add(0, 0.22, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7f, 1.35f);
    }

    private void performChargeLaunch(ServerPlayer player, ServerLevel level, DataContext context, ZipSession session) {
        int max = Math.max(1, this.maxChargeTicks.getAsInt(context));
        int threshold = Math.max(1, this.chargeThresholdTicks.getAsInt(context));
        float holdRatio = Mth.clamp((session.heldTicks - threshold) / (float) max, 0.0f, 1.0f);
        float chargeRatio = Mth.clamp(holdRatio + session.pullbackCharge, 0.0f, 1.0f);
        double qf = QuirkFactorUtil.getQuirkFactor(player);

        double power = Mth.lerp(chargeRatio,
                this.baseLaunchPower.getAsFloat(context),
                this.maxLaunchPower.getAsFloat(context))
                * (1.0 + qf * this.qfLaunchBonus.getAsFloat(context));
        Vec3 look = player.getLookAngle().normalize();
        player.setDeltaMovement(look.scale(power).add(0, power * LAUNCH_UP_BIAS, 0));
        player.hurtMarked = true;
        player.resetFallDistance();

        PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());
        retractChargeChains(level, session);
        applySweptDamage(player, level, context, look, chargeRatio, qf);

        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.65f, 1.35f);
    }

    private void applySweptDamage(ServerPlayer player, ServerLevel level, DataContext context, Vec3 look,
                                  float ratio, double qf) {
        double range = this.range.getAsFloat(context);
        float baseDamage = this.damage.getAsFloat(context);
        if (baseDamage <= 0.0f || range <= 0.0) {
            return;
        }
        float dmg = baseDamage * (0.5f + 0.5f * ratio) * (float) (1.0 + 0.1 * qf);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB search = new AABB(start, end).inflate(HIT_RADIUS);
        for (Entity e : level.getEntities(player, search)) {
            if (!(e instanceof LivingEntity target) || !target.isAlive() || target == player) {
                continue;
            }
            Optional<Vec3> impact = target.getBoundingBox().inflate(HIT_RADIUS).clip(start, end);
            if (impact.isPresent()) {
                target.hurt(level.damageSources().mobAttack(player), dmg);
            }
        }
    }

    private void retractChargeChains(ServerLevel level, ZipSession session) {
        for (int id : session.chargeChainIds) {
            if (level.getEntity(id) instanceof BlackwhipChainEntity chain) {
                chain.deactivate();
            }
        }
        session.chargeChainIds.clear();
        session.anchors.clear();
    }

    private void clearSession(ServerPlayer player, ServerLevel level) {
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            retractChargeChains(level, session);
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(),
                BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE, BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);
    }

    /** Clears zip state for mutex with swing / detach. */
    public static void forceStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            for (int id : session.chargeChainIds) {
                if (level.getEntity(id) instanceof BlackwhipChainEntity chain) {
                    chain.deactivate();
                }
            }
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(),
                BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE, BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_ZIP.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainZipAbility> {
        public MapCodec<BlackwhipChainZipAbility> codec() {
            return BlackwhipChainZipAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainZipAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Tap to Simple Zip (instant pull to a surface). Hold to Charge Zip: side chains latch blocks, lean back to load, release to fling forward.")
                    .add("range", TYPE_VALUE, "Raycast reach for simple and charge anchors.")
                    .add("simple_pull_power", TYPE_VALUE, "Velocity for a quick zip pull.")
                    .add("charge_threshold_ticks", TYPE_VALUE, "Hold ticks before charge mode arms.")
                    .add("max_charge_ticks", TYPE_VALUE, "Hold ticks for full launch power.")
                    .add("side_count", TYPE_VALUE, "Number of side-chain raycasts when charging.")
                    .add("side_angle", TYPE_VALUE, "Cone angle (degrees) for side chains from look.")
                    .add("pullback_charge_rate", TYPE_VALUE, "Extra charge gained while leaning away from anchors.")
                    .addExampleObject(new BlackwhipChainZipAbility(
                            new StaticValue(22.0f), new StaticValue(1.85f), new StaticValue(0.06f),
                            new StaticValue(10.0f), new StaticValue(7.0f), new StaticValue(28.0f),
                            new StaticValue(0.95f), new StaticValue(2.55f), new StaticValue(0.08f),
                            new StaticValue(4.0f), new StaticValue(32.0f), new StaticValue(0.035f),
                            new StaticValue(5.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
