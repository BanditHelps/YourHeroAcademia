package com.github.bandithelps.abilities.bodydata;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.*;
import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.threetag.palladium.util.PalladiumCodecs;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class DisplayBodyBarAbility extends Ability {
    public static final MapCodec<DisplayBodyBarAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter((ab) -> ab.id),
                    Codec.STRING.optionalFieldOf("label", "null").forGetter((ab) -> ab.label),
                    Codec.STRING.fieldOf("part").forGetter((ab) -> ab.part),
                    Codec.STRING.fieldOf("key").forGetter((ab) -> ab.key),
                    Value.CODEC.fieldOf("min").forGetter((ab) -> ab.min),
                    Value.CODEC.fieldOf("max").forGetter((ab) -> ab.max),
                    PalladiumCodecs.COLOR_CODEC.fieldOf("left_color").forGetter((ab) -> ab.gradientLeft),
                    PalladiumCodecs.COLOR_CODEC.fieldOf("right_color").forGetter((ab) -> ab.gradientRight),
                    PalladiumCodecs.COLOR_CODEC.optionalFieldOf("slider_color", Color.decode("#FFFFFF")).forGetter((ab) -> ab.sliderColor),
                    Codec.STRING.fieldOf("bar_type").forGetter((ab) -> ab.barType),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, DisplayBodyBarAbility::new));

    public final String id;
    public final String label;
    public final String part;
    public final String key;
    public final Value min;
    public final Value max;
    public final Color gradientLeft; // Required and fall back for everything
    public final Color gradientRight;
    public final Color sliderColor;
    public final String barType;

    public float originalMin;
    public float originalMax;


    public DisplayBodyBarAbility(String id, String label, String part, String key, Value min, Value max, Color gradientLeft, Color gradientRight, Color sliderColor, String barType, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.id = id;
        this.label = label;
        this.part = part;
        this.key = key;
        this.min = min;
        this.max = max;
        this.gradientLeft = gradientLeft;
        this.gradientRight = gradientRight;
        this.sliderColor = sliderColor;
        this.barType = barType;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            // error checking for a bad bar
            float minValue = this.min.getAsFloat(DataContext.forEntity(entity));
            float maxValue = this.max.getAsFloat(DataContext.forEntity(entity));

            updateBodyDisplayBar(minValue, maxValue, player);

            // cache the original values for min and max to help make efficient future changes
            originalMin = minValue;
            originalMax = maxValue;
        }
    }

    /*
     * The only things that we need to check here are if the two Values for max and min changed, then
     * update the bar accordingly.
     */
    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            float minValue = this.min.getAsFloat(DataContext.forEntity(entity));
            float maxValue = this.max.getAsFloat(DataContext.forEntity(entity));

            // If there is a change in these, recreate/update the body bar.
            if (minValue != originalMin || maxValue != originalMax) {
                updateBodyDisplayBar(minValue, maxValue, player);
                originalMin = minValue;
                originalMax = maxValue;
            }
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    /*
     * Cleanup the display bar, and remove it.
     *
     */
    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            BodyAttachments.get(player).removeDisplayBar(id);
            BodySyncEvents.syncNow(player);
        }
    }

    private void updateBodyDisplayBar(float minValue, float maxValue, ServerPlayer player) {
        BodyPart bodyPart = BodyPart.fromId(this.part);

        if (minValue >= maxValue) {
            player.sendSystemMessage(Component.literal("Error detected in DisplayBodyAbility | \"min\" cannot be >= \"max\"! "));
            return;
        }

        if (bodyPart == null) {
            player.sendSystemMessage(Component.literal("Error detected in DisplayBodyAbility | invalid \"part\" detected!"));
            return;
        }

        int leftColor = this.gradientLeft.getRGB();
        int rightColor = this.gradientRight.getRGB();

        BodyDisplayBar displayBar;

        if (this.barType.equals("slider")) {
            int sliderColor = this.sliderColor.getRGB();

            displayBar = new BodyDisplayBar(
                    this.id,
                    this.label,
                    bodyPart,
                    this.key,
                    minValue,
                    maxValue,
                    sliderColor,
                    sliderColor,
                    BodyDisplayBar.DEFAULT_SLIDER_COLOR_RGB,
                    leftColor,
                    rightColor,
                    BodyDisplayBarType.SLIDER
            );

        } else { // "bar"
            displayBar = new BodyDisplayBar(
                    this.id,
                    this.label,
                    bodyPart,
                    this.key,
                    minValue,
                    maxValue,
                    leftColor,
                    BodyDisplayBar.DEFAULT_SLIDER_COLOR_RGB,
                    leftColor,
                    leftColor,
                    rightColor,
                    BodyDisplayBarType.FILL
            );
        }

        BodyAttachments.get(player).setDisplayBar(displayBar);
        BodySyncEvents.syncNow(player);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.DISPLAY_BODY_BAR.get();
    }

    /*
     * Serializer for the documentation
     */
    public static class Serializer extends AbilitySerializer<DisplayBodyBarAbility> {
        public MapCodec<DisplayBodyBarAbility> codec() { return DisplayBodyBarAbility.CODEC; }

        public void addDocumentation(CodecDocumentationBuilder<Ability, DisplayBodyBarAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Registers a new display bar for any custom float stored in a body part.")
                    .add("id", TYPE_STRING, "A unique id to associate with the display bar.")
                    .add("label", TYPE_STRING, "A name to give the bar when debug mode is active.")
                    .add("part", ModSettingTypes.TYPE_BODY_PART, "The body part that the custom float value is stored in.")
                    .add("key", TYPE_STRING, "The key assigned to the custom float that you are trying to retrieve.")
                    .add("min", TYPE_VALUE, "The minimum value that the left side of the display bar will represent.")
                    .add("max", TYPE_VALUE, "The maximum value that the right side of the display bar will represent.")
                    .add("left_color", TYPE_COLOR, "This value is the color of the left side of the bar.")
                    .add("right_color", TYPE_COLOR, "This value is the color of the right side of the bar. If different than the \"left_color\", creates a gradient between the left_color and right_color. Leave blank for a solid color.")
                    .add("slider_color", TYPE_COLOR, "The color of the slider if the bar_type is set to \"slider\".")
                    .add("bar_type", ModSettingTypes.TYPE_BODY_BAR, "What kind of bar to you want to add.")
                    .addExampleObject(new DisplayBodyBarAbility("example_bar", "Example", BodyPart.CHEST.getId(), "example_float", new StaticValue(0.0f), new StaticValue(100.0f), Color.decode("#FFFFFF"), Color.decode("#FFFFFF"), Color.decode("#FFFFFF"), "bar", AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }

    }
}
