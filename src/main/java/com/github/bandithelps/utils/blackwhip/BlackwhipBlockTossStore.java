package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.BlackwhipTossedBlockEntity;
import com.github.bandithelps.utils.blockdisplays.BetterBlockDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side hovering blocks for chain Block Toss. Chains fly and wrap on their own; this store
 * owns ripped {@link BetterBlockDisplay} cargo, orbits it around the player, and throws on re-press.
 */
public final class BlackwhipBlockTossStore {

    public record Carry(int chainId, int displayId, BlockState state, float hardness) {
    }

    private static final Map<UUID, List<Carry>> PLAYER_CARRIES = new ConcurrentHashMap<>();
    private static final double ORBIT_RADIUS = 1.25;
    /** Cube center above the top of the player's hitbox so first-person view stays clear. */
    private static final double ORBIT_ABOVE_HEAD = 1.15;
    private static final double ORBIT_Y_BOB = 0.10;
    private static final float ORBIT_DEG_PER_TICK = 2.4f;
    /** Aim at this distance along the look ray so throws converge on the crosshair. */
    private static final double THROW_AIM_DISTANCE = 32.0;

    private BlackwhipBlockTossStore() {
    }

    public static boolean isGrabbable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0f) {
            return false;
        }
        if (state.getBlock().asItem() == Items.AIR) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean hasReadyCarries(ServerPlayer player) {
        List<Carry> carries = PLAYER_CARRIES.get(player.getUUID());
        return carries != null && !carries.isEmpty();
    }

    public static int carryCount(ServerPlayer player) {
        List<Carry> carries = PLAYER_CARRIES.get(player.getUUID());
        return carries == null ? 0 : carries.size();
    }

    public static void completePickup(BlackwhipChainEntity chain) {
        if (!(chain.level() instanceof ServerLevel level)) {
            chain.deactivate();
            return;
        }
        Entity owner = chain.getOwner();
        if (!(owner instanceof ServerPlayer player) || !player.isAlive()) {
            chain.deactivate();
            return;
        }
        BlockPos pos = chain.getTossTargetPos();
        if (pos == null) {
            pos = chain.getSupportPos();
        }
        if (pos == null || !isGrabbable(level, pos)) {
            chain.deactivate();
            return;
        }
        BlockState state = level.getBlockState(pos);
        float hardness = Math.max(0.0f, state.getDestroySpeed(level, pos));
        Vec3 center = Vec3.atCenterOf(pos);
        BetterBlockDisplay display = spawnDisplay(level, state, center);
        chain.followTossDisplay(display);
        level.removeBlock(pos, false);
        PLAYER_CARRIES.computeIfAbsent(player.getUUID(), key -> new ArrayList<>())
                .add(new Carry(chain.getId(), display.getId(), state, hardness));
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.45f, 0.7f);
    }

    public static boolean tossOne(ServerPlayer player, float throwSpeed, float baseDamage,
                                 float damagePerHardness, float knockback) {
        List<Carry> carries = PLAYER_CARRIES.get(player.getUUID());
        if (carries == null || carries.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Carry chosen = pickLookAligned(player, level, carries);
        if (chosen == null) {
            return false;
        }
        carries.remove(chosen);
        Entity display = level.getEntity(chosen.displayId());
        Vec3 from = display != null && display.isAlive()
                ? display.position()
                : player.getEyePosition().add(player.getLookAngle().scale(0.8));
        if (display != null) {
            display.discard();
        }
        if (level.getEntity(chosen.chainId()) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
        }
        BlackwhipTossedBlockEntity thrown = new BlackwhipTossedBlockEntity(
                level, player, chosen.state(), chosen.hardness(), baseDamage, damagePerHardness, knockback);
        thrown.setPos(from.x, from.y, from.z);
        Vec3 eye = player.getEyePosition();
        Vec3 aimPoint = eye.add(player.getLookAngle().scale(THROW_AIM_DISTANCE));
        Vec3 dir = aimPoint.subtract(from);
        if (dir.lengthSqr() < 1.0e-6) {
            dir = player.getLookAngle();
        }
        thrown.shoot(dir.x, dir.y, dir.z, throwSpeed, 0.0f);
        level.addFreshEntity(thrown);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.55f, 0.75f);
        return true;
    }

    public static void onChainBroken(ServerPlayer player, int chainId) {
        List<Carry> carries = PLAYER_CARRIES.get(player.getUUID());
        if (carries == null || carries.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Iterator<Carry> it = carries.iterator();
        while (it.hasNext()) {
            Carry carry = it.next();
            if (carry.chainId() != chainId) {
                continue;
            }
            Entity display = level.getEntity(carry.displayId());
            Vec3 dropAt = display != null ? display.position() : player.position().add(0.0, player.getBbHeight(), 0.0);
            if (display != null) {
                display.discard();
            }
            dropItem(level, dropAt, carry.state());
            it.remove();
            break;
        }
    }

    public static boolean dropAll(ServerPlayer player) {
        List<Carry> carries = PLAYER_CARRIES.remove(player.getUUID());
        boolean dropped = false;
        if (carries != null && !carries.isEmpty() && player.level() instanceof ServerLevel level) {
            for (Carry carry : carries) {
                Entity display = level.getEntity(carry.displayId());
                Vec3 dropAt = display != null ? display.position() : player.position().add(0.0, player.getBbHeight(), 0.0);
                if (display != null) {
                    display.discard();
                }
                dropItem(level, dropAt, carry.state());
                dropped = true;
            }
        }
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_BLOCK_TOSS);
        return dropped;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, server.getTickCount());
        }
    }

    private static void tickPlayer(ServerPlayer player, int tickCount) {
        List<Carry> carries = PLAYER_CARRIES.get(player.getUUID());
        if (carries == null || carries.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<Carry> alive = new ArrayList<>(carries.size());
        for (Carry carry : carries) {
            Entity display = level.getEntity(carry.displayId());
            Entity chainEnt = level.getEntity(carry.chainId());
            boolean displayOk = display instanceof BetterBlockDisplay && display.isAlive();
            boolean chainOk = chainEnt instanceof BlackwhipChainEntity chain
                    && chain.isAlive()
                    && chain.isActive()
                    && chain.getPurpose() == BlackwhipChainEntity.PURPOSE_BLOCK_TOSS;
            if (!displayOk || !chainOk) {
                Vec3 dropAt = displayOk
                        ? display.position()
                        : player.position().add(0.0, player.getBbHeight(), 0.0);
                if (displayOk) {
                    display.discard();
                }
                if (chainOk) {
                    ((BlackwhipChainEntity) chainEnt).deactivate();
                }
                dropItem(level, dropAt, carry.state());
                continue;
            }
            alive.add(carry);
        }
        PLAYER_CARRIES.put(player.getUUID(), alive);
        updateOrbit(player, level, alive, tickCount);
    }

    private static void updateOrbit(ServerPlayer player, ServerLevel level, List<Carry> carries, int tickCount) {
        int n = carries.size();
        if (n <= 0) {
            return;
        }
        double baseYaw = Math.toRadians(tickCount * ORBIT_DEG_PER_TICK);
        Vec3 center = player.position().add(0.0, player.getBbHeight() + ORBIT_ABOVE_HEAD, 0.0);
        for (int i = 0; i < n; i++) {
            Carry carry = carries.get(i);
            if (!(level.getEntity(carry.displayId()) instanceof BetterBlockDisplay display)) {
                continue;
            }
            double angle = baseYaw + (Math.PI * 2.0 * i) / n;
            double bob = Math.sin(baseYaw * 1.7 + i) * ORBIT_Y_BOB;
            display.setPos(
                    center.x + Math.cos(angle) * ORBIT_RADIUS,
                    center.y + bob,
                    center.z + Math.sin(angle) * ORBIT_RADIUS);
        }
    }

    private static Carry pickLookAligned(ServerPlayer player, ServerLevel level, List<Carry> carries) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Carry best = null;
        double bestDot = -2.0;
        for (Carry carry : carries) {
            Entity display = level.getEntity(carry.displayId());
            Vec3 pos = display != null ? display.position() : eye.add(look);
            Vec3 dir = pos.subtract(eye);
            double len = dir.length();
            double dot = len < 1.0e-6 ? 1.0 : dir.normalize().dot(look);
            if (best == null || dot > bestDot) {
                best = carry;
                bestDot = dot;
            }
        }
        return best;
    }

    private static BetterBlockDisplay spawnDisplay(ServerLevel level, BlockState state, Vec3 center) {
        BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setBlock(state);
        display.setScale(new Vector3f(1.0f, 1.0f, 1.0f));
        display.setTranslation(new Vector3f(-0.5f, -0.5f, -0.5f));
        display.setLifetime(-1);
        display.setPos(center.x, center.y, center.z);
        level.addFreshEntity(display);
        return display;
    }

    private static void dropItem(ServerLevel level, Vec3 at, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty()) {
            return;
        }
        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
    }
}
