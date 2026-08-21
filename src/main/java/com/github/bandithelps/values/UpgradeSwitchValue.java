package com.github.bandithelps.values;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.documentation.SettingType;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.logic.value.ValueSerializer;
import net.threetag.palladium.power.EntityPowerHandler;
import net.threetag.palladium.power.PowerInstance;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityReference;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.List;

/**
 * Picks the last unlocked case in an ordered upgrade list. Dummy tree nodes stay unlocked after
 * purchase, so listing cases from lowest to highest makes the highest purchased tier win.
 */
public class UpgradeSwitchValue extends Value {

    public record Case(AbilityReference ability, Value value) {
        public static final Codec<Case> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                AbilityReference.CODEC.fieldOf("ability").forGetter(Case::ability),
                Value.CODEC.fieldOf("value").forGetter(Case::value)
        ).apply(instance, Case::new));
    }

    public static final MapCodec<UpgradeSwitchValue> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Case.CODEC.listOf().fieldOf("cases").forGetter(value -> value.cases),
            Value.CODEC.optionalFieldOf("fallback", new StaticValue(0)).forGetter(value -> value.fallback)
    ).apply(instance, UpgradeSwitchValue::new));

    private final List<Case> cases;
    private final Value fallback;

    public UpgradeSwitchValue(List<Case> cases, Value fallback) {
        this.cases = List.copyOf(cases);
        this.fallback = fallback;
    }

    @Override
    public Object get(DataContext context) {
        LivingEntity entity = context.getLivingEntity();
        if (entity == null) {
            return this.fallback.get(context);
        }

        EntityPowerHandler handler = PowerUtil.getPowerHandler(entity);
        if (handler == null) {
            return this.fallback.get(context);
        }

        for (int index = this.cases.size() - 1; index >= 0; index--) {
            Case upgradeCase = this.cases.get(index);
            Identifier powerId = resolvePowerId(context, handler, upgradeCase.ability());
            if (powerId != null && AbilityUtil.isUnlocked(entity, powerId, upgradeCase.ability().abilityKey())) {
                return upgradeCase.value().get(context);
            }
        }
        return this.fallback.get(context);
    }

    private static Identifier resolvePowerId(DataContext context, EntityPowerHandler handler, AbilityReference reference) {
        if (reference.powerId() != null) {
            return reference.powerId();
        }

        AbilityInstance<?> currentAbility = context.getAbility();
        if (currentAbility != null && currentAbility.getPowerInstance() != null) {
            return currentAbility.getPowerInstance().getPowerId();
        }

        for (PowerInstance powerInstance : handler.getPowers()) {
            if (powerInstance.getAbilities().containsKey(reference.abilityKey())) {
                return powerInstance.getPowerId();
            }
        }
        return null;
    }

    @Override
    public ValueSerializer<?> getSerializer() {
        return ValueRegister.UPGRADE_SWITCH.get();
    }

    public static class Serializer extends ValueSerializer<UpgradeSwitchValue> {
        @Override
        public MapCodec<UpgradeSwitchValue> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Value, UpgradeSwitchValue> builder, HolderLookup.Provider provider) {
            builder.setName("Upgrade Switch")
                    .setDescription("Returns the value from the last unlocked ability in an ordered case list. List cases from lowest upgrade to highest.")
                    .add("cases", SettingType.list(TYPE_VALUE), "Ordered {ability, value} cases. Last unlocked case wins.")
                    .addOptional("fallback", TYPE_VALUE, "Returned if none of the listed abilities are unlocked.", "0")
                    .addExampleObject(new UpgradeSwitchValue(
                            List.of(
                                    new Case(AbilityReference.parse("upgrade_1"), new StaticValue(9)),
                                    new Case(AbilityReference.parse("upgrade_2"), new StaticValue(12))
                            ),
                            new StaticValue(9)
                    ));
        }
    }
}
