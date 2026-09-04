package com.github.bandithelps.items;

import com.github.bandithelps.throwable.FuseMode;
import com.github.bandithelps.throwable.SmokeCloudDetonation;
import com.github.bandithelps.throwable.ThrowableWeaponItem;
import com.github.bandithelps.throwable.ThrowableWeaponSpec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class SmokeCanisterItem extends ThrowableWeaponItem {
    public SmokeCanisterItem(Properties properties) {
        super(properties, smokeSpec());
    }

    @Override
    protected boolean canThrow(ItemStack stack, LivingEntity user) {
        return SmokeCanisterData.getTier(stack) > 0;
    }

    private static ThrowableWeaponSpec smokeSpec() {
        return ThrowableWeaponSpec.builder()
                .scale(1.0f)
                .minThrowSpeed(0.4f)
                .maxThrowSpeed(1.6f)
                .maxChargeTicks(20)
                .fuseTicks(40)
                .fuseMode(FuseMode.FROM_IMPACT)
                .bounce(false)
                .stickOnImpact(true)
                .breaksBlocks(false)
                .cooldownTicks(15)
                .detonation(SmokeCloudDetonation.INSTANCE)
                .build();
    }
}
