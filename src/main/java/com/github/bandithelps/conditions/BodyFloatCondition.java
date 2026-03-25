package com.github.bandithelps.conditions;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.IBodyData;
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
import net.threetag.palladium.util.NumberComparator;

public record BodyFloatCondition(String part, String key, NumberComparator operator, Value target) implements Condition {

    public static final MapCodec<BodyFloatCondition> CODEC =
            RecordCodecBuilder.mapCodec((instance) -> instance.group(
                            Codec.STRING.fieldOf("part").forGetter(BodyFloatCondition::part),
                            Codec.STRING.fieldOf("key").forGetter(BodyFloatCondition::key),
                            NumberComparator.CODEC.fieldOf("operator").forGetter(BodyFloatCondition::operator),
                            Value.CODEC.optionalFieldOf("target", new StaticValue(0)).forGetter(BodyFloatCondition::target))
                    .apply(instance, BodyFloatCondition::new));


    public boolean test(DataContext dataContext) {
        Player p = dataContext.getPlayer();

        if (p instanceof ServerPlayer player) {
            BodyPart part = BodyPart.fromId(this.part);
            if (part == null) return false;
            IBodyData body = BodyAttachments.get(player);

            Object target = this.target.get(dataContext);
            if (target instanceof Number num2) {
                Number num1 = body.getCustomFloat(player, part, this.key, 0);
                return this.operator.compare(num1, num2);
            }
        }

        return false;
    }

    public ConditionSerializer<?> getSerializer() {
        return ConditionRegister.BODY_FLOAT.get();
    }

    public static class Serializer extends ConditionSerializer<BodyFloatCondition> {
        public MapCodec<BodyFloatCondition> codec() {
            return BodyFloatCondition.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Condition, BodyFloatCondition> builder, HolderLookup.Provider provider) {
            builder.setName("Body Float Check")
                    .setDescription("Compares the current float value to the another value")
                    .add("part", ModSettingTypes.TYPE_BODY_PART, "The part in which the float is assigned")
                    .add("key", TYPE_STRING, "The key of the custom float that you are attempting to do the comparison on.")
                    .add("operator", TYPE_NUMBER_COMPARATOR, "How to compare the current float to the target value.")
                    .add("target", TYPE_VALUE, "The value that the float is being compared against.")
                    .addExampleObject(new BodyFloatCondition(BodyPart.CHEST.getId(), "energy", NumberComparator.EQUALS, new StaticValue(100)));
        }
    }

}
