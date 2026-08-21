package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipBlockTossStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * First press: unused grab tethers wrap random nearby blocks and hover them. Later presses throw
 * one hovering block at a time.
 */
public class BlackwhipBlockTossAbility extends Ability {

    public static final MapCodec<BlackwhipBlockTossAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(8.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("qf_range_bonus", new StaticValue(1.0f)).forGetter((ab) -> ab.qfRangeBonus),
                    Value.CODEC.optionalFieldOf("throw_speed", new StaticValue(1.35f)).forGetter((ab) -> ab.throwSpeed),
                    Value.CODEC.optionalFieldOf("base_damage", new StaticValue(1.0f)).forGetter((ab) -> ab.baseDamage),
                    Value.CODEC.optionalFieldOf("damage_per_hardness", new StaticValue(1.5f)).forGetter((ab) -> ab.damagePerHardness),
                    Value.CODEC.optionalFieldOf("knockback", new StaticValue(0.35f)).forGetter((ab) -> ab.knockback),
                    Value.CODEC.optionalFieldOf("segment_count", new StaticValue(4.0f)).forGetter((ab) -> ab.segmentCount),
                    Value.CODEC.optionalFieldOf("link_length", new StaticValue(0.85f)).forGetter((ab) -> ab.linkLength),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("travel_ticks", new StaticValue(10.0f)).forGetter((ab) -> ab.travelTicks),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipBlockTossAbility::new));

    public final Value range;
    public final Value qfRangeBonus;
    public final Value throwSpeed;
    public final Value baseDamage;
    public final Value damagePerHardness;
    public final Value knockback;
    public final Value segmentCount;
    public final Value linkLength;
    public final Value thickness;
    public final Value travelTicks;

    public BlackwhipBlockTossAbility(Value range, Value qfRangeBonus, Value throwSpeed, Value baseDamage,
                                     Value damagePerHardness, Value knockback, Value segmentCount,
                                     Value linkLength, Value thickness, Value travelTicks,
                                     AbilityProperties properties, AbilityStateManager conditions,
                                     List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.qfRangeBonus = qfRangeBonus;
        this.throwSpeed = throwSpeed;
        this.baseDamage = baseDamage;
        this.damagePerHardness = damagePerHardness;
        this.knockback = knockback;
        this.segmentCount = segmentCount;
        this.linkLength = linkLength;
        this.thickness = thickness;
        this.travelTicks = travelTicks;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        if (BlackwhipBlockTossStore.hasReadyCarries(player)) {
            double qf = QuirkFactorUtil.getQuirkFactor(player);
            float speed = Math.max(0.4f, this.throwSpeed.getAsFloat(context) * (float) (1.0 + 0.05 * qf));
            BlackwhipBlockTossStore.tossOne(
                    player, speed,
                    this.baseDamage.getAsFloat(context),
                    this.damagePerHardness.getAsFloat(context),
                    this.knockback.getAsFloat(context));
            return;
        }
        if (BlackwhipChainEntity.countOwnedActiveByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_BLOCK_TOSS) > 0) {
            return;
        }
        grabNearbyBlocks(player, level, context);
    }

    private void grabNearbyBlocks(ServerPlayer player, ServerLevel level, DataContext context) {
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int maxTethers = (int) ((int) BodyAttachments.get(player).getCustomFloat(
                player, BodyPart.CHEST, BlackwhipTagAbility.MAX_TETHERS_KEY, 1) + (qf * 2));
        int freeSlots = maxTethers - BlackwhipChainEntity.countOwnedActive(player.getId());
        if (freeSlots <= 0) {
            return;
        }

        float range = Math.max(1.0f, this.range.getAsFloat(context)
                + (float) (qf * this.qfRangeBonus.getAsFloat(context)));
        List<BlockPos> candidates = collectCandidates(player, level, range);
        if (candidates.isEmpty()) {
            return;
        }
        shuffle(candidates, player);

        int segments = Math.max(2, this.segmentCount.getAsInt(context));
        float link = this.linkLength.getAsFloat(context);
        float hp = Math.max(1.0f, BodyAttachments.get(player).getCustomFloat(
                player, BodyPart.CHEST, BlackwhipTagAbility.CHAIN_HP_KEY, 1.0f));
        float thickness = this.thickness.getAsFloat(context);
        int travel = Math.max(1, this.travelTicks.getAsInt(context));

        int spawned = 0;
        Vec3 origin = player.getEyePosition();
        for (BlockPos pos : candidates) {
            if (spawned >= freeSlots) {
                break;
            }
            Vec3 aim = Vec3.atCenterOf(pos);
            Vec3 dir = aim.subtract(origin);
            if (dir.lengthSqr() < 1.0e-6) {
                dir = player.getLookAngle();
            }
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                    player, dir, range, segments, link, hp, thickness, travel,
                    0, range, maxTethers, BlackwhipChainEntity.PURPOSE_BLOCK_TOSS);
            if (chain == null) {
                break;
            }
            chain.setTossTargetPos(pos);
            spawned++;
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.PLAYERS, 0.35f, 0.85f + player.getRandom().nextFloat() * 0.3f);
        }
    }

    private static List<BlockPos> collectCandidates(ServerPlayer player, ServerLevel level, float range) {
        BlockPos feet = player.getOnPos();
        BlockPos stand = player.blockPosition().below();
        Vec3 origin = player.getEyePosition();
        double rangeSqr = range * (double) range;
        int r = Math.max(1, (int) Math.ceil(range));
        BlockPos min = player.blockPosition().offset(-r, -r, -r);
        BlockPos max = player.blockPosition().offset(r, r, r);
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(feet) || immutable.equals(stand)) {
                continue;
            }
            if (Vec3.atCenterOf(immutable).distanceToSqr(player.position()) > rangeSqr) {
                continue;
            }
            if (!BlackwhipBlockTossStore.isGrabbable(level, immutable)) {
                continue;
            }
            if (BlackwhipChainEntity.isBlockTossTargeting(player.getId(), immutable)) {
                continue;
            }
            if (!hasLineOfSight(player, origin, immutable)) {
                continue;
            }
            out.add(immutable);
        }
        return out;
    }

    private static boolean hasLineOfSight(ServerPlayer player, Vec3 origin, BlockPos pos) {
        Vec3 to = Vec3.atCenterOf(pos);
        BlockHitResult hit = player.level().clip(new ClipContext(
                origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getBlockPos().equals(pos);
    }

    private static void shuffle(List<BlockPos> list, ServerPlayer player) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = player.getRandom().nextInt(i + 1);
            BlockPos tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_BLOCK_TOSS.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipBlockTossAbility> {
        public MapCodec<BlackwhipBlockTossAbility> codec() {
            return BlackwhipBlockTossAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipBlockTossAbility> builder,
                                     HolderLookup.Provider provider) {
            builder.setDescription("First press sends unused grab tethers at random nearby blocks. "
                            + "They wrap, rip (skipping chests and other block entities), and hover. "
                            + "Each later press throws one block. Entity hits deal hardness-scaled damage "
                            + "and drop the block as an item; block hits place it like a falling block.")
                    .add("range", TYPE_VALUE, "Base grab radius in blocks.")
                    .add("qf_range_bonus", TYPE_VALUE, "Extra blocks of range per quirk factor.")
                    .add("throw_speed", TYPE_VALUE, "Projectile launch speed (slightly scaled by quirk factor).")
                    .add("base_damage", TYPE_VALUE, "Flat damage added to hardness * damage_per_hardness.")
                    .add("damage_per_hardness", TYPE_VALUE, "Damage multiplied by the ripped block's destroy speed.")
                    .add("knockback", TYPE_VALUE, "Base knockback; extra is added from hardness.")
                    .add("segment_count", TYPE_VALUE, "Number of IK joints / hit-proxy segments (2-16).")
                    .add("link_length", TYPE_VALUE, "World-space length of each IK link.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("travel_ticks", TYPE_VALUE, "Ticks for the tip to reach max range.")
                    .addExampleObject(new BlackwhipBlockTossAbility(
                            new StaticValue(8.0f), new StaticValue(1.0f), new StaticValue(1.35f),
                            new StaticValue(1.0f), new StaticValue(1.5f), new StaticValue(0.35f),
                            new StaticValue(4.0f), new StaticValue(0.85f), new StaticValue(1.0f),
                            new StaticValue(10.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
