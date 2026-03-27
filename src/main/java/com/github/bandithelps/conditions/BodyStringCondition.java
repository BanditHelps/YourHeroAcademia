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
import net.threetag.palladium.util.StringComparator;

public record BodyStringCondition(String part, String key, StringComparator operator, String target) implements Condition {

    public static final MapCodec<BodyStringCondition> CODEC =
            RecordCodecBuilder.mapCodec((instance) -> instance.group(
                            Codec.STRING.fieldOf("part").forGetter(BodyStringCondition::part),
                            Codec.STRING.fieldOf("key").forGetter(BodyStringCondition::key),
                            StringComparator.CODEC.fieldOf("operator").forGetter(BodyStringCondition::operator),
                            Codec.STRING.fieldOf("target").forGetter(BodyStringCondition::target))
                    .apply(instance, BodyStringCondition::new));


    public boolean test(DataContext dataContext) {
        Player p = dataContext.getPlayer();

        if (p instanceof ServerPlayer player) {
            BodyPart part = BodyPart.fromId(this.part);
            if (part == null) return false;

            IBodyData body = BodyAttachments.get(player);
            String curString = body.getCustomString(player, part, this.key);
            if (curString == null) return false;
            return this.operator.compare(curString, this.target);
        }

        return false;
    }

    public ConditionSerializer<?> getSerializer() {
        return ConditionRegister.BODY_STRING.get();
    }

    public static class Serializer extends ConditionSerializer<BodyStringCondition> {
        public MapCodec<BodyStringCondition> codec() {
            return BodyStringCondition.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Condition, BodyStringCondition> builder, HolderLookup.Provider provider) {
            builder.setName("Body String Check")
                    .setDescription("Compares the current string value to the another value")
                    .add("part", ModSettingTypes.TYPE_BODY_PART, "The part in which the float is assigned")
                    .add("key", TYPE_STRING, "The key of the custom string that you are attempting to do the comparison on.")
                    .add("operator", TYPE_STRING_COMPARATOR, "How to compare the current string to the target value.")
                    .add("target", TYPE_VALUE, "The value that the string is being compared against.")
                    .addExampleObject(new BodyStringCondition(BodyPart.CHEST.getId(), "charge_state", StringComparator.EQUALS, "fully_charged"));
        }
    }
}