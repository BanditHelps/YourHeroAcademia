package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.BlackwhipSegmentEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Owner/target click-through for chain segments, plus explosion damage against chain joints.
 * Projectile pass-through is handled on {@link BlackwhipSegmentEntity#canBeHitByProjectile()}.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BlackwhipChainHitEvents {

    private BlackwhipChainHitEvents() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof BlackwhipSegmentEntity seg)) {
            return;
        }
        BlackwhipChainEntity chain = seg.getChain();
        if (chain == null) {
            return;
        }
        Player player = event.getEntity();
        if (!chain.isParticipant(player)) {
            return; // third party: allow normal segment damage
        }

        event.setCanceled(true);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        attackThrough(serverPlayer, seg);
    }

    private static void attackThrough(ServerPlayer player, BlackwhipSegmentEntity ignored) {
        double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(reach));
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                sweep,
                e -> e.isAlive()
                        && e.isPickable()
                        && e != player
                        && !(e instanceof BlackwhipSegmentEntity)
                        && !isOwnChainController(e, player),
                reach * reach);

        if (hit != null) {
            player.attack(hit.getEntity());
        }
    }

    private static boolean isOwnChainController(Entity e, Player player) {
        return e instanceof BlackwhipChainEntity chain && chain.isParticipant(player);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Explosion explosion = event.getExplosion();
        Vec3 center = explosion.center();
        // Conservative radius from affected area; joints near any affected entity also get checked.
        double radius = 8.0;
        if (!event.getAffectedBlocks().isEmpty()) {
            radius = Math.max(radius, Math.sqrt(event.getAffectedBlocks().size()) * 0.6);
        }
        double radiusSqr = radius * radius;
        float damage = Math.max(4.0f, (float) radius * 1.5f);
        DamageSource source = level.damageSources().explosion(
                explosion.getDirectSourceEntity(),
                explosion.getIndirectSourceEntity());

        for (BlackwhipChainEntity chain : BlackwhipChainEntity.activeServerChains()) {
            if (!chain.isAlive() || !chain.isActive() || chain.level() != level) {
                continue;
            }
            double best = Double.MAX_VALUE;
            for (Vec3 joint : chain.jointPositions()) {
                best = Math.min(best, joint.distanceToSqr(center));
            }
            if (best <= radiusSqr) {
                float falloff = (float) (1.0 - Math.sqrt(best) / radius);
                chain.damageFromSegment(source, Math.max(2.0f, damage * falloff));
            }
        }
    }
}
