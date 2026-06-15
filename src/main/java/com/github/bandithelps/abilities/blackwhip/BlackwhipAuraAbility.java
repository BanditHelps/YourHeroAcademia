package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
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
 * "Aura": while active, summons a writhing crown of Blackwhip tendrils from the user's back. Purely a
 * cosmetic intimidation display; the number of tendrils scales with quirk factor.
 */
public class BlackwhipAuraAbility extends Ability {

    private static final Map<UUID, Integer> AURA = new ConcurrentHashMap<>();

    public static final MapCodec<BlackwhipAuraAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("base_strands", new StaticValue(6.0f)).forGetter((ab) -> ab.baseStrands),
                    Value.CODEC.optionalFieldOf("length", new StaticValue(2.0f)).forGetter((ab) -> ab.length),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(0.8f)).forGetter((ab) -> ab.curve),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("jaggedness", new StaticValue(0.4f)).forGetter((ab) -> ab.jaggedness),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipAuraAbility::new));

    public final Value baseStrands;
    public final Value length;
    public final Value curve;
    public final Value thickness;
    public final Value jaggedness;

    public BlackwhipAuraAbility(Value baseStrands, Value length, Value curve, Value thickness, Value jaggedness,
                               AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.baseStrands = baseStrands;
        this.length = length;
        this.curve = curve;
        this.thickness = thickness;
        this.jaggedness = jaggedness;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel) {
            spawnAura(player);
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (player.tickCount % 20 == 0) {
                Integer id = AURA.get(player.getUUID());
                if (id == null || !(level.getEntity(id) instanceof BlackwhipEntity aura) || !aura.isActive()) {
                    spawnAura(player);
                }
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    private void spawnAura(ServerPlayer player) {
        Integer old = AURA.remove(player.getUUID());
        if (old != null && player.level() instanceof ServerLevel level && level.getEntity(old) instanceof BlackwhipEntity existing) {
            existing.discard();
        }
        DataContext context = DataContext.forEntity(player);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int strands = Math.max(2, this.baseStrands.getAsInt(context) + (int) Math.floor(qf));
        BlackwhipEntity aura = BlackwhipHelper.spawnAura(player, strands,
                this.length.getAsFloat(context), this.curve.getAsFloat(context),
                this.thickness.getAsFloat(context), this.jaggedness.getAsFloat(context));
        AURA.put(player.getUUID(), aura.getId());
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            Integer id = AURA.remove(player.getUUID());
            if (id != null && player.level() instanceof ServerLevel level && level.getEntity(id) instanceof BlackwhipEntity aura) {
                aura.deactivate();
            }
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_AURA.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipAuraAbility> {
        public MapCodec<BlackwhipAuraAbility> codec() {
            return BlackwhipAuraAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipAuraAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While active, summons a writhing crown of Blackwhip tendrils from the user's back (cosmetic). Tendril count = base_strands + floor(quirk factor).")
                    .add("base_strands", TYPE_VALUE, "Base number of tendrils before quirk-factor scaling.")
                    .add("length", TYPE_VALUE, "Tendril length.")
                    .add("curve", TYPE_VALUE, "Tendril curl amount.")
                    .add("thickness", TYPE_VALUE, "Tendril thickness.")
                    .add("jaggedness", TYPE_VALUE, "How much the tendrils writhe/wave.")
                    .addExampleObject(new BlackwhipAuraAbility(new StaticValue(6.0f), new StaticValue(2.0f), new StaticValue(0.8f),
                            new StaticValue(1.0f), new StaticValue(0.4f), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
