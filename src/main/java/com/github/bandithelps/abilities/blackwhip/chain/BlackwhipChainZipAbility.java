package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whip Zip: casts a chain at a surface and applies a single quick velocity burst toward it.
 */
public class BlackwhipChainZipAbility extends Ability {

    private static final float THICKNESS = 0.9f;
    private static final float LINK_LENGTH = 0.85f;
    private static final float CHAIN_HP = 18.0f;
    private static final int SEGMENT_COUNT = 8;
    private static final int MAX_KEEP = 8;
    /** Brief echoes so client move packets don't cancel the burst. */
    private static final int BURST_ECHO_TICKS = 2;
    private static final float UP_BIAS = 0.22f;

    private static final Map<UUID, ZipSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class ZipSession {
        Vec3 burstVelocity = Vec3.ZERO;
        int echoTicksLeft;
        int chainId = -1;
    }

    public static final MapCodec<BlackwhipChainZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(22.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("simple_pull_power", new StaticValue(1.8f)).forGetter((ab) -> ab.simplePullPower),
                    Value.CODEC.optionalFieldOf("qf_pull_bonus", new StaticValue(0.05f)).forGetter((ab) -> ab.qfPullBonus),
                    Value.CODEC.optionalFieldOf("simple_visual_ticks", new StaticValue(10.0f)).forGetter((ab) -> ab.simpleVisualTicks),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainZipAbility::new));

    public final Value range;
    public final Value simplePullPower;
    public final Value qfPullBonus;
    public final Value simpleVisualTicks;

    public BlackwhipChainZipAbility(Value range, Value simplePullPower, Value qfPullBonus, Value simpleVisualTicks,
                                    AbilityProperties properties, AbilityStateManager conditions,
                                    List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.simplePullPower = simplePullPower;
        this.qfPullBonus = qfPullBonus;
        this.simpleVisualTicks = simpleVisualTicks;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlackwhipChainSwingAbility.forceStop(player);
        BlackwhipChainChargeZipAbility.forceStop(player);
        clearSession(player, level);

        DataContext context = DataContext.forEntity(entity);
        double range = this.range.getAsFloat(context);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        BlockHitResult hit = level.clip(new ClipContext(
                eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        Vec3 anchor = BlackwhipChainAnchors.surfaceAttachPoint(hit);
        int visualTicks = Math.max(4, this.simpleVisualTicks.getAsInt(context));
        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnAnchoredChain(
                player, anchor, hit.getBlockPos(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS, range * 1.35, MAX_KEEP, visualTicks);
        if (chain == null) {
            return;
        }

        double qf = QuirkFactorUtil.getQuirkFactor(player);
        float power = this.simplePullPower.getAsFloat(context) * (float) (1.0 + qf * this.qfPullBonus.getAsFloat(context));
        Vec3 burst = computeBurstVelocity(player, anchor, power);

        ZipSession session = new ZipSession();
        session.chainId = chain.getId();
        session.burstVelocity = burst;
        session.echoTicksLeft = BURST_ECHO_TICKS;
        SESSIONS.put(player.getUUID(), session);

        applyBurst(player, burst);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.55f, 1.35f);
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7f, 1.35f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!(entity instanceof ServerPlayer player)) {
            return super.tick(entity, abilityInstance, enabled);
        }
        ZipSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return super.tick(entity, abilityInstance, enabled);
        }

        // Short echoes only — no sustained reel while held.
        if (session.echoTicksLeft > 0) {
            applyBurst(player, session.burstVelocity);
            session.echoTicksLeft--;
            if (session.echoTicksLeft <= 0) {
                SESSIONS.remove(player.getUUID());
            }
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        // Burst already applied on press; release does not tug again.
        if (entity instanceof ServerPlayer player) {
            SESSIONS.remove(player.getUUID());
        }
    }

    private static Vec3 computeBurstVelocity(ServerPlayer player, Vec3 anchor, float power) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        Vec3 dir = dist > 1.0e-3 ? toAnchor.scale(1.0 / dist) : player.getLookAngle().normalize();
        // Mild falloff at point-blank so close zips don't overshoot as hard.
        double distScale = Mth.clamp(dist / 10.0, 0.7, 1.0);
        return dir.scale(power * distScale).add(0.0, UP_BIAS, 0.0);
    }

    private static void applyBurst(ServerPlayer player, Vec3 burst) {
        // Replace velocity — quick zip, not a blended reel.
        player.setDeltaMovement(burst);
        player.hurtMarked = true;
        player.setOnGround(false);
        player.resetFallDistance();
    }

    private void clearSession(ServerPlayer player, ServerLevel level) {
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session != null && session.chainId >= 0
                && level.getEntity(session.chainId) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE);
    }

    /** Clears zip state for mutex with swing / charge zip / detach. */
    public static void forceStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session != null && session.chainId >= 0
                && level.getEntity(session.chainId) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE);
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
            builder.setDescription("Cast a chain and zip toward a surface with a quick velocity burst.")
                    .add("range", TYPE_VALUE, "Raycast reach for the zip anchor.")
                    .add("simple_pull_power", TYPE_VALUE, "Launch velocity toward the attach point.")
                    .add("qf_pull_bonus", TYPE_VALUE, "Extra launch velocity per quirk factor.")
                    .add("simple_visual_ticks", TYPE_VALUE, "How long the chain stays visible after the zip.")
                    .addExampleObject(new BlackwhipChainZipAbility(
                            new StaticValue(22.0f), new StaticValue(1.8f), new StaticValue(0.05f),
                            new StaticValue(10.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
