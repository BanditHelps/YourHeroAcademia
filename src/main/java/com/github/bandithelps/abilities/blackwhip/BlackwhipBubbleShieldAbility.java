package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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
 * "Bubble Shield": while active, weaves a forward dome of Blackwhip petals that absorbs a fraction of
 * incoming damage (see {@code BlackwhipShieldEvents}), knocks the user back from the blow, and spends
 * stamina per point of damage prevented.
 */
public class BlackwhipBubbleShieldAbility extends Ability {

    /** Settings cached for the damage handler while a player's shield is up. */
    public record ShieldSettings(float absorb, float knockback, float staminaPerDamage, boolean frontalOnly) {
    }

    private static final Map<UUID, Integer> BUBBLE = new ConcurrentHashMap<>();
    private static final Map<UUID, ShieldSettings> ACTIVE = new ConcurrentHashMap<>();

    public static ShieldSettings getActive(UUID playerId) {
        return ACTIVE.get(playerId);
    }

    public static final MapCodec<BlackwhipBubbleShieldAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("base_strands", new StaticValue(8.0f)).forGetter((ab) -> ab.baseStrands),
                    Value.CODEC.optionalFieldOf("radius", new StaticValue(1.4f)).forGetter((ab) -> ab.radius),
                    Value.CODEC.optionalFieldOf("forward_offset", new StaticValue(1.2f)).forGetter((ab) -> ab.forwardOffset),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(1.1f)).forGetter((ab) -> ab.curve),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("jaggedness", new StaticValue(0.35f)).forGetter((ab) -> ab.jaggedness),
                    Value.CODEC.optionalFieldOf("absorb", new StaticValue(0.8f)).forGetter((ab) -> ab.absorb),
                    Value.CODEC.optionalFieldOf("knockback_strength", new StaticValue(0.6f)).forGetter((ab) -> ab.knockback),
                    Value.CODEC.optionalFieldOf("stamina_per_damage", new StaticValue(10.0f)).forGetter((ab) -> ab.staminaPerDamage),
                    Codec.BOOL.optionalFieldOf("frontal_only", true).forGetter((ab) -> ab.frontalOnly),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipBubbleShieldAbility::new));

    public final Value baseStrands;
    public final Value radius;
    public final Value forwardOffset;
    public final Value curve;
    public final Value thickness;
    public final Value jaggedness;
    public final Value absorb;
    public final Value knockback;
    public final Value staminaPerDamage;
    public final boolean frontalOnly;

    public BlackwhipBubbleShieldAbility(Value baseStrands, Value radius, Value forwardOffset, Value curve, Value thickness,
                                       Value jaggedness, Value absorb, Value knockback, Value staminaPerDamage, boolean frontalOnly,
                                       AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.baseStrands = baseStrands;
        this.radius = radius;
        this.forwardOffset = forwardOffset;
        this.curve = curve;
        this.thickness = thickness;
        this.jaggedness = jaggedness;
        this.absorb = absorb;
        this.knockback = knockback;
        this.staminaPerDamage = staminaPerDamage;
        this.frontalOnly = frontalOnly;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel) {
            spawnBubble(player);
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (player.tickCount % 20 == 0) {
                Integer id = BUBBLE.get(player.getUUID());
                if (id == null || !(level.getEntity(id) instanceof BlackwhipEntity bubble) || !bubble.isActive()) {
                    spawnBubble(player);
                }
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    private void spawnBubble(ServerPlayer player) {
        Integer old = BUBBLE.remove(player.getUUID());
        if (old != null && player.level() instanceof ServerLevel level && level.getEntity(old) instanceof BlackwhipEntity existing) {
            existing.discard();
        }
        DataContext context = DataContext.forEntity(player);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int strands = Math.max(4, this.baseStrands.getAsInt(context) + (int) Math.floor(qf * 0.5));
        BlackwhipEntity bubble = BlackwhipHelper.spawnBubble(player, strands,
                this.radius.getAsFloat(context), this.forwardOffset.getAsFloat(context),
                this.curve.getAsFloat(context), this.thickness.getAsFloat(context), this.jaggedness.getAsFloat(context));
        BUBBLE.put(player.getUUID(), bubble.getId());

        float absorbValue = Math.max(0.0f, Math.min(1.0f, this.absorb.getAsFloat(context)));
        ACTIVE.put(player.getUUID(), new ShieldSettings(absorbValue,
                Math.max(0.0f, this.knockback.getAsFloat(context)),
                Math.max(0.0f, this.staminaPerDamage.getAsFloat(context)),
                this.frontalOnly));
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            ACTIVE.remove(player.getUUID());
            Integer id = BUBBLE.remove(player.getUUID());
            if (id != null && player.level() instanceof ServerLevel level && level.getEntity(id) instanceof BlackwhipEntity bubble) {
                bubble.deactivate();
            }
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_BUBBLE_SHIELD.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipBubbleShieldAbility> {
        public MapCodec<BlackwhipBubbleShieldAbility> codec() {
            return BlackwhipBubbleShieldAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipBubbleShieldAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While active, weaves a forward Blackwhip dome that absorbs a fraction of incoming damage, knocks the user back from the blow, and spends stamina per point of damage prevented.")
                    .add("base_strands", TYPE_VALUE, "Base number of petals before quirk-factor scaling.")
                    .add("radius", TYPE_VALUE, "Radius of the shield sphere.")
                    .add("forward_offset", TYPE_VALUE, "How far in front of the player the dome centers.")
                    .add("curve", TYPE_VALUE, "Curvature of the petals (visual).")
                    .add("thickness", TYPE_VALUE, "Petal ribbon thickness.")
                    .add("jaggedness", TYPE_VALUE, "Noise amount along petals.")
                    .add("absorb", TYPE_VALUE, "Fraction of incoming damage absorbed (0-1).")
                    .add("knockback_strength", TYPE_VALUE, "Knockback applied to the user when a hit is blocked.")
                    .add("stamina_per_damage", TYPE_VALUE, "Stamina spent per point of damage prevented.")
                    .add("frontal_only", TYPE_BOOLEAN, "If true, only blocks attacks coming from the front.")
                    .addExampleObject(new BlackwhipBubbleShieldAbility(new StaticValue(8.0f), new StaticValue(1.4f), new StaticValue(1.2f),
                            new StaticValue(1.1f), new StaticValue(1.0f), new StaticValue(0.35f), new StaticValue(0.8f), new StaticValue(0.6f),
                            new StaticValue(10.0f), true, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
