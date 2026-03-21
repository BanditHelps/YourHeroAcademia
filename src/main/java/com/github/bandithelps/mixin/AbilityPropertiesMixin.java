package com.github.bandithelps.mixin;

import com.github.bandithelps.utils.stamina.StaminaProperties;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import net.threetag.palladium.power.ability.AbilityProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin allows us to add in 3 properties into any and every palladium ability.
 * Adds "initial_stamina", "stamina_interval", and "stamina_interval_cost".
 * Gets a little confusing because we needed to add them into the Codec, but basically just combines the existing codec
 * with the new one. (Used AI to help with the Codec unpacking/repacking logic cause I haven't used them before)
 */
@Mixin(AbilityProperties.class)
public abstract class AbilityPropertiesMixin implements StaminaProperties {

    @Shadow
    @Final
    @Mutable
    public static Codec<AbilityProperties> CODEC;

    @Unique
    private static final String YHA_INITIAL_STAMINA_KEY = "initial_stamina";

    @Unique
    private static final String YHA_STAMINA_INTERVAL_KEY = "stamina_interval";

    @Unique
    private static final String YHA_STAMINA_INTERVAL_COST_KEY = "stamina_interval_cost";

    @Unique
    private int yha$initialStamina = 0;

    @Unique
    private int yha$staminaInterval = 0;

    @Unique
    private int yha$staminaIntervalCost = 0;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void yha$extendCodec(CallbackInfo ci) {
        final Codec<AbilityProperties> baseCodec = CODEC;
        CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<AbilityProperties, T>> decode(DynamicOps<T> ops, T input) {
                return baseCodec.decode(ops, input).map((decoded) -> {
                    AbilityProperties properties = decoded.getFirst();
                    StaminaProperties staminaProperties = StaminaProperties.of(properties);
                    staminaProperties.yha$setInitialStamina(yha$readInt(ops, input, YHA_INITIAL_STAMINA_KEY, 0));
                    staminaProperties.yha$setStaminaInterval(yha$readInt(ops, input, YHA_STAMINA_INTERVAL_KEY, 0));
                    staminaProperties.yha$setStaminaIntervalCost(yha$readInt(ops, input, YHA_STAMINA_INTERVAL_COST_KEY, 0));
                    return decoded;
                });
            }

            @Override
            public <T> DataResult<T> encode(AbilityProperties input, DynamicOps<T> ops, T prefix) {
                StaminaProperties staminaProperties = StaminaProperties.of(input);
                return baseCodec.encode(input, ops, prefix).flatMap((encoded) ->
                        yha$writeInt(ops, encoded, YHA_INITIAL_STAMINA_KEY, staminaProperties.yha$getInitialStamina())
                                .flatMap((withInitial) ->
                                        yha$writeInt(ops, withInitial, YHA_STAMINA_INTERVAL_KEY, staminaProperties.yha$getStaminaInterval())
                                                .flatMap((withInterval) ->
                                                        yha$writeInt(ops, withInterval, YHA_STAMINA_INTERVAL_COST_KEY, staminaProperties.yha$getStaminaIntervalCost())
                                                )
                                )
                );
            }
        };
    }

    @Unique
    private static <T> int yha$readInt(DynamicOps<T> ops, T input, String key, int fallback) {
        DataResult<MapLike<T>> mapResult = ops.getMap(input);
        if (mapResult.isError()) {
            return fallback;
        }

        return mapResult.result()
                .map((map) -> map.get(key))
                .map((value) -> ops.getNumberValue(value).result().map(Number::intValue).orElse(fallback))
                .orElse(fallback);
    }

    @Unique
    private static <T> DataResult<T> yha$writeInt(DynamicOps<T> ops, T input, String key, int value) {
        return ops.mergeToMap(input, ops.createString(key), ops.createInt(value));
    }

    @Override
    public int yha$getInitialStamina() {
        return this.yha$initialStamina;
    }

    @Override
    public void yha$setInitialStamina(int value) {
        this.yha$initialStamina = Math.max(0, value);
    }

    @Override
    public int yha$getStaminaInterval() {
        return this.yha$staminaInterval;
    }

    @Override
    public void yha$setStaminaInterval(int value) {
        this.yha$staminaInterval = Math.max(0, value);
    }

    @Override
    public int yha$getStaminaIntervalCost() {
        return this.yha$staminaIntervalCost;
    }

    @Override
    public void yha$setStaminaIntervalCost(int value) {
        this.yha$staminaIntervalCost = Math.max(0, value);
    }
}
