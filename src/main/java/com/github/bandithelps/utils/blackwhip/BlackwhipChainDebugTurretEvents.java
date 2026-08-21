package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug harness: armor stands that periodically shoot flying Blackwhip chains via
 * {@link BlackwhipChainHelper#spawnFlyingChain}. Does not alter chain IK/damage logic.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BlackwhipChainDebugTurretEvents {

    public static final String TAG_KEY = "yha_bw_debug_turret";
    public static final String CHAIN_ID_KEY = "yha_bw_debug_chain_id";
    public static final String DISPLAY_NAME = "BW Debug Turret";

    public static final int FIRE_INTERVAL_TICKS = 40;
    public static final double AIM_RANGE = 18.0;
    public static final int SEGMENT_COUNT = 4;
    public static final float LINK_LENGTH = 0.85f;
    public static final float CHAIN_HP = 18.0f;
    public static final float THICKNESS = 1.0f;
    public static final int TRAVEL_TICKS = 12;

    private record TurretHandle(ResourceKey<Level> dimension, int entityId) {
    }

    private static final Map<UUID, TurretHandle> TURRETS = new ConcurrentHashMap<>();

    private BlackwhipChainDebugTurretEvents() {
    }

    public static ArmorStand summon(ServerLevel level, Vec3 pos, float yRot) {
        ArmorStand stand = new ArmorStand(level, pos.x, pos.y, pos.z);
        stand.setCustomName(Component.literal(DISPLAY_NAME));
        stand.setCustomNameVisible(true);
        stand.setNoGravity(true);
        stand.setInvisible(false);
        stand.setShowArms(true);
        stand.setYRot(yRot);
        stand.setYHeadRot(yRot);
        stand.getPersistentData().putBoolean(TAG_KEY, true);
        stand.getPersistentData().putInt(CHAIN_ID_KEY, -1);
        level.addFreshEntity(stand);
        register(stand);
        return stand;
    }

    public static boolean isTurret(Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentData().getBoolean(TAG_KEY).orElse(false);
    }

    public static void register(ArmorStand stand) {
        if (!(stand.level() instanceof ServerLevel level)) {
            return;
        }
        TURRETS.put(stand.getUUID(), new TurretHandle(level.dimension(), stand.getId()));
    }

    public static int clearAll(MinecraftServer server) {
        int removed = 0;
        List<UUID> ids = new ArrayList<>(TURRETS.keySet());
        for (UUID id : ids) {
            TurretHandle handle = TURRETS.remove(id);
            if (handle == null) {
                continue;
            }
            ServerLevel level = server.getLevel(handle.dimension());
            if (level == null) {
                continue;
            }
            Entity entity = level.getEntity(handle.entityId());
            if (entity instanceof ArmorStand stand && isTurret(stand)) {
                retractOwnedChain(stand);
                stand.discard();
                removed++;
            }
        }
        // Sweep any loaded turrets that missed the registry (e.g. after chunk reload).
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ArmorStand stand && isTurret(stand) && stand.isAlive()) {
                    retractOwnedChain(stand);
                    TURRETS.remove(stand.getUUID());
                    stand.discard();
                    removed++;
                }
            }
        }
        return removed;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ArmorStand stand && isTurret(stand)) {
            register(stand);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (TURRETS.isEmpty()) {
            return;
        }
        List<UUID> stale = new ArrayList<>();
        for (Map.Entry<UUID, TurretHandle> entry : TURRETS.entrySet()) {
            TurretHandle handle = entry.getValue();
            ServerLevel level = server.getLevel(handle.dimension());
            if (level == null) {
                stale.add(entry.getKey());
                continue;
            }
            Entity entity = level.getEntity(handle.entityId());
            if (!(entity instanceof ArmorStand stand) || !stand.isAlive() || !isTurret(stand)) {
                stale.add(entry.getKey());
                continue;
            }
            tickTurret(stand);
        }
        for (UUID id : stale) {
            TURRETS.remove(id);
        }
    }

    private static void tickTurret(ArmorStand stand) {
        if (!(stand.level() instanceof ServerLevel level)) {
            return;
        }
        if (hasActiveOwnedChain(stand)) {
            return;
        }
        // Pace shots so deploy/latch can be observed.
        if (stand.tickCount % FIRE_INTERVAL_TICKS != 0) {
            return;
        }

        Player target = level.getNearestPlayer(stand, AIM_RANGE);
        Vec3 dir;
        if (target != null && target.isAlive()) {
            Vec3 from = stand.getEyePosition();
            Vec3 to = target.getEyePosition();
            dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-6) {
                dir = stand.getLookAngle();
            } else {
                dir = dir.normalize();
            }
            faceDirection(stand, dir);
        } else {
            dir = stand.getLookAngle();
            if (dir.lengthSqr() < 1.0e-6) {
                return;
            }
        }

        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                stand,
                dir,
                AIM_RANGE,
                SEGMENT_COUNT,
                LINK_LENGTH,
                CHAIN_HP,
                THICKNESS,
                TRAVEL_TICKS,
                0,
                AIM_RANGE + 14.0,
                1);
        if (chain != null) {
            stand.getPersistentData().putInt(CHAIN_ID_KEY, chain.getId());
        }
    }

    private static boolean hasActiveOwnedChain(ArmorStand stand) {
        if (!(stand.level() instanceof ServerLevel level)) {
            return false;
        }
        int storedId = stand.getPersistentData().getInt(CHAIN_ID_KEY).orElse(-1);
        if (storedId >= 0) {
            Entity e = level.getEntity(storedId);
            if (e instanceof BlackwhipChainEntity chain && chain.isAlive() && chain.isActive()) {
                return true;
            }
            stand.getPersistentData().putInt(CHAIN_ID_KEY, -1);
        }
        // Fallback: any active chain owned by this stand.
        return BlackwhipChainEntity.findOwnedActive(stand.getId()) != null;
    }

    private static void retractOwnedChain(ArmorStand stand) {
        BlackwhipChainEntity.retractAllOwned(stand.getId());
        stand.getPersistentData().putInt(CHAIN_ID_KEY, -1);
    }

    private static void faceDirection(ArmorStand stand, Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
        stand.setYRot(yaw);
        stand.setYHeadRot(yaw);
        stand.setXRot(pitch);
    }
}
