package com.github.bandithelps.conditions;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.DamageState;
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

/**
 * A condition designed to check either the DamageState of a body part, or the specific value the health of the part
 * is at.
 */
public record BodyHealthCondition(String part, String checkType, NumberComparator operator, String state, Value target) implements Condition {
    public static final MapCodec<BodyHealthCondition> CODEC =
            RecordCodecBuilder.mapCodec((instance) -> instance.group(
                            Codec.STRING.fieldOf("part").forGetter(BodyHealthCondition::part),
                            Codec.STRING.fieldOf("check_type").forGetter(BodyHealthCondition::checkType),
                    NumberComparator.CODEC.optionalFieldOf("operator", NumberComparator.EQUALS).forGetter(BodyHealthCondition::operator),
                            Codec.STRING.optionalFieldOf("state", "healthy").forGetter(BodyHealthCondition::state),
                    Value.CODEC.optionalFieldOf("target", new StaticValue(0)).forGetter(BodyHealthCondition::target))
                    .apply(instance, BodyHealthCondition::new));


    public boolean test(DataContext dataContext) {
        Player p = dataContext.getPlayer();

        if (p instanceof ServerPlayer player) {
            BodyPart part = BodyPart.fromId(this.part);
            if (part == null) return false;
            IBodyData body = BodyAttachments.get(player);

            if (this.checkType.equals("value")) {
                Object target = this.target.get(dataContext);
                if (target instanceof Number num2) {
                    Number num1 = body.getHealth(player, part);
                    return this.operator.compare(num1, num2);
                }
            } else { // defaults to "state" in the event they type it wrong
                DamageState checkState = DamageState.fromId(this.state);

                if (checkState != null) {
                    if (this.operator == NumberComparator.EQUALS) {
                        return checkState.equals(body.getDamageState(player, part));
                    } else if (this.operator == NumberComparator.NOT) {
                        return !checkState.equals(body.getDamageState(player, part));
                    }
                }
            }
        }

        return false;
    }

    public ConditionSerializer<?> getSerializer() {
        return ConditionRegister.BODY_HEALTH.get();
    }

    public static class Serializer extends ConditionSerializer<BodyHealthCondition> {
        public MapCodec<BodyHealthCondition> codec() {
            return BodyHealthCondition.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Condition, BodyHealthCondition> builder, HolderLookup.Provider provider) {
            builder.setName("Body Health Check")
                    .setDescription("Checks the current state or value of the health of a body part")
                    .add("part", ModSettingTypes.TYPE_BODY_PART, "The part to check the health of")
                    .add("check_type", ModSettingTypes.TYPE_BODY_HEALTH_CHECK, "The type of comparison to do. \"state\" checks the damage state of the part. \"value\" compares the health to the specified value.")
                    .add("state", ModSettingTypes.TYPE_DAMAGE_STATE, "The state to compare against the current health state of the body part")
                    .add("operator", TYPE_NUMBER_COMPARATOR, "How to compare the current health to the target health. (If check_type is \"state\" then this accepts EQUALS or NOT)")
                    .add("target", TYPE_VALUE, "The value that the health is being compared against. (Only applies if check_type is set to \"value\")")
                    .addExampleObject(new BodyHealthCondition(BodyPart.CHEST.getId(), "state", NumberComparator.EQUALS, "destroyed", new StaticValue(0)))
                    .addExampleObject(new BodyHealthCondition(BodyPart.CHEST.getId(), "value", NumberComparator.LESS_OR_EQUAL, "null", new StaticValue(50.0)));
        }
    }
}
