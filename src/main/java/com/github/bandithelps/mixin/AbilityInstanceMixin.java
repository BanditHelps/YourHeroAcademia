package com.github.bandithelps.mixin;

import com.github.bandithelps.utils.stamina.StaminaProperties;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin allows us to implement stamina logic into any ability that exists within palladium.
 * Simply adds extra logic to call the StaminaUtil functions based on the stored information in the StaminaProperties.
 * @param <T>
 */
@Mixin(AbilityInstance.class)
public abstract class AbilityInstanceMixin<T extends Ability> {

    @Shadow
    private T ability;

    @Shadow
    private int enabledTicks;

    @Shadow
    public abstract boolean isEnabled();

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/threetag/palladium/power/ability/Ability;tick(Lnet/minecraft/world/entity/LivingEntity;Lnet/threetag/palladium/power/ability/AbilityInstance;Z)Z"
            )
    )
    private void yha$consumeAbilityStamina(LivingEntity entity, boolean dampened, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer serverPlayer) || !this.isEnabled()) {
            return;
        }

        AbilityProperties properties = this.ability.getProperties();
        StaminaProperties stamina = StaminaProperties.of(properties);

        int initialStamina = stamina.yha$getInitialStamina();
        if (this.enabledTicks == 1) {
            StaminaUtil.useStamina(serverPlayer, initialStamina);
        }

        int interval = stamina.yha$getStaminaInterval();
        int intervalCost = stamina.yha$getStaminaIntervalCost();
        if (interval > 0 && this.enabledTicks % interval == 0) {
            StaminaUtil.useStamina(serverPlayer, intervalCost);
        }
    }
}
