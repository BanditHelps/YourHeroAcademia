package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.entities.BlackwhipStyle;
import com.github.bandithelps.network.BlackwhipSwingPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Zip": fires a Blackwhip at a surface and starts a true Spider-Man pendulum swing. The server pins
 * the anchor + rope entity and tells the owning client to run the {@code BlackwhipSwingController}
 * pendulum simulation. On release the player keeps their built-up momentum (plus an optional quirk-
 * scaled boost) for a natural launch. Range scales with quirk factor.
 */
public class BlackwhipZipAbility extends Ability {

    private static final Map<UUID, Integer> ACTIVE_ROPE = new ConcurrentHashMap<>();

    public static final MapCodec<BlackwhipZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(28.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("quirk_range_bonus", new StaticValue(3.0f)).forGetter((ab) -> ab.quirkRangeBonus),
                    Value.CODEC.optionalFieldOf("release_up_boost", new StaticValue(0.25f)).forGetter((ab) -> ab.releaseUpBoost),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(0.8f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(0.18f)).forGetter((ab) -> ab.curve),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipZipAbility::new));

    public final Value range;
    public final Value quirkRangeBonus;
    public final Value releaseUpBoost;
    public final Value thickness;
    public final Value curve;

    public BlackwhipZipAbility(Value range, Value quirkRangeBonus, Value releaseUpBoost, Value thickness, Value curve,
                              AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.quirkRangeBonus = quirkRangeBonus;
        this.releaseUpBoost = releaseUpBoost;
        this.thickness = thickness;
        this.curve = curve;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        double range = this.range.getAsFloat(context) + qf * this.quirkRangeBonus.getAsFloat(context);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.6f);
            return;
        }

        // Clean up any prior swing rope.
        stopSwing(player, level);

        Vec3 anchor = hit.getLocation();
        BlackwhipEntity rope = BlackwhipHelper.spawnAnchorRope(player, anchor, BlackwhipStyle.SWING_ROPE,
                this.thickness.getAsFloat(context), this.curve.getAsFloat(context), 4);
        ACTIVE_ROPE.put(player.getUUID(), rope.getId());

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        float ropeLength = (float) center.distanceTo(anchor);
        PacketDistributor.sendToPlayer(player, new BlackwhipSwingPayload(true, anchor.x, anchor.y, anchor.z, ropeLength));
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7f, 1.1f);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ACTIVE_ROPE.containsKey(player.getUUID())) {
            DataContext context = DataContext.forEntity(entity);
            double upBoost = this.releaseUpBoost.getAsFloat(context);
            if (upBoost > 0.0) {
                player.setDeltaMovement(player.getDeltaMovement().add(0, upBoost, 0));
                player.hurtMarked = true;
            }
            stopSwing(player, level);
        }
    }

    private void stopSwing(ServerPlayer player, ServerLevel level) {
        Integer ropeId = ACTIVE_ROPE.remove(player.getUUID());
        if (ropeId != null && level.getEntity(ropeId) instanceof BlackwhipEntity rope) {
            rope.deactivate();
        }
        PacketDistributor.sendToPlayer(player, new BlackwhipSwingPayload(false, 0, 0, 0, 0));
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_ZIP.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipZipAbility> {
        public MapCodec<BlackwhipZipAbility> codec() {
            return BlackwhipZipAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipZipAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Fires a Blackwhip at a surface and starts a Spider-Man pendulum swing while held. Hold sneak to reel in, sprint to let out, W to pump the arc. Release to launch with your built-up momentum.")
                    .add("range", TYPE_VALUE, "Base reach of the anchor raycast.")
                    .add("quirk_range_bonus", TYPE_VALUE, "Extra reach per point of quirk factor.")
                    .add("release_up_boost", TYPE_VALUE, "Small upward velocity added on release to clear ledges.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("curve", TYPE_VALUE, "Visual whip slack (kept low for a taut swing rope).")
                    .addExampleObject(new BlackwhipZipAbility(new StaticValue(28.0f), new StaticValue(3.0f), new StaticValue(0.25f),
                            new StaticValue(0.8f), new StaticValue(0.18f), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
