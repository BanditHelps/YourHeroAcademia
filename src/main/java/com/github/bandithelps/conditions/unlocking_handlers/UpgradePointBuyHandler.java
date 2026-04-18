package com.github.bandithelps.conditions.unlocking_handlers;

import com.github.bandithelps.client.stamina.ClientStaminaState;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.threetag.palladium.icon.IngredientIcon;
import net.threetag.palladium.logic.condition.Condition;
import net.threetag.palladium.logic.condition.TrueCondition;
import net.threetag.palladium.power.ability.unlocking.BuyableUnlockingHandler;
import net.threetag.palladium.power.ability.unlocking.UnlockingHandlerSerializer;

public class UpgradePointBuyHandler extends BuyableUnlockingHandler {

    public static final MapCodec<UpgradePointBuyHandler> CODEC =
            RecordCodecBuilder.mapCodec((instance) -> instance.group(
                            Codec.INT.fieldOf("cost").forGetter((c) -> c.upgradeCost),
                            Condition.CODEC.optionalFieldOf("conditions", TrueCondition.INSTANCE).forGetter((c) -> c.condition))
                            .apply(instance, UpgradePointBuyHandler::new));


    private final int upgradeCost;

    public UpgradePointBuyHandler(int upgradeCost, Condition conditions) {
        super(conditions);
        this.upgradeCost = upgradeCost;
    }

    @Override
    public boolean hasEnoughCurrency(LivingEntity livingEntity) {
        if (livingEntity instanceof Player player) {
            return ClientStaminaState.getUpgradePoints() >= upgradeCost;
        } else {
            return false;
        }
    }

    @Override
    public void consumeCurrency(LivingEntity livingEntity) {
        if (livingEntity instanceof ServerPlayer player) {
            StaminaUtil.spendUpgradePoints(player, upgradeCost);
        }
    }

    @Override
    public Display getDisplay() {
        return new BuyableUnlockingHandler.Display(new IngredientIcon(Ingredient.of(Items.COMMAND_BLOCK)), 1, Component.translatable("gui.yha.powers.buy_ability"));
    }

    @Override
    public UnlockingHandlerSerializer<?> getSerializer() {
        return UnlockingHandlerRegister.UPGRADE_BUYABLE.get();
    }

    public static class Serializer extends UnlockingHandlerSerializer<UpgradePointBuyHandler> {
        public MapCodec<UpgradePointBuyHandler> codec() {
            return UpgradePointBuyHandler.CODEC;
        }
    }
}
