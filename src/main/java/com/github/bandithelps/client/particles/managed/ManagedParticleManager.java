package com.github.bandithelps.client.particles.managed;

import com.github.bandithelps.cloud.CloudCellPos;
import com.github.bandithelps.cloud.CloudSimConfig;
import com.github.bandithelps.particles.ManagedSmokeParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ManagedParticleManager {
    private static final ManagedParticleManager INSTANCE = new ManagedParticleManager();

    private final List<ManagedParticleInstance> active = new ArrayList<>();
    private final Map<ManagedParticleOwnerKey, List<ManagedParticleInstance>> byOwner = new HashMap<>();
    private final Map<ManagedParticleProfile, ManagedParticleController> controllers = new EnumMap<>(ManagedParticleProfile.class);
    private final DisperseImpulseController disperseImpulseController = new DisperseImpulseController();

    private int droppedSpawnsThisTick;
    private int impulsesThisTick;
    private int ticksSinceLastLog;

    private ManagedParticleManager() {
        this.controllers.put(ManagedParticleProfile.CLOUD_STAGNANT, new StagnantSmokeController());
        this.controllers.put(ManagedParticleProfile.BEAM_STALL, new BeamStallController());
    }

    public static ManagedParticleManager get() {
        return INSTANCE;
    }

    public void tick(Minecraft minecraft) {
        this.droppedSpawnsThisTick = 0;
        this.impulsesThisTick = 0;
        if (minecraft.level == null || minecraft.player == null || this.active.isEmpty()) {
            return;
        }

        int updateBudget = CloudSimConfig.managedMaxUpdatesPerTick();
        RandomSource random = minecraft.level.random;
        ManagedParticleTickContext context = new ManagedParticleTickContext(random, minecraft.player.position());
        int updates = 0;

        Iterator<ManagedParticleInstance> iterator = this.active.iterator();
        while (iterator.hasNext()) {
            ManagedParticleInstance instance = iterator.next();
            if (!instance.isAlive()) {
                removeFromOwnerIndex(instance);
                iterator.remove();
                continue;
            }

            if (updates < updateBudget) {
                this.disperseImpulseController.tick(instance, context);
                ManagedParticleController profileController = this.controllers.get(instance.profile());
                if (profileController != null) {
                    profileController.tick(instance, context);
                }
                instance.incrementTicksAlive();
                updates++;
            }
        }

        if (CloudSimConfig.debugLogging()) {
            this.ticksSinceLastLog++;
            if (this.ticksSinceLastLog >= 100) {
                this.ticksSinceLastLog = 0;
                System.out.println("[YHA ManagedParticles] active=" + this.active.size()
                        + " droppedSpawns=" + this.droppedSpawnsThisTick
                        + " impulses=" + this.impulsesThisTick);
            }
        }
    }

    public boolean spawn(ClientLevel level, ManagedParticleSpawnRequest request) {
        if (!ManagedSmokeParticle.isReady()) {
            this.droppedSpawnsThisTick++;
            return false;
        }

        int maxActive = CloudSimConfig.managedMaxActiveParticles();
        if (this.active.size() >= maxActive) {
            this.droppedSpawnsThisTick++;
            return false;
        }

        ManagedSmokeParticle particle = new ManagedSmokeParticle(
                level,
                request.position().x,
                request.position().y,
                request.position().z,
                request.initialVelocity().x,
                request.initialVelocity().y,
                request.initialVelocity().z
        );
        particle.setLifetimeTicks(request.lifetimeTicks());
        particle.setManagedAlpha(request.alpha());
        particle.setManagedSize(request.sizeScale());

        ManagedParticleInstance instance = new ManagedParticleInstance(
                particle,
                request.ownerKey(),
                request.profile(),
                request.beamStallAfterTicks()
        );
        this.active.add(instance);
        if (request.ownerKey() != null) {
            this.byOwner.computeIfAbsent(request.ownerKey(), ignored -> new ArrayList<>()).add(instance);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.particleEngine != null) {
            minecraft.particleEngine.add(particle);
        }
        return true;
    }

    public boolean spawnCloudSmoke(
            ClientLevel level,
            Vec3 position,
            Vec3 initialVelocity,
            int lifetimeTicks,
            ManagedParticleOwnerKey ownerKey
    ) {
        return spawn(level, new ManagedParticleSpawnRequest(
                position,
                initialVelocity,
                lifetimeTicks,
                0.8F,
                ClientManagedParticleSettings.cloudSmokeSize(),
                ManagedParticleProfile.CLOUD_STAGNANT,
                ownerKey,
                0
        ));
    }

    public boolean spawnBeamSmoke(
            ClientLevel level,
            Vec3 position,
            Vec3 initialVelocity,
            int lifetimeTicks,
            int stallAfterTicks
    ) {
        return spawn(level, new ManagedParticleSpawnRequest(
                position,
                initialVelocity,
                lifetimeTicks,
                0.85F,
                ClientManagedParticleSettings.beamSmokeSize(),
                ManagedParticleProfile.BEAM_STALL,
                null,
                stallAfterTicks
        ));
    }

    public void applyDisperseImpulse(ManagedParticleOwnerKey ownerKey, Vec3 direction, double strength) {
        List<ManagedParticleInstance> owned = this.byOwner.get(ownerKey);
        if (owned == null || owned.isEmpty()) {
            return;
        }

        Vec3 normalized = direction.lengthSqr() > 0.00001D ? direction.normalize() : new Vec3(0.0D, 0.1D, 0.0D);
        Vec3 impulse = normalized.scale(Math.max(0.001D, strength));
        int impulseTicks = CloudSimConfig.managedDisperseImpulseTicks();
        for (ManagedParticleInstance instance : owned) {
            if (!instance.isAlive()) {
                continue;
            }
            instance.setImpulse(impulse, impulseTicks);
            this.impulsesThisTick++;
        }
    }

    public void applyDisperseImpulseForCell(UUID volumeId, CloudCellPos cellPos, Vec3 direction, double strength) {
        applyDisperseImpulse(new ManagedParticleOwnerKey(volumeId, cellPos), direction, strength);
    }

    public void removeOwner(ManagedParticleOwnerKey ownerKey) {
        List<ManagedParticleInstance> owned = this.byOwner.remove(ownerKey);
        if (owned == null) {
            return;
        }
        for (ManagedParticleInstance instance : owned) {
            instance.particle().remove();
        }
    }

    public void removeVolume(UUID volumeId) {
        List<ManagedParticleOwnerKey> removals = new ArrayList<>();
        for (ManagedParticleOwnerKey key : this.byOwner.keySet()) {
            if (key.volumeId().equals(volumeId)) {
                removals.add(key);
            }
        }
        for (ManagedParticleOwnerKey key : removals) {
            removeOwner(key);
        }
    }

    public void clear() {
        for (ManagedParticleInstance instance : this.active) {
            instance.particle().remove();
        }
        this.active.clear();
        this.byOwner.clear();
        this.droppedSpawnsThisTick = 0;
        this.impulsesThisTick = 0;
    }

    public int activeCount() {
        return this.active.size();
    }

    public int droppedSpawnsThisTick() {
        return this.droppedSpawnsThisTick;
    }

    public int impulsesThisTick() {
        return this.impulsesThisTick;
    }

    private void removeFromOwnerIndex(ManagedParticleInstance instance) {
        if (instance.ownerKey() == null) {
            return;
        }
        List<ManagedParticleInstance> owned = this.byOwner.get(instance.ownerKey());
        if (owned == null) {
            return;
        }
        owned.remove(instance);
        if (owned.isEmpty()) {
            this.byOwner.remove(instance.ownerKey());
        }
    }
}
