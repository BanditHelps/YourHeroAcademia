package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainSwingPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
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
 * Charge Zip: latch four wide-spread chains, walk/back up like a slingshot to stretch charge, release to fling.
 */
public class BlackwhipChainChargeZipAbility extends Ability {

    private static final float LAUNCH_UP_BIAS = 0.28f;
    private static final float HIT_RADIUS = 1.5f;
    private static final float THICKNESS = 0.9f;
    private static final float LINK_LENGTH = 0.95f;
    private static final float CHAIN_HP = 18.0f;
    private static final int SEGMENT_COUNT = 12;
    private static final int MAX_KEEP = 8;
    /** How many blocks of stretch past latch distance maps to full slingshot charge. */
    private static final float FULL_STRETCH_BLOCKS = 8.5f;
    /** Soft rubber-band spring strength (keeps slingshot feel without locking the player). */
    private static final float SPRING_STRENGTH = 0.038f;
    private static final float SPRING_MAX = 0.18f;
    /** Outward velocity damping while stretched — leave most of the player's backup speed. */
    private static final float OUTWARD_DAMP = 0.18f;

    private static final Map<UUID, ChargeSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class ChargeSession {
        int heldTicks;
        boolean armed;
        float stretchCharge;
        float initialDist;
        float peakStretch;
        Vec3 centroid = Vec3.ZERO;
        final List<Integer> chainIds = new ArrayList<>();
        final List<Vec3> anchors = new ArrayList<>();
    }

    public static final MapCodec<BlackwhipChainChargeZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(34.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("max_charge_ticks", new StaticValue(36.0f)).forGetter((ab) -> ab.maxChargeTicks),
                    Value.CODEC.optionalFieldOf("base_launch_power", new StaticValue(1.05f)).forGetter((ab) -> ab.baseLaunchPower),
                    Value.CODEC.optionalFieldOf("max_launch_power", new StaticValue(2.85f)).forGetter((ab) -> ab.maxLaunchPower),
                    Value.CODEC.optionalFieldOf("qf_launch_bonus", new StaticValue(0.08f)).forGetter((ab) -> ab.qfLaunchBonus),
                    Value.CODEC.optionalFieldOf("side_count", new StaticValue(4.0f)).forGetter((ab) -> ab.sideCount),
                    Value.CODEC.optionalFieldOf("side_angle", new StaticValue(52.0f)).forGetter((ab) -> ab.sideAngle),
                    Value.CODEC.optionalFieldOf("pullback_charge_rate", new StaticValue(0.05f)).forGetter((ab) -> ab.pullbackChargeRate),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(5.0f)).forGetter((ab) -> ab.damage),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainChargeZipAbility::new));

    public final Value range;
    public final Value maxChargeTicks;
    public final Value baseLaunchPower;
    public final Value maxLaunchPower;
    public final Value qfLaunchBonus;
    public final Value sideCount;
    public final Value sideAngle;
    public final Value pullbackChargeRate;
    public final Value damage;

    public BlackwhipChainChargeZipAbility(Value range, Value maxChargeTicks, Value baseLaunchPower,
                                          Value maxLaunchPower, Value qfLaunchBonus, Value sideCount,
                                          Value sideAngle, Value pullbackChargeRate, Value damage,
                                          AbilityProperties properties, AbilityStateManager conditions,
                                          List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
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
        BlackwhipWebSwingAbility.forceStop(player);
        BlackwhipChainZipAbility.forceStop(player);
        clearSession(player, level);

        DataContext context = DataContext.forEntity(entity);
        ChargeSession session = new ChargeSession();
        SESSIONS.put(player.getUUID(), session);

        if (!armCharge(player, level, context, session)) {
            SESSIONS.remove(player.getUUID());
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!enabled || !(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return super.tick(entity, abilityInstance, enabled);
        }
        ChargeSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.armed) {
            return super.tick(entity, abilityInstance, enabled);
        }

        DataContext context = DataContext.forEntity(entity);
        session.heldTicks++;
        pruneDeadChains(level, session);
        if (session.anchors.size() < 2) {
            retractChains(level, session);
            SESSIONS.remove(player.getUUID());
            return super.tick(entity, abilityInstance, enabled);
        }
        tickSlingshot(player, context, session);
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChargeSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }

        DataContext context = DataContext.forEntity(entity);
        // Launch only if still crouching (key released). Standing cancels.
        if (session.armed && session.anchors.size() >= 2 && entity.isCrouching()) {
            performChargeLaunch(player, level, context, session);
        } else {
            retractChains(level, session);
        }
    }

    private boolean armCharge(ServerPlayer player, ServerLevel level, DataContext context, ChargeSession session) {
        PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());

        double range = this.range.getAsFloat(context);
        int count = Math.max(4, this.sideCount.getAsInt(context));
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

        // Wide diamond: four rays around a broad cone so anchors land farther apart.
        for (int i = 0; i < count; i++) {
            double ring = (2.0 * Math.PI * i) / count + Math.PI * 0.25;
            Vec3 offset = right.scale(Math.cos(ring)).add(up.scale(Math.sin(ring))).normalize();
            Vec3 dir = look.scale(Math.cos(cone)).add(offset.scale(Math.sin(cone))).normalize();
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            Vec3 anchor = BlackwhipChainAnchors.surfaceAttachPoint(hit);
            // Long max-distance so the player can walk back and stretch the slingshot.
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnAnchoredChain(
                    player, anchor, hit.getBlockPos(), BlackwhipChainEntity.PURPOSE_ZIP_CHARGE,
                    SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS, range + FULL_STRETCH_BLOCKS + 10.0, MAX_KEEP, 0);
            if (chain != null) {
                session.chainIds.add(chain.getId());
                session.anchors.add(anchor);
            }
        }

        if (session.anchors.size() < 2) {
            retractChains(level, session);
            return false;
        }

        session.armed = true;
        session.centroid = averageAnchors(session);
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        session.initialDist = (float) center.distanceTo(session.centroid);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.35f, 1.55f);
        return true;
    }

    private void tickSlingshot(ServerPlayer player, DataContext context, ChargeSession session) {
        session.centroid = averageAnchors(session);
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toCentroid = session.centroid.subtract(center);
        double dist = toCentroid.length();
        if (dist < 1.0e-4) {
            return;
        }
        Vec3 toward = toCentroid.scale(1.0 / dist);
        Vec3 away = toward.scale(-1.0);
        double stretch = dist - session.initialDist;
        session.peakStretch = Math.max(session.peakStretch, (float) Math.max(0.0, stretch));

        // Stretch is the primary slingshot charge — backing up loads the band.
        float stretchCharge = Mth.clamp(session.peakStretch / FULL_STRETCH_BLOCKS, 0.0f, 1.0f);
        session.stretchCharge = Math.max(session.stretchCharge, stretchCharge);

        Vec3 vel = player.getDeltaMovement();
        double awaySpeed = vel.dot(away);
        float rate = this.pullbackChargeRate.getAsFloat(context);
        if (awaySpeed > 0.015 || stretch > 0.35) {
            // Keep feeding charge while the player actively stretches further.
            session.stretchCharge = Math.min(1.0f, session.stretchCharge + rate);
        }

        // Soft rubber-band: resist stretch gently so you can still walk back and load it.
        if (stretch > 0.2) {
            float spring = (float) Math.min(stretch * SPRING_STRENGTH, SPRING_MAX);
            if (awaySpeed > 0.02) {
                vel = vel.subtract(away.scale(awaySpeed * OUTWARD_DAMP));
            }
            player.setDeltaMovement(vel.add(toward.scale(spring)));
            player.hurtMarked = true;
        }
    }

    private void performChargeLaunch(ServerPlayer player, ServerLevel level, DataContext context, ChargeSession session) {
        session.centroid = averageAnchors(session);
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toCentroid = session.centroid.subtract(center);
        double dist = toCentroid.length();
        Vec3 towardAnchors = dist > 1.0e-4 ? toCentroid.scale(1.0 / dist) : player.getLookAngle().normalize();
        Vec3 look = player.getLookAngle().normalize();
        // Mostly slingshot toward the latch cluster, with a bit of look aim.
        Vec3 launchDir = towardAnchors.scale(0.72).add(look.scale(0.28));
        if (launchDir.lengthSqr() < 1.0e-6) {
            launchDir = look;
        } else {
            launchDir = launchDir.normalize();
        }

        int max = Math.max(1, this.maxChargeTicks.getAsInt(context));
        float holdRatio = Mth.clamp(session.heldTicks / (float) max, 0.0f, 1.0f);
        // Stretch dominates charge; hold time is a small assist.
        float chargeRatio = Mth.clamp(session.stretchCharge * 0.85f + holdRatio * 0.25f, 0.0f, 1.0f);
        double qf = QuirkFactorUtil.getQuirkFactor(player);

        double power = Mth.lerp(chargeRatio,
                this.baseLaunchPower.getAsFloat(context),
                this.maxLaunchPower.getAsFloat(context))
                * (1.0 + qf * this.qfLaunchBonus.getAsFloat(context));
        player.setDeltaMovement(launchDir.scale(power).add(0, power * LAUNCH_UP_BIAS, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        player.setOnGround(false);

        PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());
        retractChains(level, session);
        applySweptDamage(player, level, context, launchDir, chargeRatio, qf);

        float pitch = 1.15f + 0.35f * chargeRatio;
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, pitch);
    }

    private void applySweptDamage(ServerPlayer player, ServerLevel level, DataContext context, Vec3 dir,
                                  float ratio, double qf) {
        double range = this.range.getAsFloat(context);
        float baseDamage = this.damage.getAsFloat(context);
        if (baseDamage <= 0.0f || range <= 0.0) {
            return;
        }
        float dmg = baseDamage * (0.5f + 0.5f * ratio) * (float) (1.0 + 0.1 * qf);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(dir.scale(range));
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

    private static Vec3 averageAnchors(ChargeSession session) {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 a : session.anchors) {
            sum = sum.add(a);
        }
        return sum.scale(1.0 / session.anchors.size());
    }

    private static void pruneDeadChains(ServerLevel level, ChargeSession session) {
        for (int i = session.chainIds.size() - 1; i >= 0; i--) {
            int id = session.chainIds.get(i);
            if (!(level.getEntity(id) instanceof BlackwhipChainEntity chain) || !chain.isAnchored()) {
                session.chainIds.remove(i);
                if (i < session.anchors.size()) {
                    session.anchors.remove(i);
                }
            }
        }
    }

    private void retractChains(ServerLevel level, ChargeSession session) {
        for (int id : session.chainIds) {
            if (level.getEntity(id) instanceof BlackwhipChainEntity chain) {
                chain.deactivate();
            }
        }
        session.chainIds.clear();
        session.anchors.clear();
        session.armed = false;
    }

    private void clearSession(ServerPlayer player, ServerLevel level) {
        ChargeSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            retractChains(level, session);
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);
    }

    /** Clears charge-zip state for mutex with swing / simple zip / detach. */
    public static void forceStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChargeSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            for (int id : session.chainIds) {
                if (level.getEntity(id) instanceof BlackwhipChainEntity chain) {
                    chain.deactivate();
                }
            }
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_CHARGE_ZIP.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainChargeZipAbility> {
        public MapCodec<BlackwhipChainChargeZipAbility> codec() {
            return BlackwhipChainChargeZipAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainChargeZipAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While crouching, hold to latch four wide chains, back up to stretch the slingshot, release to fling forward.")
                    .add("range", TYPE_VALUE, "Raycast reach for side-chain anchors.")
                    .add("max_charge_ticks", TYPE_VALUE, "Hold ticks that assist launch power (stretch is primary).")
                    .add("side_count", TYPE_VALUE, "Number of side-chain raycasts (use 4).")
                    .add("side_angle", TYPE_VALUE, "Cone angle (degrees) spreading the four chains.")
                    .add("pullback_charge_rate", TYPE_VALUE, "Extra charge gained while actively stretching back.")
                    .addExampleObject(new BlackwhipChainChargeZipAbility(
                            new StaticValue(34.0f), new StaticValue(36.0f),
                            new StaticValue(1.05f), new StaticValue(2.85f), new StaticValue(0.08f),
                            new StaticValue(4.0f), new StaticValue(52.0f), new StaticValue(0.05f),
                            new StaticValue(5.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
