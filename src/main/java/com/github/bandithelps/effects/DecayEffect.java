package com.github.bandithelps.effects;

import com.github.bandithelps.values.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The signature effect of the Decay quirk. While active it eats away at the target.
 * If the entity is wearing armor, the decay aggressively chews through the armor's durability
 * (prioritizing armor before flesh). Once all damageable armor has been destroyed, the decay
 * begins inflicting a custom decay damage type directly to the entity.
 *
 * The amplifier acts as the "strength" of the decay and is generally derived from the user's
 * quirk factor when the effect is applied.
 */
public class DecayEffect extends MobEffect {

    // Armor slots, ordered so the decay prioritises the larger/most protective pieces first.
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.HEAD,
            EquipmentSlot.FEET
    };

    // How often (in ticks) the decay "bites".
    private static final int TICK_INTERVAL = 10;

    public DecayEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel serverLevel, LivingEntity mob, int amplification) {
        ItemStack targetArmor = findDamageableArmor(mob);

        if (targetArmor != null) {
            // Chew through the armor. Higher amplifier = much faster destruction.
            int armorDamage = 8 + (amplification * 6);
            EquipmentSlot slot = slotFor(mob, targetArmor);
            if (slot != null) {
                targetArmor.hurtAndBreak(armorDamage, mob, slot);
            }

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(),
                    4, 0.3, 0.4, 0.3, 0.02);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    3, 0.3, 0.4, 0.3, 0.01);

            if (serverLevel.getRandom().nextFloat() < 0.25f) {
                serverLevel.playSound(null, mob.blockPosition(), SoundEvents.ITEM_BREAK.value(),
                        SoundSource.PLAYERS, 0.4f, 0.6f);
            }
        } else {
            // No armor left to protect the body - decay eats the flesh directly.
            float decayDamage = 1.0f + (amplification * 0.75f);
            mob.hurtServer(serverLevel, ModDamageTypes.decayDamageSource(serverLevel), decayDamage);

            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    5, 0.3, 0.5, 0.3, 0.02);
            serverLevel.sendParticles(ParticleTypes.ASH,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    3, 0.3, 0.5, 0.3, 0.01);
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return tickCount % TICK_INTERVAL == 0;
    }

    private static ItemStack findDamageableArmor(LivingEntity mob) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                return stack;
            }
        }
        return null;
    }

    private static EquipmentSlot slotFor(LivingEntity mob, ItemStack stack) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (mob.getItemBySlot(slot) == stack) {
                return slot;
            }
        }
        return null;
    }
}
