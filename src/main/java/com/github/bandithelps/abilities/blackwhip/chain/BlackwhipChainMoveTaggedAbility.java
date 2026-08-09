package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainReelSessionPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainLeadPhysics;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

/**
 * Chain Puppet: while held, drags tagged chain targets to a hold point in front of the crosshair.
 * Larger hitboxes are harder to move; {@code yha:strength} offsets that mass scale.
 * With Lead on, mouse scroll extends/retracts locked tether length; overloaded targets can drag you.
 */
public class BlackwhipChainMoveTaggedAbility extends Ability {

    private static final double MIN_MASS_SCALE = 0.04;
    /** Strain above this applies Slowness / movement drag (0 = effortless, 1 = immovable). */
    private static final double SLOWNESS_STRAIN_THRESHOLD = 0.08;
    private static final int SLOWNESS_DURATION_TICKS = 10;
    private static final int MAX_SLOWNESS_AMPLIFIER = 4;
    /** Horizontal speed kept at full strain (effect stacks on top). */
    private static final double MIN_PLAYER_MOVE_FACTOR = 0.18;

    public static final MapCodec<BlackwhipChainMoveTaggedAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("hold_distance", new StaticValue(5.0f)).forGetter((ab) -> ab.holdDistance),
                    Value.CODEC.optionalFieldOf("pull_strength", new StaticValue(0.5f)).forGetter((ab) -> ab.pullStrength),
                    Value.CODEC.optionalFieldOf("max_step", new StaticValue(1.4f)).forGetter((ab) -> ab.maxStep),
                    Codec.STRING.optionalFieldOf("mode", "all").forGetter((ab) -> ab.mode),
                    Codec.DOUBLE.optionalFieldOf("reference_volume", BlackwhipChainLeadPhysics.DEFAULT_REFERENCE_VOLUME)
                            .forGetter((ab) -> ab.referenceVolume),
                    Codec.DOUBLE.optionalFieldOf("reel_step", 0.5).forGetter((ab) -> ab.reelStep),
                    Codec.DOUBLE.optionalFieldOf("reel_min_length", 0.5).forGetter((ab) -> ab.reelMinLength),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainMoveTaggedAbility::new));

    public final Value holdDistance;
    public final Value pullStrength;
    public final Value maxStep;
    public final String mode;
    public final double referenceVolume;
    public final double reelStep;
    public final double reelMinLength;

    public BlackwhipChainMoveTaggedAbility(Value holdDistance, Value pullStrength, Value maxStep, String mode,
                                           double referenceVolume, double reelStep, double reelMinLength,
                                           AbilityProperties properties, AbilityStateManager conditions,
                                           List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.holdDistance = holdDistance;
        this.pullStrength = pullStrength;
        this.maxStep = maxStep;
        this.mode = mode;
        this.referenceVolume = Math.max(0.01, referenceVolume);
        this.reelStep = Math.max(0.05, reelStep);
        this.reelMinLength = Math.max(0.25, reelMinLength);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            int lockedTargetId = -1;
            if ("single".equalsIgnoreCase(this.mode)) {
                List<LivingEntity> pick = BlackwhipChainTagStore.resolveTargets(player, "single");
                lockedTargetId = pick.isEmpty() ? -1 : pick.getFirst().getId();
            }
            BlackwhipChainTagStore.startReelSession(
                    player, this.mode, this.reelStep, this.reelMinLength, lockedTargetId);
            PacketDistributor.sendToPlayer(player, BlackwhipChainReelSessionPayload.start(this.mode));
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            player.removeEffect(MobEffects.SLOWNESS);
            BlackwhipChainTagStore.clearPuppeted(player);
            BlackwhipChainTagStore.stopReelSession(player);
            PacketDistributor.sendToPlayer(player, BlackwhipChainReelSessionPayload.stop());
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            double holdDistance = this.holdDistance.getAsFloat(context);
            double pull = Math.max(0.0, this.pullStrength.getAsFloat(context));
            double maxStep = Math.max(0.0, this.maxStep.getAsFloat(context));
            double strength = BlackwhipChainLeadPhysics.readStrength(player);

            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            List<LivingEntity> targets = BlackwhipChainTagStore.resolveSessionTargets(player);
            BlackwhipChainTagStore.markPuppeted(player, targets);

            double load = 0.0;
            for (LivingEntity target : targets) {
                load += BlackwhipChainLeadPhysics.loadContribution(target, this.referenceVolume);
            }
            applyPuppetStrain(player, strength, load);

            for (LivingEntity target : targets) {
                BlackwhipChainEntity chain = BlackwhipChainTagStore.getChainForTarget(player, target.getId());
                double massScale = BlackwhipChainLeadPhysics.massScale(target, strength, this.referenceVolume);

                // Overloaded locked leads: heavy / stronger targets tow the owner along the tether.
                if (chain != null && chain.isLengthLocked()) {
                    BlackwhipChainLeadPhysics.applyPuppetDrag(
                            player, target, chain.getLockedLeashLength(), strength, this.referenceVolume);
                }

                if (massScale < MIN_MASS_SCALE) {
                    continue;
                }
                double scaledPull = pull * massScale;
                double scaledStep = maxStep * massScale;

                // With Lead locked, scroll owns range: hold at the locked leash length.
                double effectiveHold = holdDistance;
                if (chain != null && chain.isLengthLocked()) {
                    effectiveHold = Math.max(0.5, chain.getLockedLeashLength());
                }
                Vec3 hold = eye.add(look.scale(effectiveHold));

                Vec3 to = hold.subtract(target.getBoundingBox().getCenter());
                double dist = to.length();
                Vec3 velocity = dist < 1.0e-3
                        ? Vec3.ZERO
                        : to.scale(Math.min(dist, scaledStep) / dist).scale(scaledPull);
                target.setDeltaMovement(velocity);
                target.hurtMarked = true;
                target.fallDistance = 0;
                if (target instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    /**
     * Applies Slowness + horizontal movement drag from overload ({@code load} vs {@code strength}).
     * Refreshed each tick while held.
     */
    private static void applyPuppetStrain(ServerPlayer player, double strength, double load) {
        if (load <= 1.0e-4) {
            return;
        }
        double ease = Mth.clamp(strength / load, 0.0, 1.0);
        double strain = 1.0 - ease;
        if (strain < SLOWNESS_STRAIN_THRESHOLD) {
            return;
        }

        int amplifier = Mth.clamp((int) Math.floor(strain * 5.0), 0, MAX_SLOWNESS_AMPLIFIER);
        player.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS, SLOWNESS_DURATION_TICKS, amplifier, false, false, false));

        double moveFactor = Mth.lerp(strain, 1.0, MIN_PLAYER_MOVE_FACTOR);
        Vec3 mv = player.getDeltaMovement();
        player.setDeltaMovement(mv.x * moveFactor, mv.y, mv.z * moveFactor);
    }

    /**
     * Applies one scroll notch while Puppet is held. {@code direction > 0} retracts (shortens);
     * {@code < 0} extends. No-op unless Lead is active and a puppet reel session is running.
     */
    public static void handleScroll(ServerPlayer player, int direction) {
        if (direction == 0 || !BlackwhipChainTagStore.isLeadActive(player)) {
            return;
        }
        BlackwhipChainTagStore.ReelSession session = BlackwhipChainTagStore.getReelSession(player);
        if (session == null) {
            return;
        }

        double delta = session.step() * (direction > 0 ? -1.0 : 1.0);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<LivingEntity> targets = BlackwhipChainTagStore.resolveSessionTargets(player);
        for (LivingEntity target : targets) {
            BlackwhipChainEntity chain = BlackwhipChainTagStore.getChainForTarget(player, target.getId());
            if (chain == null || !chain.isLengthLocked()) {
                continue;
            }
            BlackwhipChainTagStore.TagEntry entry = BlackwhipChainTagStore.getTagEntry(player, target.getId());
            double maxLen = entry != null && entry.maxDistance() > 0
                    ? entry.maxDistance()
                    : Math.max(chain.getLockedLeashLength(), 28.0);
            double newLen = Mth.clamp(chain.getLockedLeashLength() + delta, session.minLength(), maxLen);
            chain.setLockedLeashLength(newLen);

            // Snap the hold target along look to the new leash length so extend/retract isn't
            // waiting on the next puppet tick (and isn't capped by hold_distance).
            Vec3 hold = eye.add(look.scale(Math.max(0.5, newLen)));
            Vec3 toHold = hold.subtract(target.getBoundingBox().getCenter());
            double dist = toHold.length();
            if (dist > 1.0e-3) {
                Vec3 push = toHold.scale(Math.min(dist, Math.abs(delta) + 0.35) / dist);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.45).add(push));
                target.hurtMarked = true;
                target.fallDistance = 0;
            }
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_MOVE_TAGGED.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainMoveTaggedAbility> {
        public MapCodec<BlackwhipChainMoveTaggedAbility> codec() {
            return BlackwhipChainMoveTaggedAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainMoveTaggedAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While held, drags chain-tagged entities to a hold point. With Lead on, scroll extends/retracts tether length. Larger/stronger targets can drag the owner.")
                    .add("hold_distance", TYPE_VALUE, "How far in front of the eyes the hold point sits.")
                    .add("pull_strength", TYPE_VALUE, "Base fraction of the gap closed each tick.")
                    .add("max_step", TYPE_VALUE, "Base maximum movement per tick.")
                    .add("mode", TYPE_STRING, "'all' moves every tethered entity; 'single' moves only the looked-at/nearest one.")
                    .add("reference_volume", TYPE_FLOAT, "Hitbox volume treated as 'player-sized' for mass scaling.")
                    .add("reel_step", TYPE_FLOAT, "Blocks changed per scroll notch while Lead is on.")
                    .add("reel_min_length", TYPE_FLOAT, "Shortest locked leash length allowed when scrolling.")
                    .addExampleObject(new BlackwhipChainMoveTaggedAbility(
                            new StaticValue(5.0f), new StaticValue(0.5f), new StaticValue(1.4f), "all",
                            BlackwhipChainLeadPhysics.DEFAULT_REFERENCE_VOLUME, 0.5, 0.5,
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
