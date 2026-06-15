package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.blockdisplays.BetterBlockDisplay;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
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
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Block Grab": rips a 1x3 column of blocks out of the world, carries them at the user's shoulder on a
 * Blackwhip tether, then hurls them on release to damage and re-place the column at the impact point.
 * Carried blocks use vanilla block-display entities so they render for everyone and in replays.
 */
public class BlackwhipBlockGrabAbility extends Ability {

    private static final class GrabState {
        final int[] displayIds;
        final BlockState[] states;
        int whipId = -1;

        GrabState(int[] displayIds, BlockState[] states) {
            this.displayIds = displayIds;
            this.states = states;
        }
    }

    private static final Map<UUID, GrabState> GRABS = new ConcurrentHashMap<>();

    public static final MapCodec<BlackwhipBlockGrabAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("reach", new StaticValue(6.0f)).forGetter((ab) -> ab.reach),
                    Value.CODEC.optionalFieldOf("throw_range", new StaticValue(24.0f)).forGetter((ab) -> ab.throwRange),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(8.0f)).forGetter((ab) -> ab.damage),
                    Value.CODEC.optionalFieldOf("scale", new StaticValue(0.7f)).forGetter((ab) -> ab.scale),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.2f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(0.5f)).forGetter((ab) -> ab.curve),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipBlockGrabAbility::new));

    public final Value reach;
    public final Value throwRange;
    public final Value damage;
    public final Value scale;
    public final Value thickness;
    public final Value curve;

    public BlackwhipBlockGrabAbility(Value reach, Value throwRange, Value damage, Value scale, Value thickness, Value curve,
                                    AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.reach = reach;
        this.throwRange = throwRange;
        this.damage = damage;
        this.scale = scale;
        this.thickness = thickness;
        this.curve = curve;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (GRABS.containsKey(player.getUUID())) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(this.reach.getAsFloat(context)));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos base = hit.getBlockPos();
        BlockPos[] positions = {base, base.above(), base.above(2)};
        BlockState[] states = new BlockState[3];
        boolean anyGrabbed = false;
        for (int i = 0; i < 3; i++) {
            BlockState state = level.getBlockState(positions[i]);
            if (isGrabbable(level, positions[i], state)) {
                states[i] = state;
                anyGrabbed = true;
            }
        }
        if (!anyGrabbed) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (states[i] != null) {
                level.removeBlock(positions[i], false);
            }
        }

        float scale = Math.max(0.1f, this.scale.getAsFloat(context));
        int[] displayIds = {-1, -1, -1};
        BetterBlockDisplay tetherDisplay = null;
        for (int i = 0; i < 3; i++) {
            if (states[i] == null) {
                continue;
            }
            BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
            display.setBlock(states[i]);
            display.setScale(new Vector3f(scale, scale, scale));
            display.setTranslation(new Vector3f(-scale * 0.5f, -scale * 0.5f, -scale * 0.5f));
            display.setLifetime(-1);
            level.addFreshEntity(display);
            displayIds[i] = display.getId();
            if (tetherDisplay == null || i == 1) {
                tetherDisplay = display;
            }
        }

        GrabState grab = new GrabState(displayIds, states);
        if (tetherDisplay != null) {
            BlackwhipEntity whip = BlackwhipHelper.spawnBlockGrab(player, tetherDisplay,
                    this.thickness.getAsFloat(context), this.curve.getAsFloat(context), 5);
            grab.whipId = whip.getId();
        }
        GRABS.put(player.getUUID(), grab);
        updateCarry(player, level, grab, scale);
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            GrabState grab = GRABS.get(player.getUUID());
            if (grab != null) {
                updateCarry(player, level, grab, Math.max(0.1f, this.scale.getAsFloat(DataContext.forEntity(entity))));
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        GrabState grab = GRABS.remove(player.getUUID());
        if (grab == null) {
            return;
        }
        for (int id : grab.displayIds) {
            if (id != -1 && level.getEntity(id) instanceof BetterBlockDisplay display) {
                display.discard();
            }
        }
        if (grab.whipId >= 0 && level.getEntity(grab.whipId) instanceof BlackwhipEntity whip) {
            whip.deactivate();
        }

        DataContext context = DataContext.forEntity(entity);
        double range = this.throwRange.getAsFloat(context);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        float damage = this.damage.getAsFloat(context) * (float) (1.0 + 0.1 * qf);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK ? eye.distanceTo(blockHit.getLocation()) : range;

        AABB searchBox = new AABB(eye, end).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox,
                e -> e instanceof LivingEntity && e != player && e.isAlive() && e.isPickable(), range * range);
        LivingEntity hitEntity = entityHit != null && eye.distanceTo(entityHit.getLocation()) < blockDist
                ? (LivingEntity) entityHit.getEntity() : null;

        if (hitEntity != null) {
            hitEntity.hurt(level.damageSources().mobAttack(player), damage);
            Vec3 kb = look.scale(0.6);
            hitEntity.push(kb.x, Math.max(0.25, kb.y), kb.z);
            placeStackAtGround(level, hitEntity.getBoundingBox().getCenter(), grab.states);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            placeStackAdjacent(level, blockHit, grab.states);
        } else {
            placeStackAtGround(level, end, grab.states);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    private void updateCarry(ServerPlayer player, ServerLevel level, GrabState grab, float scale) {
        float yaw = player.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, yaw).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(up).normalize();
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        double shoulder = Math.max(0.45, Math.min(0.9, player.getBbHeight() * 0.78));
        Vec3 carry = player.position().add(0, shoulder + 0.25, 0).add(right.scale(1.1 * side)).add(fwd.scale(0.1));
        Vec3[] offsets = {carry.add(0, -scale, 0), carry, carry.add(0, scale, 0)};
        for (int i = 0; i < 3; i++) {
            if (grab.displayIds[i] != -1 && level.getEntity(grab.displayIds[i]) instanceof BetterBlockDisplay display) {
                display.setPos(offsets[i].x, offsets[i].y, offsets[i].z);
            }
        }
    }

    private boolean isGrabbable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0.0f;
    }

    private boolean canOccupy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private void placeStackAdjacent(ServerLevel level, BlockHitResult hit, BlockState[] states) {
        Direction face = hit.getDirection();
        BlockPos anchor = hit.getBlockPos().relative(face);
        placeColumn(level, anchor, states);
    }

    private void placeStackAtGround(ServerLevel level, Vec3 where, BlockState[] states) {
        BlockPos probe = BlockPos.containing(where);
        for (int i = 0; i < 6 && level.getBlockState(probe).isAir(); i++) {
            probe = probe.below();
        }
        BlockPos base = level.getBlockState(probe).isAir() ? BlockPos.containing(where) : probe.above();
        placeColumn(level, base, states);
    }

    private void placeColumn(ServerLevel level, BlockPos base, BlockState[] states) {
        for (int i = 0; i < 3; i++) {
            if (states[i] == null) {
                continue;
            }
            BlockPos target = base.above(i);
            if (canOccupy(level, target)) {
                level.setBlock(target, states[i], 3);
            }
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_BLOCK_GRAB.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipBlockGrabAbility> {
        public MapCodec<BlackwhipBlockGrabAbility> codec() {
            return BlackwhipBlockGrabAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipBlockGrabAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Rips a 1x3 column of blocks from the world, carries them at the shoulder on a Blackwhip tether, then hurls them on release to damage and re-place the column at the impact point.")
                    .add("reach", TYPE_VALUE, "How far the grab raycast reaches.")
                    .add("throw_range", TYPE_VALUE, "How far the thrown column travels / scans for hits.")
                    .add("damage", TYPE_VALUE, "Base impact damage to a hit entity (quirk-factor scaled).")
                    .add("scale", TYPE_VALUE, "Render scale of the carried blocks.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("curve", TYPE_VALUE, "Visual whip curve amount.")
                    .addExampleObject(new BlackwhipBlockGrabAbility(new StaticValue(6.0f), new StaticValue(24.0f), new StaticValue(8.0f),
                            new StaticValue(0.7f), new StaticValue(1.2f), new StaticValue(0.5f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
