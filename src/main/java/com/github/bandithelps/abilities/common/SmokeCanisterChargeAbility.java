package com.github.bandithelps.abilities.common;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.body.IBodyData;
import com.github.bandithelps.items.SmokeCanisterData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

public class SmokeCanisterChargeAbility extends Ability {
    public static final MapCodec<SmokeCanisterChargeAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.listOf().optionalFieldOf("tier_costs", List.of(35, 55, 80)).forGetter(ab -> ab.tierCosts),
                    Codec.INT.optionalFieldOf("max_tier", 3).forGetter(ab -> ab.maxTier),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, SmokeCanisterChargeAbility::new));

    private final List<Integer> tierCosts;
    private final int maxTier;

    public SmokeCanisterChargeAbility(List<Integer> tierCosts, int maxTier, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.tierCosts = tierCosts == null || tierCosts.isEmpty() ? List.of(35, 55, 80) : tierCosts;
        this.maxTier = maxTier;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        InteractionHand hand = this.resolveCanisterHand(player);
        if (hand == null) {
            player.sendSystemMessage(Component.translatable("ability.yha.smoke_canister_charge.empty_hand"));
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        int currentTier = SmokeCanisterData.getTier(stack);
        if (currentTier >= SmokeCanisterData.MAX_TIER || currentTier == maxTier) {
            player.sendSystemMessage(Component.translatable("ability.yha.smoke_canister_charge.max_tier"));
            return;
        }

        int nextTier = currentTier + 1;
        int cost = this.resolveTierCost(nextTier);
        IBodyData bodyData = BodyAttachments.get(player);
        float currentSmoke = bodyData.getCustomFloat(player, BodyPart.CHEST, "smoke_capacity", 0.0f);
        if (currentSmoke < cost) {
            player.sendSystemMessage(Component.translatable("ability.yha.smoke_canister_charge.not_enough_smoke"));
            return;
        }

        bodyData.setCustomFloat(player, BodyPart.CHEST, "smoke_capacity", currentSmoke - cost);
        BodySyncEvents.syncNow(player);
        this.updateCanisterStack(player, hand, stack, nextTier);
        player.sendSystemMessage(Component.translatable("ability.yha.smoke_canister_charge.charged", nextTier, SmokeCanisterData.MAX_TIER));
    }

    private int resolveTierCost(int tier) {
        int index = Math.max(0, tier - 1);
        if (index < this.tierCosts.size()) {
            return Math.max(1, this.tierCosts.get(index));
        }
        return Math.max(1, this.tierCosts.get(this.tierCosts.size() - 1));
    }

    private InteractionHand resolveCanisterHand(ServerPlayer player) {
        if (isFillableCanister(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (isFillableCanister(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private boolean isFillableCanister(ItemStack stack) {
        return stack.is(YourHeroAcademia.EMPTY_CANISTER.get())
                || stack.is(YourHeroAcademia.FILLED_SMOKE_CANISTER.get())
                || stack.is(YourHeroAcademia.INFUSED_SMOKE_CANISTER.get());
    }

    private void updateCanisterStack(ServerPlayer player, InteractionHand hand, ItemStack currentStack, int targetTier) {
        ItemStack updatedStack;
        if (currentStack.is(YourHeroAcademia.EMPTY_CANISTER.get())) {
            updatedStack = new ItemStack(YourHeroAcademia.FILLED_SMOKE_CANISTER.get(), currentStack.getCount());
        } else {
            updatedStack = currentStack.copy();
        }
        SmokeCanisterData.setTier(updatedStack, targetTier);
        player.setItemInHand(hand, updatedStack);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.SMOKE_CANISTER_CHARGE.get();
    }

    public static class Serializer extends AbilitySerializer<SmokeCanisterChargeAbility> {
        @Override
        public MapCodec<SmokeCanisterChargeAbility> codec() {
            return SmokeCanisterChargeAbility.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, SmokeCanisterChargeAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Consumes Smokescreen capacity to charge canisters in tiers.")
                    .add("tier_costs", TYPE_INT, "Smoke capacity cost for each charge tier in ascending order.")
                    .add("max_tier", TYPE_INT, "The highest tier that the ability can make. (Max 3)")
                    .addExampleObject(new SmokeCanisterChargeAbility(List.of(35, 55, 80), 3, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
