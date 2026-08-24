package com.github.bandithelps.mixin;

import com.github.bandithelps.gui.tree.TreeConnectionPaths;
import com.github.bandithelps.utils.stamina.StaminaProperties;
import com.github.bandithelps.utils.tree.ConnectionPathProperties;
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
    private int yha$activationStamina = 0;

    @Unique
    private int yha$staminaInterval = 0;

    @Unique
    private int yha$staminaIntervalCost = 0;

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
                    staminaProperties.yha$setActivationStamina(yha$readInt(ops, input, YHA_ACTIVATION_STAMINA_KEY, 0));
                    staminaProperties.yha$setStaminaInterval(yha$readInt(ops, input, YHA_STAMINA_INTERVAL_KEY, 0));
                    staminaProperties.yha$setStaminaIntervalCost(yha$readInt(ops, input, YHA_STAMINA_INTERVAL_COST_KEY, 0));
                    ConnectionPathProperties.of(properties).yha$setGuiConnections(yha$readConnections(ops, input));
                    return decoded;
                });
            }

            @Override
            public <T> DataResult<T> encode(AbilityProperties input, DynamicOps<T> ops, T prefix) {
                StaminaProperties staminaProperties = StaminaProperties.of(input);
                ConnectionPathProperties connectionProperties = ConnectionPathProperties.of(input);
                return baseCodec.encode(input, ops, prefix).flatMap((encoded) ->
                        yha$writeInt(ops, encoded, YHA_ACTIVATION_STAMINA_KEY, staminaProperties.yha$getActivationStamina())
                                .flatMap((withInitial) ->
                                        yha$writeInt(ops, withInitial, YHA_STAMINA_INTERVAL_KEY, staminaProperties.yha$getStaminaInterval())
                                                .flatMap((withInterval) ->
                                                        yha$writeInt(ops, withInterval, YHA_STAMINA_INTERVAL_COST_KEY, staminaProperties.yha$getStaminaIntervalCost())
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
    public int yha$getActivationStamina() {
        return this.yha$activationStamina;
    }

    @Override
    public void yha$setActivationStamina(int value) {
        this.yha$activationStamina = Math.max(0, value);
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

    @Override
    public TreeConnectionPaths yha$getGuiConnections() {
        return this.yha$guiConnections == null ? TreeConnectionPaths.EMPTY : this.yha$guiConnections;
    }

    @Override
    public void yha$setGuiConnections(TreeConnectionPaths paths) {
        this.yha$guiConnections = paths == null || paths.isEmpty() ? TreeConnectionPaths.EMPTY : paths.copy();
    }
}
