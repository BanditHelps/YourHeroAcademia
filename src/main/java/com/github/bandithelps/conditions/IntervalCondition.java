package com.github.bandithelps.conditions;

import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.condition.Condition;
import net.threetag.palladium.logic.condition.ConditionSerializer;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;

public record IntervalCondition(Value interval) implements Condition {

    public static final MapCodec<IntervalCondition> CODEC =
            RecordCodecBuilder.mapCodec((instance) -> instance.group(
                            Value.CODEC.optionalFieldOf("interval", new StaticValue(24000)).forGetter(IntervalCondition::interval))
                    .apply(instance, IntervalCondition::new));


    public boolean test(DataContext dataContext) {
        Player p = dataContext.getPlayer();

        if (p instanceof ServerPlayer player) {
            long gameTime = player.level().getGameTime();

            long target = Long.parseLong(this.interval.get(dataContext).toString());
            return target > 0 && gameTime % target == 0;
        }

        return false;
    }

    public ConditionSerializer<?> getSerializer() {
        return ConditionRegister.INTERVAL.get();
    }

    public static class Serializer extends ConditionSerializer<IntervalCondition> {
        public MapCodec<IntervalCondition> codec() {
            return IntervalCondition.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Condition, IntervalCondition> builder, HolderLookup.Provider provider) {
            builder.setName("Game Time Interval Check")
                    .setDescription("Returns true if the current game time is a multiple of the interval")
                    .add("interval", TYPE_VALUE, "The interval to check against the game time. Default is 24000 (1 Minecraft day).")
                    .addExampleObject(new IntervalCondition(new StaticValue(24000)));
        }
    }
}