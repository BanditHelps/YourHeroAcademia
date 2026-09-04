package com.github.bandithelps.mixin;

import com.github.bandithelps.gui.tree.TreeConnectionPaths;
import com.github.bandithelps.utils.stamina.StaminaProperties;
import com.github.bandithelps.utils.tree.ConnectionPathProperties;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
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
 * Adds stamina fields and optional {@code gui_connection} waypoints onto every Palladium ability.
 * Extra keys are wrapped into {@link AbilityProperties#CODEC} because the stock codec drops unknown fields.
 */
@Mixin(AbilityProperties.class)
public abstract class AbilityPropertiesMixin implements StaminaProperties, ConnectionPathProperties {

    @Shadow
    @Final
    @Mutable
    public static Codec<AbilityProperties> CODEC;

    @Unique
    private static final String YHA_ACTIVATION_STAMINA_KEY = "activation_stamina";

    @Unique
    private static final String YHA_STAMINA_INTERVAL_KEY = "stamina_interval";

    @Unique
    private static final String YHA_STAMINA_INTERVAL_COST_KEY = "stamina_interval_cost";

    @Unique
    private Value yha$activationStamina = new StaticValue(0);

    @Unique
    private Value yha$staminaInterval = new StaticValue(0);

    @Unique
    private Value yha$staminaIntervalCost = new StaticValue(0);

    @Unique
    private TreeConnectionPaths yha$guiConnections = TreeConnectionPaths.EMPTY;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void yha$extendCodec(CallbackInfo ci) {
        final Codec<AbilityProperties> baseCodec = CODEC;
        CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<AbilityProperties, T>> decode(DynamicOps<T> ops, T input) {
                return baseCodec.decode(ops, input).map((decoded) -> {
                    AbilityProperties properties = decoded.getFirst();
                    StaminaProperties staminaProperties = StaminaProperties.of(properties);
                    staminaProperties.yha$setActivationStamina(yha$readValue(ops, input, YHA_ACTIVATION_STAMINA_KEY, new StaticValue(0)));
                    staminaProperties.yha$setStaminaInterval(yha$readValue(ops, input, YHA_STAMINA_INTERVAL_KEY, new StaticValue(0)));
                    staminaProperties.yha$setStaminaIntervalCost(yha$readValue(ops, input, YHA_STAMINA_INTERVAL_COST_KEY, new StaticValue(0)));
                    ConnectionPathProperties.of(properties).yha$setGuiConnections(yha$readConnections(ops, input));
                    return decoded;
                });
            }

            @Override
            public <T> DataResult<T> encode(AbilityProperties input, DynamicOps<T> ops, T prefix) {
                StaminaProperties staminaProperties = StaminaProperties.of(input);
                ConnectionPathProperties connectionProperties = ConnectionPathProperties.of(input);
                return baseCodec.encode(input, ops, prefix).flatMap((encoded) ->
                        yha$writeValue(ops, encoded, YHA_ACTIVATION_STAMINA_KEY, staminaProperties.yha$getActivationStamina())
                                .flatMap((withInitial) ->
                                        yha$writeValue(ops, withInitial, YHA_STAMINA_INTERVAL_KEY, staminaProperties.yha$getStaminaInterval())
                                                .flatMap((withInterval) ->
                                                        yha$writeValue(ops, withInterval, YHA_STAMINA_INTERVAL_COST_KEY, staminaProperties.yha$getStaminaIntervalCost())
                                                                .flatMap((withCost) ->
                                                                        yha$writeConnections(ops, withCost, connectionProperties.yha$getGuiConnections())
                                                                )
                                                )
                                )
                );
            }
        };
    }

    @Unique
    private static <T> Value yha$readValue(DynamicOps<T> ops, T input, String key, Value fallback) {
        DataResult<MapLike<T>> mapResult = ops.getMap(input);
        if (mapResult.isError()) {
            return fallback;
        }

        T raw = mapResult.result().map((map) -> map.get(key)).orElse(null);
        if (raw == null) {
            return fallback;
        }
        return Value.CODEC.parse(ops, raw).result().orElse(fallback);
    }

    @Unique
    private static <T> DataResult<T> yha$writeValue(DynamicOps<T> ops, T input, String key, Value value) {
        if (value == null) {
            return DataResult.success(input);
        }
        return Value.CODEC.encodeStart(ops, value)
                .flatMap((encoded) -> ops.mergeToMap(input, ops.createString(key), encoded));
    }

    @Unique
    private static <T> TreeConnectionPaths yha$readConnections(DynamicOps<T> ops, T input) {
        DataResult<MapLike<T>> mapResult = ops.getMap(input);
        if (mapResult.isError()) {
            return TreeConnectionPaths.EMPTY;
        }
        T value = mapResult.result().map((map) -> map.get(TreeConnectionPaths.JSON_KEY)).orElse(null);
        if (value == null) {
            return TreeConnectionPaths.EMPTY;
        }
        return TreeConnectionPaths.CODEC.parse(ops, value).result().orElse(TreeConnectionPaths.EMPTY);
    }

    @Unique
    private static <T> DataResult<T> yha$writeConnections(DynamicOps<T> ops, T input, TreeConnectionPaths paths) {
        if (paths == null || paths.isEmpty()) {
            return DataResult.success(input);
        }
        return TreeConnectionPaths.CODEC.encodeStart(ops, paths)
                .flatMap((encoded) -> ops.mergeToMap(input, ops.createString(TreeConnectionPaths.JSON_KEY), encoded));
    }

    @Override
    public Value yha$getActivationStamina() {
        return this.yha$activationStamina;
    }

    @Override
    public void yha$setActivationStamina(Value value) {
        this.yha$activationStamina = value == null ? new StaticValue(0) : value;
    }

    @Override
    public Value yha$getStaminaInterval() {
        return this.yha$staminaInterval;
    }

    @Override
    public void yha$setStaminaInterval(Value value) {
        this.yha$staminaInterval = value == null ? new StaticValue(0) : value;
    }

    @Override
    public Value yha$getStaminaIntervalCost() {
        return this.yha$staminaIntervalCost;
    }

    @Override
    public void yha$setStaminaIntervalCost(Value value) {
        this.yha$staminaIntervalCost = value == null ? new StaticValue(0) : value;
    }

    @Override
    public TreeConnectionPaths yha$getGuiConnections() {
        return this.yha$guiConnections == null ? TreeConnectionPaths.EMPTY : this.yha$guiConnections;
    }

    @Override
    public void yha$setGuiConnections(TreeConnectionPaths paths) {
        this.yha$guiConnections = paths == null || paths.isEmpty() ? TreeConnectionPaths.EMPTY : paths.copy();
    }
}
