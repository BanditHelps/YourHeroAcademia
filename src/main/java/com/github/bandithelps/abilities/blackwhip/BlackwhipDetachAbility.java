package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blackwhip.BlackwhipTagStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * Releases Blackwhip tethers: if {@code all} is false and the crosshair is on a tagged entity, only
 * that one is released; otherwise every tether is dropped at once.
 */
public class BlackwhipDetachAbility extends Ability {

    public static final MapCodec<BlackwhipDetachAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(20.0f)).forGetter((ab) -> ab.range),
                    Codec.BOOL.optionalFieldOf("all", false).forGetter((ab) -> ab.all),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipDetachAbility::new));

    public final Value range;
    public final boolean all;

    public BlackwhipDetachAbility(Value range, boolean all, AbilityProperties properties, AbilityStateManager conditions,
                                 List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.all = all;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);

        if (!this.all) {
            LivingEntity target = BlackwhipTargeting.raycastLiving(player, this.range.getAsFloat(context));
            if (target != null && BlackwhipTagStore.isTagged(player, target.getId())) {
                BlackwhipTagStore.removeTag(player, target.getId());
                level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.6f, 1.0f);
                return;
            }
        }

        if (BlackwhipTagStore.getTagCount(player) > 0) {
            BlackwhipTagStore.clearTags(player);
            level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_DETACH.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipDetachAbility> {
        public MapCodec<BlackwhipDetachAbility> codec() {
            return BlackwhipDetachAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipDetachAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Releases Blackwhip tethers. With all=false, releases only the tagged entity under the crosshair (falling back to releasing all if none is targeted); with all=true, always releases everything.")
                    .add("range", TYPE_VALUE, "Reach of the targeted-release raycast.")
                    .add("all", TYPE_BOOLEAN, "If true, always release every tether.")
                    .addExampleObject(new BlackwhipDetachAbility(new StaticValue(20.0f), false,
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
