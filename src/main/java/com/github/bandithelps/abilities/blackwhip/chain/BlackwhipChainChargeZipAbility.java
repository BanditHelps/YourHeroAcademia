package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainChargeZipPayload;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
 * Charge Zip: hold to shoot side chains, stretch backward, and release to fling along the start facing.
 */
public class BlackwhipChainChargeZipAbility extends Ability {

    private static final float LAUNCH_UP_BIAS = 0.22f;
    private static final float HIT_RADIUS = 1.5f;
    private static final float THICKNESS = 0.9f;
    private static final float LINK_LENGTH = 0.95f;
    private static final float CHAIN_HP = 18.0f;
    private static final float MAX_STRETCH_BLOCKS = 6.0f;
    private static final float DOWNWARD_BIAS = 0.15f;
    private static final int SEGMENT_COUNT = 12;
    private static final int MAX_KEEP = 8;
    private static final int TRAVEL_TICKS = 10;

    private static final Map<UUID, ChargeSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class ChargeSession {
        int heldTicks;
        float lockedYaw;
        float lockedPitch;
        float pullbackSpeed;
        Vec3 lockedLook = Vec3.ZERO;
        Vec3 startPos = Vec3.ZERO;
        final List<Integer> chainIds = new ArrayList<>();
    }

    public static final MapCodec<BlackwhipChainChargeZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(28.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("max_charge_ticks", new StaticValue(40.0f)).forGetter((ab) -> ab.maxChargeTicks),
                    Value.CODEC.optionalFieldOf("base_launch_power", new StaticValue(1.1f)).forGetter((ab) -> ab.baseLaunchPower),
                    Value.CODEC.optionalFieldOf("max_launch_power", new StaticValue(2.9f)).forGetter((ab) -> ab.maxLaunchPower),
                    Value.CODEC.optionalFieldOf("qf_launch_bonus", new StaticValue(0.08f)).forGetter((ab) -> ab.qfLaunchBonus),
                    Value.CODEC.optionalFieldOf("side_count", new StaticValue(2.0f)).forGetter((ab) -> ab.sideCount),
                    Value.CODEC.optionalFieldOf("side_angle", new StaticValue(42.0f)).forGetter((ab) -> ab.sideAngle),
                    Value.CODEC.optionalFieldOf("pullback_speed", new StaticValue(0.06f)).forGetter((ab) -> ab.pullbackSpeed),
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
    public final Value pullbackSpeed;
    public final Value damage;

    public BlackwhipChainChargeZipAbility(Value range, Value maxChargeTicks, Value baseLaunchPower,
                                          Value maxLaunchPower, Value qfLaunchBonus, Value sideCount,
                                          Value sideAngle, Value pullbackSpeed, Value damage,
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
        this.pullbackSpeed = pullbackSpeed;
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
        session.lockedYaw = player.getYRot();
        session.lockedPitch = player.getXRot();
        session.lockedLook = player.getLookAngle().normalize();
        session.startPos = player.position();
        session.pullbackSpeed = Math.max(0.0f, this.pullbackSpeed.getAsFloat(context));
        SESSIONS.put(player.getUUID(), session);

        spawnSideChains(player, context, session);
        syncClient(player, session, 0.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.7f, 0.85f);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.35f, 1.55f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!enabled || !(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return super.tick(entity, abilityInstance, enabled);
        }
        ChargeSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return super.tick(entity, abilityInstance, enabled);
        }

        DataContext context = DataContext.forEntity(entity);
        session.heldTicks++;
        pruneDeadChains(level, session);
        float ratio = chargeRatio(session, context);
        tickPullback(player, session, ratio);
        syncClient(player, session, ratio);
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChargeSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            stopClient(player);
            return;
        }

        DataContext context = DataContext.forEntity(entity);
        performChargeLaunch(player, level, context, session);
        retractChains(level, session);
        stopClient(player);
    }

    private void spawnSideChains(ServerPlayer player, DataContext context, ChargeSession session) {
        double range = Math.max(4.0, this.range.getAsFloat(context));
        int count = Math.max(2, this.sideCount.getAsInt(context));
        float angleDeg = this.sideAngle.getAsFloat(context);
        Vec3 look = session.lockedLook.lengthSqr() < 1.0e-6 ? player.getLookAngle().normalize() : session.lockedLook;
        double maxDistance = range + MAX_STRETCH_BLOCKS + 10.0;

        for (int i = 0; i < count; i++) {
            float t = count <= 1 ? 0.0f : (i / (float) (count - 1)) * 2.0f - 1.0f;
            Vec3 dir = yawOffset(look, t * angleDeg).add(0.0, -DOWNWARD_BIAS, 0.0);
            if (dir.lengthSqr() < 1.0e-6) {
                dir = look;
            } else {
                dir = dir.normalize();
            }
            HumanoidArm arm = i < (count + 1) / 2 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                    player, dir, range, SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS,
                    TRAVEL_TICKS, 0, maxDistance, MAX_KEEP, BlackwhipChainEntity.PURPOSE_ZIP_CHARGE, arm);
            if (chain != null) {
                session.chainIds.add(chain.getId());
            }
        }
    }

    private void tickPullback(ServerPlayer player, ChargeSession session, float chargeRatio) {
        if (player.horizontalCollision) {
            return;
        }
        Vec3 look = session.lockedLook;
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        if (back.lengthSqr() < 1.0e-6) {
            return;
        }
        back = back.normalize();
        double stretch = player.position().distanceTo(session.startPos);
        Vec3 vel = player.getDeltaMovement();
        if (stretch >= MAX_STRETCH_BLOCKS) {
            player.setDeltaMovement(0.0, vel.y, 0.0);
            player.hurtMarked = true;
            return;
        }
        float speed = session.pullbackSpeed * (0.7f + 0.3f * chargeRatio);
        player.setDeltaMovement(back.x * speed, vel.y, back.z * speed);
        player.hurtMarked = true;
        player.setOnGround(false);
    }

    private void performChargeLaunch(ServerPlayer player, ServerLevel level, DataContext context, ChargeSession session) {
        float ratio = chargeRatio(session, context);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        Vec3 launchDir = session.lockedLook.lengthSqr() < 1.0e-6 ? player.getLookAngle().normalize() : session.lockedLook;
        double power = Mth.lerp(ratio,
                this.baseLaunchPower.getAsFloat(context),
                this.maxLaunchPower.getAsFloat(context))
                * (1.0 + qf * this.qfLaunchBonus.getAsFloat(context));
        player.setDeltaMovement(launchDir.scale(power).add(0.0, power * LAUNCH_UP_BIAS, 0.0));
        player.hurtMarked = true;
        player.resetFallDistance();
        player.setOnGround(false);

        applySweptDamage(player, level, context, launchDir, ratio, qf);
        float pitch = 1.15f + 0.35f * ratio;
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

    private float chargeRatio(ChargeSession session, DataContext context) {
        int max = Math.max(1, this.maxChargeTicks.getAsInt(context));
        return Mth.clamp(session.heldTicks / (float) max, 0.0f, 1.0f);
    }

    private static Vec3 yawOffset(Vec3 look, float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(look.x * cos - look.z * sin, look.y, look.x * sin + look.z * cos);
    }

    private static void pruneDeadChains(ServerLevel level, ChargeSession session) {
        for (int i = session.chainIds.size() - 1; i >= 0; i--) {
            int id = session.chainIds.get(i);
            if (!(level.getEntity(id) instanceof BlackwhipChainEntity chain) || !chain.isActive()) {
                session.chainIds.remove(i);
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
    }

    private void clearSession(ServerPlayer player, ServerLevel level) {
        ChargeSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            retractChains(level, session);
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);
        stopClient(player);
    }

    private static void syncClient(ServerPlayer player, ChargeSession session, float chargeRatio) {
        PacketDistributor.sendToPlayer(player, new BlackwhipChainChargeZipPayload(
                true, session.lockedYaw, session.lockedPitch, chargeRatio, session.pullbackSpeed));
    }

    private static void stopClient(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, BlackwhipChainChargeZipPayload.stop());
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
        stopClient(player);
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
            builder.setDescription("Hold to shoot side chains from both hands and stretch backward. Release to fling in the facing from when the charge started. Charge time scales launch power.")
                    .add("range", TYPE_VALUE, "Raycast reach for side-chain tips.")
                    .add("max_charge_ticks", TYPE_VALUE, "Hold ticks for a full-power launch.")
                    .add("base_launch_power", TYPE_VALUE, "Launch velocity at zero charge.")
                    .add("max_launch_power", TYPE_VALUE, "Launch velocity at full charge.")
                    .add("qf_launch_bonus", TYPE_VALUE, "Extra launch multiplier per quirk factor.")
                    .add("side_count", TYPE_VALUE, "Number of side chains (minimum 2).")
                    .add("side_angle", TYPE_VALUE, "Horizontal yaw fan half-angle in degrees.")
                    .add("pullback_speed", TYPE_VALUE, "Blocks per tick the player is pulled backward while charging.")
                    .add("damage", TYPE_VALUE, "Base impact damage along the launch path.")
                    .addExampleObject(new BlackwhipChainChargeZipAbility(
                            new StaticValue(28.0f), new StaticValue(40.0f),
                            new StaticValue(1.1f), new StaticValue(2.9f), new StaticValue(0.08f),
                            new StaticValue(2.0f), new StaticValue(42.0f), new StaticValue(0.06f),
                            new StaticValue(5.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
