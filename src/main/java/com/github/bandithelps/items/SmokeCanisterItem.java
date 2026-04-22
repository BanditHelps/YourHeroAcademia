package com.github.bandithelps.items;

import com.github.bandithelps.entities.SmokeCanisterProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SmokeCanisterItem extends Item {
    public SmokeCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        int tier = SmokeCanisterData.getTier(stack);
        if (tier <= 0) {
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            SmokeCanisterProjectileEntity projectile = new SmokeCanisterProjectileEntity(serverLevel, player);
            ItemStack thrownStack = stack.copyWithCount(1);
            projectile.setItem(thrownStack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.0f, 1.0f);
            serverLevel.addFreshEntity(projectile);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.7f + (player.getRandom().nextFloat() * 0.2f));
        }

        player.getCooldowns().addCooldown(stack, 15);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
