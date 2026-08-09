package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.attributes.StrengthAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Shared Lead / Puppet contest math: hitbox load vs {@code yha:strength}, and taut-tether tug-of-war.
 */
public final class BlackwhipChainLeadPhysics {

    public static final double DEFAULT_REFERENCE_VOLUME = 0.6 * 0.6 * 1.8;
    public static final double MASS_LOAD_EXPONENT = 2.0;

    private BlackwhipChainLeadPhysics() {
    }

    public static double readStrength(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(StrengthAttributes.STRENGTH);
        if (instance == null) {
            return StrengthAttributes.STRENGTH_DEFAULT;
        }
        return Math.max(0.0, instance.getValue());
    }

    public static double relativeMass(LivingEntity target, double referenceVolume) {
        var box = target.getBoundingBox();
        double volume = Math.max(1.0e-4, box.getXsize() * box.getYsize() * box.getZsize());
        return Math.max(volume / Math.max(1.0e-4, referenceVolume), 0.25);
    }

    public static double loadContribution(LivingEntity target, double referenceVolume) {
        return Math.pow(relativeMass(target, referenceVolume), MASS_LOAD_EXPONENT);
    }

    public static double loadContribution(LivingEntity target) {
        return loadContribution(target, DEFAULT_REFERENCE_VOLUME);
    }

    /**
     * How hard a target pulls in a lead contest. Players use {@code yha:strength} (at least their
     * hitbox load so a bulky strong player still yanks); mobs use hitbox load.
     */
    public static double contestPower(LivingEntity target, double referenceVolume) {
        double load = loadContribution(target, referenceVolume);
        if (target instanceof ServerPlayer) {
            return Math.max(readStrength(target), load);
        }
        return load;
    }

    public static double contestPower(LivingEntity target) {
        return contestPower(target, DEFAULT_REFERENCE_VOLUME);
    }

    public static double massScale(LivingEntity target, double ownerStrength, double referenceVolume) {
        return Mth.clamp(ownerStrength / loadContribution(target, referenceVolume), 0.0, 1.0);
    }

    /**
     * While the tether is past {@code leash} length: if the target outpowers the owner they drag
     * the owner along the lead; otherwise the owner soft-reels the target in.
     *
     * @param springStrength Lead reel strength when the owner wins
     */
    public static void applyTautContest(ServerPlayer owner, LivingEntity target, double leash,
                                        double springStrength, double referenceVolume) {
        Vec3 ownerPos = owner.position();
        Vec3 targetPos = target.position();
        Vec3 toOwner = ownerPos.subtract(targetPos);
        double dist = toOwner.length();
        if (dist <= leash || dist < 1.0e-3) {
            return;
        }

        double over = dist - leash;
        double ownerPow = readStrength(owner);
        double targetPow = contestPower(target, referenceVolume);

        if (targetPow > ownerPow + 1.0e-3) {
            // Target wins: yank the owner toward them; leave only a weak reel on the target.
            double drag = Mth.clamp((targetPow - ownerPow) / Math.max(targetPow, 1.0e-3), 0.0, 1.0);
            Vec3 towardTarget = targetPos.subtract(ownerPos).scale(1.0 / dist);
            double yank = Math.min(over * 0.28 * (0.45 + drag) + drag * 0.22, 1.45);
            owner.setDeltaMovement(owner.getDeltaMovement().scale(0.65).add(towardTarget.scale(yank)));
            owner.hurtMarked = true;
            owner.fallDistance = 0;

            double reel = Math.min(over * 0.12 * springStrength * (1.0 - drag), 0.55);
            if (reel > 1.0e-3) {
                Vec3 dir = toOwner.scale(1.0 / dist);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.8).add(dir.scale(reel)));
                target.hurtMarked = true;
                target.fallDistance = 0;
            }
            return;
        }

        // Owner wins: soft-spring the target back (classic Lead).
        double strength = Math.max(0.0, springStrength);
        Vec3 dir = toOwner.scale(1.0 / dist);
        Vec3 pull = dir.scale(Math.min(over * 0.2 * strength, 1.2));
        target.setDeltaMovement(target.getDeltaMovement().scale(0.6).add(pull));
        target.hurtMarked = true;
        target.fallDistance = 0;
    }

    /**
     * While Puppet is struggling with an overloaded target on a locked lead, let the target drag
     * the owner when the tether is taut (or nearly taut).
     */
    public static void applyPuppetDrag(ServerPlayer owner, LivingEntity target, double leash,
                                       double ownerStrength, double referenceVolume) {
        double targetPow = contestPower(target, referenceVolume);
        if (targetPow <= ownerStrength + 1.0e-3) {
            return;
        }
        Vec3 ownerPos = owner.position();
        Vec3 targetPos = target.position();
        double dist = ownerPos.distanceTo(targetPos);
        if (dist < 1.0e-3) {
            return;
        }
        // Start dragging a bit before full taut so heavy targets can tow you while you try to puppet.
        double tautStart = Math.max(0.5, leash * 0.92);
        if (dist < tautStart) {
            return;
        }
        double drag = Mth.clamp((targetPow - ownerStrength) / Math.max(targetPow, 1.0e-3), 0.0, 1.0);
        double over = Math.max(0.0, dist - leash);
        Vec3 towardTarget = targetPos.subtract(ownerPos).scale(1.0 / dist);
        double yank = Math.min((0.12 + over * 0.22) * drag + drag * 0.18, 1.35);
        owner.setDeltaMovement(owner.getDeltaMovement().scale(0.7).add(towardTarget.scale(yank)));
        owner.hurtMarked = true;
        owner.fallDistance = 0;
    }
}
