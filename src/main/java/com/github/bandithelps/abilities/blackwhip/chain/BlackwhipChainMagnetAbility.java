package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.entities.BlackwhipChainEntity;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Toggle: unused grab tethers shoot at nearby dropped items and retract them into inventory.
 */
public class BlackwhipChainMagnetAbility extends Ability {

    public static final MapCodec<BlackwhipChainMagnetAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(8.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("qf_range_bonus", new StaticValue(2.0f)).forGetter((ab) -> ab.qfRangeBonus),
                    Value.CODEC.optionalFieldOf("segment_count", new StaticValue(4.0f)).forGetter((ab) -> ab.segmentCount),
                    Value.CODEC.optionalFieldOf("link_length", new StaticValue(0.85f)).forGetter((ab) -> ab.linkLength),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("travel_ticks", new StaticValue(12.0f)).forGetter((ab) -> ab.travelTicks),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainMagnetAbility::new));

    public final Value range;
    public final Value qfRangeBonus;
    public final Value segmentCount;
    public final Value linkLength;
    public final Value thickness;
    public final Value travelTicks;

    public BlackwhipChainMagnetAbility(Value range, Value qfRangeBonus, Value segmentCount, Value linkLength,
                                       Value thickness, Value travelTicks, AbilityProperties properties,
                                       AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.qfRangeBonus = qfRangeBonus;
        this.segmentCount = segmentCount;
        this.linkLength = linkLength;
        this.thickness = thickness;
        this.travelTicks = travelTicks;
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_MAGNET);
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            shootAtNearbyItems(player, level);
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    private void shootAtNearbyItems(ServerPlayer player, ServerLevel level) {
        DataContext context = DataContext.forEntity(player);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int maxTethers = (int) ((int) BodyAttachments.get(player).getCustomFloat(
                player, BodyPart.CHEST, BlackwhipChainTagAbility.MAX_TETHERS_KEY, 1) + (qf * 2));
        int freeSlots = maxTethers - BlackwhipChainEntity.countOwnedActive(player.getId());
        if (freeSlots <= 0) {
            return;
        }

        float range = Math.max(1.0f, this.range.getAsFloat(context)
                + (float) (qf * this.qfRangeBonus.getAsFloat(context)));
        double rangeSqr = range * (double) range;
        Vec3 origin = player.getEyePosition();
        AABB search = player.getBoundingBox().inflate(range);

        List<ItemEntity> items = new ArrayList<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, search)) {
            if (isMagnetCandidate(player, item, rangeSqr, origin)) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(player)));

        int segments = Math.max(2, this.segmentCount.getAsInt(context));
        float link = this.linkLength.getAsFloat(context);
        float hp = Math.max(1.0f, BodyAttachments.get(player).getCustomFloat(
                player, BodyPart.CHEST, BlackwhipChainTagAbility.CHAIN_HP_KEY, 1.0f));
        float thickness = this.thickness.getAsFloat(context);
        int travel = Math.max(1, this.travelTicks.getAsInt(context));

        int spawned = 0;
        for (ItemEntity item : items) {
            if (spawned >= freeSlots) {
                break;
            }
            Vec3 aim = item.position().add(0.0, item.getBbHeight() * 0.5, 0.0);
            Vec3 dir = aim.subtract(origin);
            if (dir.lengthSqr() < 1.0e-6) {
                dir = player.getLookAngle();
            }
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                    player, dir, range, segments, link, hp, thickness, travel,
                    0, range, maxTethers, BlackwhipChainEntity.PURPOSE_MAGNET);
            if (chain == null) {
                break;
            }
            chain.setTargetId(item.getId());
            spawned++;
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.PLAYERS, 0.4f, 1.55f);
        }
    }

    private static boolean isMagnetCandidate(ServerPlayer player, ItemEntity item, double rangeSqr, Vec3 origin) {
        if (!item.isAlive() || item.getItem().isEmpty() || item.hasPickUpDelay()) {
            return false;
        }
        if (item.distanceToSqr(player) > rangeSqr) {
            return false;
        }
        if (BlackwhipChainEntity.isMagnetTargeting(player.getId(), item.getId())) {
            return false;
        }
        return hasLineOfSight(player, item, origin);
    }

    private static boolean hasLineOfSight(ServerPlayer player, ItemEntity item, Vec3 origin) {
        Vec3 to = item.position().add(0.0, item.getBbHeight() * 0.5, 0.0);
        BlockHitResult hit = player.level().clip(new ClipContext(
                origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        double itemDist = origin.distanceTo(to);
        return origin.distanceTo(hit.getLocation()) >= itemDist - 0.35;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_MAGNET.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainMagnetAbility> {
        public MapCodec<BlackwhipChainMagnetAbility> codec() {
            return BlackwhipChainMagnetAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainMagnetAbility> builder,
                                     HolderLookup.Provider provider) {
            builder.setDescription("While toggled on, unused grab tethers shoot at nearby dropped items and "
                            + "retract them into the owner's inventory. Shares the living-tag tether cap. "
                            + "Effective range = range + quirk_factor * qf_range_bonus.")
                    .add("range", TYPE_VALUE, "Base magnet radius in blocks.")
                    .add("qf_range_bonus", TYPE_VALUE, "Extra blocks of range per quirk factor.")
                    .add("segment_count", TYPE_VALUE, "Number of IK joints / hit-proxy segments (2-16).")
                    .add("link_length", TYPE_VALUE, "World-space length of each IK link.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("travel_ticks", TYPE_VALUE, "Ticks for the tip to reach max range.")
                    .addExampleObject(new BlackwhipChainMagnetAbility(
                            new StaticValue(8.0f), new StaticValue(2.0f), new StaticValue(4.0f),
                            new StaticValue(0.85f), new StaticValue(1.0f), new StaticValue(12.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
