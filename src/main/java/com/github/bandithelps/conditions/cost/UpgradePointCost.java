package com.github.bandithelps.conditions.cost;


import com.github.bandithelps.client.stamina.ClientStaminaState;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.icon.IngredientIcon;
import net.threetag.palladium.logic.cost.Cost;
import net.threetag.palladium.logic.cost.CostDisplay;
import net.threetag.palladium.logic.cost.CostSerializer;

import java.util.stream.Stream;

/**
 * Defines a new cost type that can be used in the buyable condition for powers
 */
public class UpgradePointCost extends Cost {

    public static final MapCodec<UpgradePointCost> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(ExtraCodecs.POSITIVE_INT.fieldOf("points").forGetter((c) -> c.points)).apply(instance, UpgradePointCost::new));

    private final int points;

    public UpgradePointCost(int points) {
        this.points = points;
    }

    @Override
    public boolean hasEnoughCurrency(LivingEntity livingEntity) {
        if (livingEntity instanceof Player player) {
            return ClientStaminaState.getUpgradePoints() >= points;
        } else {
            return false;
        }
    }

    @Override
    public void consumeCurrency(LivingEntity livingEntity) {
        if (livingEntity instanceof ServerPlayer player) {
            StaminaUtil.spendUpgradePoints(player, points);
        }
    }

    public CostDisplay createDisplay() {
        return new CostDisplay(
                new IngredientIcon(Ingredient.of(Items.COMMAND_BLOCK)),
                1,
                Component.translatable("gui.yha.upgrade_point")
        );
    }

    public CostSerializer<?> getSerializer() {
        return (CostSerializer) CostRegister.UPGRADE_POINT.value();
    }

    public static class Serializer extends CostSerializer<UpgradePointCost> {
        public MapCodec<UpgradePointCost> codec() {
            return UpgradePointCost.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Cost, UpgradePointCost> builder, HolderLookup.Provider provider) {
            builder.setName("Upgrade Point Cost").setDescription("Requires the entity to have a certain number of upgrade points.").add("points", TYPE_POSITIVE_INT, "The number of upgrade points to consume.").addExampleObject(new UpgradePointCost(2));
        }
    }
}
