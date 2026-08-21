package com.github.bandithelps.abilities.decay;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.decay.DecayHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A toggleable ability representing a lack of fine control over the Decay quirk. While active:
 *  - Items held in the user's hands slowly decay (tools lose durability, other items disintegrate).
 *  - Striking an entity with an open (empty) hand applies the {@link com.github.bandithelps.effects.DecayEffect}
 *    to that entity (handled by {@link DecayFistAttackHandler}).
 *
 * The attack handler queries {@link #isActive(UUID)} to know which players currently have the
 * decaying touch enabled.
 */
public class DecayFistAbility extends Ability {

    private static final Set<UUID> ACTIVE_USERS = ConcurrentHashMap.newKeySet();

    public static final MapCodec<DecayFistAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("item_durability_damage", new StaticValue(2.0f)).forGetter((ab) -> ab.itemDurabilityDamage),
                    Value.CODEC.optionalFieldOf("item_decay_interval", new StaticValue(20.0f)).forGetter((ab) -> ab.itemDecayInterval),
                    Value.CODEC.optionalFieldOf("instability_per_interval", new StaticValue(1.0f)).forGetter((ab) -> ab.instabilityPerInterval),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, DecayFistAbility::new));

    public final Value itemDurabilityDamage;
    public final Value itemDecayInterval;
    public final Value instabilityPerInterval;

    public DecayFistAbility(Value itemDurabilityDamage, Value itemDecayInterval, Value instabilityPerInterval, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.itemDurabilityDamage = itemDurabilityDamage;
        this.itemDecayInterval = itemDecayInterval;
        this.instabilityPerInterval = instabilityPerInterval;
    }

    public static boolean isActive(UUID uuid) {
        return ACTIVE_USERS.contains(uuid);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            ACTIVE_USERS.add(player.getUUID());
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.4f, 1.4f);
            }
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            ACTIVE_USERS.add(player.getUUID());

            DataContext context = DataContext.forEntity(entity);
            int interval = Math.max(1, this.itemDecayInterval.getAsInt(context));

            // Constant ominous aura around the active hand.
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    2, 0.35, 0.35, 0.35, 0.005);

            if (player.tickCount % interval == 0) {
                int durabilityDamage = Math.max(1, this.itemDurabilityDamage.getAsInt(context));
                boolean decayedSomething = decayHeldItem(player, EquipmentSlot.MAINHAND, durabilityDamage)
                        | decayHeldItem(player, EquipmentSlot.OFFHAND, durabilityDamage);

                float instability = this.instabilityPerInterval.getAsFloat(context);
                if (instability > 0.0f) {
                    DecayHelper.addInstability(player, instability);
                }

                if (decayedSomething) {
                    serverLevel.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 0.7f);
                }
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            ACTIVE_USERS.remove(player.getUUID());
        }
    }

    private boolean decayHeldItem(ServerPlayer player, EquipmentSlot slot, int durabilityDamage) {
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(durabilityDamage, player, slot);
            return true;
        }

        // Regular items simply disintegrate one at a time.
        stack.shrink(1);
        return true;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.DECAY_FIST.get();
    }

    public static class Serializer extends AbilitySerializer<DecayFistAbility> {
        public MapCodec<DecayFistAbility> codec() {
            return DecayFistAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, DecayFistAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("A toggleable 'decaying touch'. While active, items held in either hand slowly decay (tools lose durability, other items disintegrate), and striking an entity with an empty hand inflicts the decay effect on them.")
                    .add("item_durability_damage", TYPE_VALUE, "Durability damage applied to held tools each decay interval.")
                    .add("item_decay_interval", TYPE_VALUE, "How many ticks between each held-item decay tick.")
                    .add("instability_per_interval", TYPE_VALUE, "How much instability builds up per decay interval while active.")
                    .addExampleObject(new DecayFistAbility(new StaticValue(2.0f), new StaticValue(20.0f), new StaticValue(1.0f), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
