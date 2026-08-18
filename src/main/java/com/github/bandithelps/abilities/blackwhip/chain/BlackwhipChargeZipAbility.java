package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainChargeZipPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge Zip: hold to shoot side chains, stretch backward, and release to fling along the start facing.
 */
public class BlackwhipChargeZipAbility extends Ability {

    private static final float LAUNCH_UP_BIAS = 0.22f;
    private static final float HIT_RADIUS = 2.15f;
    private static final float HIT_KNOCKBACK = 0.55f;
    private static final float MIN_FLIGHT_SPEED = 0.28f;
    private static final int FLIGHT_HIT_TICKS = 24;
    private static final float THICKNESS = 0.9f;
    private static final float LINK_LENGTH = 0.95f;
    private static final float CHAIN_HP = 18.0f;
    private static final float MAX_STRETCH_BLOCKS = 6.0f;
    private static final float DOWNWARD_BIAS = 0.15f;
    private static final int SEGMENT_COUNT = 12;
    private static final int MAX_KEEP = 8;
    private static final int TRAVEL_TICKS = 10;

    private static final Map<UUID, ChargeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, FlightHit> FLIGHTS = new ConcurrentHashMap<>();

    private static final class ChargeSession {
        int heldTicks;
        float lockedYaw;
        float lockedPitch;
        float pullbackSpeed;
        Vec3 lockedLook = Vec3.ZERO;
        Vec3 startPos = Vec3.ZERO;
        final List<Integer> chainIds = new ArrayList<>();
    }

    /** Post-release slam window so fly-throughs keep counting after the launch tick. */
    private static final class FlightHit {
        int ticksLeft;
        int maxHits;
        float damage;
        Vec3 lastCenter = Vec3.ZERO;
        final Set<Integer> hitIds = new HashSet<>();
    }

    public static final MapCodec<BlackwhipChargeZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
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
                    Value.CODEC.optionalFieldOf("max_hits", new StaticValue(4.0f)).forGetter((ab) -> ab.maxHits),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChargeZipAbility::new));

    public final Value range;
    public final Value maxChargeTicks;
    public final Value baseLaunchPower;
    public final Value maxLaunchPower;
    public final Value qfLaunchBonus;
    public final Value sideCount;
    public final Value sideAngle;
    public final Value pullbackSpeed;
    public final Value damage;
    public final Value maxHits;

    public BlackwhipChargeZipAbility(Value range, Value maxChargeTicks, Value baseLaunchPower,
                                     Value maxLaunchPower, Value qfLaunchBonus, Value sideCount,
                                     Value sideAngle, Value pullbackSpeed, Value damage, Value maxHits,
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
        this.maxHits = maxHits;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlackwhipSwingAbility.forceStop(player);
        BlackwhipWebSwingAbility.forceStop(player);
        BlackwhipZipAbility.forceStop(player);
        FLIGHTS.remove(player.getUUID());
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

        float dmg = this.damage.getAsFloat(context) * ratio * (float) (1.0 + 0.1 * qf);
        int maxHits = Math.max(0, this.maxHits.getAsInt(context));
        startFlightHits(player, dmg, maxHits);
        float pitch = 1.15f + 0.35f * ratio;
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, pitch);
    }

    private void startFlightHits(ServerPlayer player, float damage, int maxHits) {
        if (damage <= 0.0f || maxHits <= 0) {
            return;
        }
        FlightHit flight = new FlightHit();
        flight.ticksLeft = FLIGHT_HIT_TICKS;
        flight.maxHits = maxHits;
        flight.damage = damage;
        flight.lastCenter = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        FLIGHTS.put(player.getUUID(), flight);
        if (player.level() instanceof ServerLevel level) {
            sweepFlightHits(player, level, flight);
        }
    }

    /**
     * Continues slam hit detection after release. Called once per server tick from
     * {@link com.github.bandithelps.utils.blackwhip.BlackwhipServerEvents}.
     */
    public static void tickFlightHits(MinecraftServer server) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, FlightHit>> it = FLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FlightHit> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(player.level() instanceof ServerLevel level) || !player.isAlive()) {
                it.remove();
                continue;
            }
            FlightHit flight = entry.getValue();
            sweepFlightHits(player, level, flight);
            flight.ticksLeft--;
            if (flight.hitIds.size() >= flight.maxHits
                    || flight.ticksLeft <= 0
                    || player.getDeltaMovement().lengthSqr() < MIN_FLIGHT_SPEED * MIN_FLIGHT_SPEED) {
                it.remove();
            }
        }
    }

    private static void sweepFlightHits(ServerPlayer player, ServerLevel level, FlightHit flight) {
        if (flight.hitIds.size() >= flight.maxHits) {
            return;
        }
        Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        Vec3 motion = player.getDeltaMovement();
        Vec3 end = motion.lengthSqr() > 1.0e-6 ? center.add(motion) : center;
        AABB aroundPlayer = player.getBoundingBox().inflate(HIT_RADIUS);
        AABB search = aroundPlayer.minmax(new AABB(flight.lastCenter, end).inflate(HIT_RADIUS));
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity e : level.getEntities(player, search)) {
            if (!(e instanceof LivingEntity target) || !target.isAlive() || target == player) {
                continue;
            }
            if (flight.hitIds.contains(target.getId())) {
                continue;
            }
            AABB hitBox = target.getBoundingBox().inflate(0.65);
            boolean overlapped = hitBox.intersects(aroundPlayer);
            boolean swept = hitBox.clip(flight.lastCenter, end).isPresent()
                    || hitBox.clip(flight.lastCenter, center).isPresent();
            if (overlapped || swept) {
                candidates.add(target);
            }
        }
        candidates.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        for (LivingEntity target : candidates) {
            if (flight.hitIds.size() >= flight.maxHits) {
                break;
            }
            float hitDamage = damageForHit(flight);
            if (hitDamage <= 0.0f) {
                break;
            }
            target.hurt(level.damageSources().mobAttack(player), hitDamage);
            target.knockback(HIT_KNOCKBACK, player.getX() - target.getX(), player.getZ() - target.getZ());
            target.hurtMarked = true;
            flight.hitIds.add(target.getId());
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.65f, 1.1f);
        }
        flight.lastCenter = center;
    }

    /**
     * First hit deals full charge-scaled damage; each later hit drops by {@code 1 / max_hits}.
     * With {@code max_hits = 4}: 100%, 75%, 50%, 25%.
     */
    private static float damageForHit(FlightHit flight) {
        int remaining = flight.maxHits - flight.hitIds.size();
        if (remaining <= 0 || flight.maxHits <= 0) {
            return 0.0f;
        }
        return flight.damage * remaining / (float) flight.maxHits;
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
        FLIGHTS.remove(player.getUUID());
        stopClient(player);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHARGE_ZIP.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChargeZipAbility> {
        public MapCodec<BlackwhipChargeZipAbility> codec() {
            return BlackwhipChargeZipAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChargeZipAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Hold to shoot side chains from both hands and stretch backward. Release to fling in the facing from when the charge started. Charge time scales launch power.")
                    .add("range", TYPE_VALUE, "Raycast reach for side-chain tips.")
                    .add("max_charge_ticks", TYPE_VALUE, "Hold ticks for a full-power launch.")
                    .add("base_launch_power", TYPE_VALUE, "Launch velocity at zero charge.")
                    .add("max_launch_power", TYPE_VALUE, "Launch velocity at full charge.")
                    .add("qf_launch_bonus", TYPE_VALUE, "Extra launch multiplier per quirk factor.")
                    .add("side_count", TYPE_VALUE, "Number of side chains (minimum 2).")
                    .add("side_angle", TYPE_VALUE, "Horizontal yaw fan half-angle in degrees.")
                    .add("pullback_speed", TYPE_VALUE, "Blocks per tick the player is pulled backward while charging.")
                    .add("damage", TYPE_VALUE, "Full-charge damage for the first entity hit. Scales linearly with charge percentage.")
                    .add("max_hits", TYPE_VALUE, "Maximum entities damaged per launch. Each successive hit deals 1/max_hits less of full damage.")
                    .addExampleObject(new BlackwhipChargeZipAbility(
                            new StaticValue(28.0f), new StaticValue(40.0f),
                            new StaticValue(1.1f), new StaticValue(2.9f), new StaticValue(0.08f),
                            new StaticValue(2.0f), new StaticValue(42.0f), new StaticValue(0.06f),
                            new StaticValue(5.0f), new StaticValue(4.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
