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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whip Swing: instant-raycast block attach, then client pendulum physics while held.
 */
public class BlackwhipChainSwingAbility extends Ability {

    private static final float MIN_ROPE = 2.0f;
    private static final float MAX_ROPE = 48.0f;
    private static final float REEL_SPEED = 0.35f;
    private static final float DAMPING = 0.994f;

    private static final Map<UUID, SwingSession> SESSIONS = new ConcurrentHashMap<>();

    private record SwingSession(int chainId, Vec3 anchor, float ropeLength) {
    }

    public static final MapCodec<BlackwhipChainSwingAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(28.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("release_up_boost", new StaticValue(0.18f)).forGetter((ab) -> ab.releaseUpBoost),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(0.85f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("segment_count", new StaticValue(10.0f)).forGetter((ab) -> ab.segmentCount),
                    Value.CODEC.optionalFieldOf("link_length", new StaticValue(0.9f)).forGetter((ab) -> ab.linkLength),
                    Value.CODEC.optionalFieldOf("chain_hp", new StaticValue(24.0f)).forGetter((ab) -> ab.chainHp),
                    Value.CODEC.optionalFieldOf("max_distance", new StaticValue(48.0f)).forGetter((ab) -> ab.maxDistance),
                    Value.CODEC.optionalFieldOf("pump_accel", new StaticValue(0.038f)).forGetter((ab) -> ab.pumpAccel),
                    Value.CODEC.optionalFieldOf("qf_pump_bonus", new StaticValue(0.008f)).forGetter((ab) -> ab.qfPumpBonus),
                    Value.CODEC.optionalFieldOf("max_speed", new StaticValue(2.4f)).forGetter((ab) -> ab.maxSpeed),
                    Value.CODEC.optionalFieldOf("qf_speed_bonus", new StaticValue(0.12f)).forGetter((ab) -> ab.qfSpeedBonus),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainSwingAbility::new));

    public final Value range;
    public final Value releaseUpBoost;
    public final Value thickness;
    public final Value segmentCount;
    public final Value linkLength;
    public final Value chainHp;
    public final Value maxDistance;
    public final Value pumpAccel;
    public final Value qfPumpBonus;
    public final Value maxSpeed;
    public final Value qfSpeedBonus;

    public BlackwhipChainSwingAbility(Value range, Value releaseUpBoost, Value thickness, Value segmentCount,
                                      Value linkLength, Value chainHp, Value maxDistance, Value pumpAccel,
                                      Value qfPumpBonus, Value maxSpeed, Value qfSpeedBonus,
                                      AbilityProperties properties, AbilityStateManager conditions,
                                      List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.releaseUpBoost = releaseUpBoost;
        this.thickness = thickness;
        this.segmentCount = segmentCount;
        this.linkLength = linkLength;
        this.chainHp = chainHp;
        this.maxDistance = maxDistance;
        this.pumpAccel = pumpAccel;
        this.qfPumpBonus = qfPumpBonus;
        this.maxSpeed = maxSpeed;
        this.qfSpeedBonus = qfSpeedBonus;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        stopSwing(player, level, false);

        BlackwhipWebSwingAbility.forceStop(player);
        BlackwhipChainZipAbility.forceStop(player);
        BlackwhipChainChargeZipAbility.forceStop(player);
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(),
                BlackwhipChainEntity.PURPOSE_WEB_SWING,
                BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);

        double range = this.range.getAsFloat(context);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        BlockHitResult hit = level.clip(new ClipContext(
                eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.45f, 0.55f);
            return;
        }

        // Latch on the hit face (nudged along the face normal), not the block's top.
        Vec3 anchor = BlackwhipChainAnchors.surfaceAttachPoint(hit);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnAnchoredChain(
                player, anchor, hit.getBlockPos(), BlackwhipChainEntity.PURPOSE_SWING,
                Math.max(4, this.segmentCount.getAsInt(context)),
                this.linkLength.getAsFloat(context),
                this.chainHp.getAsFloat(context),
                this.thickness.getAsFloat(context),
                this.maxDistance.getAsFloat(context),
                2,
                0);
        if (chain == null) {
            return;
        }

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        float ropeLength = (float) center.distanceTo(anchor);
        float pump = this.pumpAccel.getAsFloat(context) + (float) (qf * this.qfPumpBonus.getAsFloat(context));
        float speedCap = this.maxSpeed.getAsFloat(context) * (1.0f + (float) (qf * this.qfSpeedBonus.getAsFloat(context)));

        SESSIONS.put(player.getUUID(), new SwingSession(chain.getId(), anchor, ropeLength));
        PacketDistributor.sendToPlayer(player, new BlackwhipChainSwingPayload(
                true,
                anchor.x, anchor.y, anchor.z,
                ropeLength,
                MIN_ROPE,
                MAX_ROPE,
                REEL_SPEED,
                pump,
                DAMPING,
                speedCap));
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            SwingSession session = SESSIONS.get(player.getUUID());
            if (session == null) {
                return super.tick(entity, abilityInstance, enabled);
            }
            if (!(level.getEntity(session.chainId()) instanceof BlackwhipChainEntity chain) || !chain.isAnchored()) {
                stopSwing(player, level, false);
                return super.tick(entity, abilityInstance, enabled);
            }

            // Server only verifies the chain is still alive — no positional yank.
            // Client owns swing constraint; server teleports fight collision and cause jitter.
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        SwingSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double upBoost = this.releaseUpBoost.getAsFloat(context);
        if (upBoost > 0.0) {
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(vel.x, vel.y + upBoost, vel.z);
        }
        stopSwing(player, level, true);
    }

    private void stopSwing(ServerPlayer player, ServerLevel level, boolean playBreak) {
        SwingSession session = SESSIONS.remove(player.getUUID());
        if (session != null && level.getEntity(session.chainId()) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
            if (playBreak) {
                level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.55f, 1.2f);
            }
        } else {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_SWING);
        }
        PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());
    }

    /** Clears any active swing for mutex with zip / detach. */
    public static void forceStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SwingSession session = SESSIONS.remove(player.getUUID());
        if (session != null && level.getEntity(session.chainId()) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
        } else {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_SWING);
        }
        PacketDistributor.sendToPlayer(player, BlackwhipChainSwingPayload.stop());
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_SWING.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainSwingAbility> {
        public MapCodec<BlackwhipChainSwingAbility> codec() {
            return BlackwhipChainSwingAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainSwingAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Latch a chain to a surface and swing. Hold WASD to pump momentum along the arc. Space climbs up the chain, Shift slides down. Release keeps momentum.")
                    .add("range", TYPE_VALUE, "Look-raycast attach range.")
                    .add("release_up_boost", TYPE_VALUE, "Small upward velocity added on release.")
                    .add("pump_accel", TYPE_VALUE, "Tangential accel applied while holding movement keys.")
                    .add("qf_pump_bonus", TYPE_VALUE, "Extra pump accel per quirk factor.")
                    .add("max_speed", TYPE_VALUE, "Soft swing speed cap before quirk scaling.")
                    .add("qf_speed_bonus", TYPE_VALUE, "Extra max-speed multiplier per quirk factor.")
                    .addExampleObject(new BlackwhipChainSwingAbility(
                            new StaticValue(28.0f), new StaticValue(0.18f), new StaticValue(0.85f),
                            new StaticValue(10.0f), new StaticValue(0.9f), new StaticValue(24.0f),
                            new StaticValue(48.0f), new StaticValue(0.038f), new StaticValue(0.008f),
                            new StaticValue(2.4f), new StaticValue(0.12f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
