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
 * Chain Puppet — telekinesis via Blackwhip tethers.
 * <p>
 * While held, tagged targets are dragged toward a hold point along the player's look vector.
 * {@code mode} picks who moves ({@code single} vs {@code all}). Mouse aim steers the hold point;
 * with Lock active, scroll extends/retracts locked tether length. Heavier hitboxes resist movement
 * (scaled by {@code yha:strength}); overloaded locked leads can drag the owner instead.
 */
public class BlackwhipMoveTaggedAbility extends Ability {

    private static final double MIN_MASS_SCALE = 0.04;
    /** Strain above this applies Slowness / movement drag (0 = effortless, 1 = immovable). */
    private static final double SLOWNESS_STRAIN_THRESHOLD = 0.08;
    private static final int SLOWNESS_DURATION_TICKS = 10;
    private static final int MAX_SLOWNESS_AMPLIFIER = 4;
    /** Horizontal speed kept at full strain (effect stacks on top). */
    private static final double MIN_PLAYER_MOVE_FACTOR = 0.18;

    public static final MapCodec<BlackwhipMoveTaggedAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("hold_distance", new StaticValue(5.0f)).forGetter((ab) -> ab.holdDistance),
                    Value.CODEC.optionalFieldOf("pull_factor", new StaticValue(0.5f)).forGetter((ab) -> ab.pullFactor),
                    Value.CODEC.optionalFieldOf("move_step", new StaticValue(1.4f)).forGetter((ab) -> ab.moveStep),
                    Codec.STRING.optionalFieldOf("mode", "all").forGetter((ab) -> ab.mode),
                    Codec.DOUBLE.optionalFieldOf("reference_volume", BlackwhipChainLeadPhysics.DEFAULT_REFERENCE_VOLUME)
                            .forGetter((ab) -> ab.referenceVolume),
                    Codec.DOUBLE.optionalFieldOf("reel_step", 0.5).forGetter((ab) -> ab.reelStep),
                    Value.CODEC.optionalFieldOf("reel_min_length", new StaticValue(0.5f)).forGetter((ab) -> ab.reelMinLength),
                    Value.CODEC.optionalFieldOf("reel_max_length", new StaticValue(28.0f)).forGetter((ab) -> ab.reelMaxLength),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipMoveTaggedAbility::new));

    /**
     * Default hold point distance along look (blocks from eyes).
     * Not a reel limit — with Lead locked, scroll owns range via {@link #reelMinLength}/{@link #reelMaxLength}.
     */
    public final Value holdDistance;
    /** How strongly each tick applies {@link #moveStep} (roughly 0–1; 1 snaps fully). */
    public final Value pullFactor;
    /**
     * Base blocks a player-sized target travels toward the hold point each tick.
     * Scaled down for heavier targets via mass / strength.
     */
    public final Value moveStep;
    /** {@code all} = every tethered target; {@code single} = looked-at / nearest only. */
    public final String mode;
    /** Hitbox volume treated as "player-sized" when computing mass scale. */
    public final double referenceVolume;
    /** Blocks of tether length changed per scroll notch while Lead is on. */
    public final double reelStep;
    /** Shortest locked leash length scroll may retract to. */
    public final Value reelMinLength;
    /** Farthest locked leash length scroll may extend to (also capped by the tag's break distance). */
    public final Value reelMaxLength;

    public BlackwhipMoveTaggedAbility(Value holdDistance, Value pullFactor, Value moveStep, String mode,
                                      double referenceVolume, double reelStep, Value reelMinLength,
                                      Value reelMaxLength, AbilityProperties properties,
                                      AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.holdDistance = holdDistance;
        this.pullFactor = pullFactor;
        this.moveStep = moveStep;
        this.mode = mode;
        this.referenceVolume = Math.max(0.01, referenceVolume);
        this.reelStep = Math.max(0.05, reelStep);
        this.reelMinLength = reelMinLength;
        this.reelMaxLength = reelMaxLength;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            double minLength = Math.max(0.25, this.reelMinLength.getAsFloat(context));
            double maxLength = Math.max(minLength, this.reelMaxLength.getAsFloat(context));

            int lockedTargetId = -1;
            if ("single".equalsIgnoreCase(this.mode)) {
                List<LivingEntity> pick = BlackwhipChainTagStore.resolveTargets(player, "single");
                lockedTargetId = pick.isEmpty() ? -1 : pick.getFirst().getId();
            }
            BlackwhipChainTagStore.startReelSession(
                    player, this.mode, this.reelStep, minLength, maxLength, lockedTargetId);
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
            double pullFactor = Math.max(0.0, this.pullFactor.getAsFloat(context));
            double moveStep = Math.max(0.0, this.moveStep.getAsFloat(context));
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
                puppetTarget(player, target, eye, look, holdDistance, pullFactor, moveStep, strength);
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    /**
     * Drags one tagged entity toward the look-based hold point, applying mass scale and
     * Lead-lock override (scroll-owned tether length).
     */
    private void puppetTarget(ServerPlayer player, LivingEntity target, Vec3 eye, Vec3 look,
                              double holdDistance, double pullFactor, double moveStep, double strength) {
        BlackwhipChainEntity chain = BlackwhipChainTagStore.getChainForTarget(player, target.getId());
        double massScale = BlackwhipChainLeadPhysics.massScale(target, strength, this.referenceVolume);

        // Overloaded locked leads: heavy / stronger targets tow the owner along the tether.
        if (chain != null && chain.isLengthLocked()) {
            BlackwhipChainLeadPhysics.applyPuppetDrag(
                    player, target, chain.getLockedLeashLength(), strength, this.referenceVolume);
        }

        if (massScale < MIN_MASS_SCALE) {
            return;
        }

        // Lead lock: scroll owns range — hold on the locked leash instead of hold_distance.
        double effectiveHold = holdDistance;
        if (chain != null && chain.isLengthLocked()) {
            effectiveHold = Math.max(0.5, chain.getLockedLeashLength());
        }
        Vec3 holdPoint = eye.add(look.scale(effectiveHold));

        double step = moveStep * massScale;
        double pull = pullFactor * massScale;
        Vec3 toHold = holdPoint.subtract(target.getBoundingBox().getCenter());
        double dist = toHold.length();
        Vec3 velocity = dist < 1.0e-3
                ? Vec3.ZERO
                : toHold.scale(Math.min(dist, step) / dist).scale(pull);

        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        target.fallDistance = 0;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
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
            // Puppet reel cap, further limited by the tag's break distance when set.
            double maxLen = session.maxLength();
            if (entry != null && entry.maxDistance() > 0) {
                maxLen = Math.min(maxLen, entry.maxDistance());
            }
            double newLen = Mth.clamp(chain.getLockedLeashLength() + delta, session.minLength(), maxLen);
            chain.setLockedLeashLength(newLen);

            // Snap along look to the new leash length so reel feels immediate (not capped by hold_distance).
            Vec3 holdPoint = eye.add(look.scale(Math.max(0.5, newLen)));
            Vec3 toHold = holdPoint.subtract(target.getBoundingBox().getCenter());
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
        return AbilityRegister.BLACKWHIP_MOVE_TAGGED.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipMoveTaggedAbility> {
        public MapCodec<BlackwhipMoveTaggedAbility> codec() {
            return BlackwhipMoveTaggedAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipMoveTaggedAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription(
                            "Telekinesis for chain-tagged entities: hold to drag them to a look-based hold point. "
                                    + "Mode selects single vs all targets. With lock on, scroll reels tether length. "
                                    + "Larger/stronger targets resist and can drag the owner.")
                    .add("hold_distance", TYPE_VALUE,
                            "Default hold point distance along look (ignored while Lead lock owns range). Not the scroll max.")
                    .add("pull_factor", TYPE_VALUE,
                            "How hard each tick applies move_step toward the hold point (0 = none, 1 = full step).")
                    .add("move_step", TYPE_VALUE,
                            "Base blocks a player-sized target moves toward the hold point each tick; heavier targets scale this down.")
                    .add("mode", TYPE_STRING,
                            "'all' moves every tethered entity; 'single' moves only the looked-at/nearest one.")
                    .add("reference_volume", TYPE_FLOAT,
                            "Hitbox volume treated as player-sized for mass scaling.")
                    .add("reel_step", TYPE_FLOAT,
                            "Blocks of tether length changed per scroll notch while Lead is on.")
                    .add("reel_min_length", TYPE_VALUE,
                            "Shortest locked leash length allowed when scrolling in.")
                    .add("reel_max_length", TYPE_VALUE,
                            "Farthest locked leash length allowed when scrolling out (also capped by the tag's max_distance).")
                    .addExampleObject(new BlackwhipMoveTaggedAbility(
                            new StaticValue(5.0f), new StaticValue(0.5f), new StaticValue(1.4f), "all",
                            BlackwhipChainLeadPhysics.DEFAULT_REFERENCE_VOLUME, 0.5,
                            new StaticValue(0.5f), new StaticValue(28.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
