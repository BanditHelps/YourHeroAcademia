package com.github.bandithelps.gui.ui.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.threetag.palladium.client.gui.screen.power.PowerUiScreen;
import net.threetag.palladium.client.gui.ui.background.RepeatingTextureBackground;
import net.threetag.palladium.client.gui.ui.background.UiBackground;
import net.threetag.palladium.client.gui.ui.component.PowerTreeUiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.widget.PowerTreePopulator;
import net.threetag.palladium.client.gui.widget.PowerTreeWidget;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.power.PowerInstance;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.registry.PalladiumRegistryKeys;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Optional;

public class AnchoredPowerTreeUiComponent extends UiComponent {
    public static final MapCodec<AnchoredPowerTreeUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            ResourceKey.codec(PalladiumRegistryKeys.POWER).optionalFieldOf("power").forGetter((c) -> Optional.ofNullable(c.power)),
            UiBackground.Codecs.CODEC.optionalFieldOf("background", RepeatingTextureBackground.RED_WOOL).forGetter((c) -> c.background),
            Codec.BOOL.optionalFieldOf("auto_center", false).forGetter(AnchoredPowerTreeUiComponent::isAutoCenter),
            Codec.BOOL.optionalFieldOf("scrollable", false).forGetter(AnchoredPowerTreeUiComponent::isScrollable),
            Codec.FLOAT.optionalFieldOf("start_grid_x", 0.0F).forGetter(AnchoredPowerTreeUiComponent::getStartGridX),
            Codec.FLOAT.optionalFieldOf("start_grid_y", 0.0F).forGetter(AnchoredPowerTreeUiComponent::getStartGridY),
            propertiesCodec()
    ).apply(instance, (power, background, autoCenter, scrollable, startGridX, startGridY, props) ->
            new AnchoredPowerTreeUiComponent((ResourceKey<Power>) power.orElse(null), background, autoCenter, scrollable, startGridX, startGridY, props)));

    @Nullable
    private final ResourceKey<Power> power;
    private final UiBackground background;
    private final boolean autoCenter;
    private final boolean scrollable;
    private final float startGridX;
    private final float startGridY;

    public AnchoredPowerTreeUiComponent(
            @Nullable ResourceKey<Power> power,
            UiBackground background,
            boolean autoCenter,
            boolean scrollable,
            float startGridX,
            float startGridY,
            UiComponentProperties properties
    ) {
        super(properties);
        this.power = power;
        this.background = background;
        this.autoCenter = autoCenter;
        this.scrollable = scrollable;
        this.startGridX = startGridX;
        this.startGridY = startGridY;
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.ANCHORED_POWER_TREE;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle) {
        PowerInstance powerInstance = null;
        if (this.power != null) {
            powerInstance = PowerUtil.getPowerHandler(screen.getMinecraft().player).getPowerInstance(this.power.identifier());
        } else if (screen instanceof PowerUiScreen powerUiScreen) {
            powerInstance = powerUiScreen.getPowerInstance();
        }

        return new AnchoredPowerTreeWidget(
                screen,
                powerInstance,
                this.background,
                this.getX(rectangle),
                this.getY(rectangle),
                this.getWidth(),
                this.getHeight(),
                this.autoCenter,
                this.scrollable,
                this.startGridX,
                this.startGridY
        );
    }

    public boolean isAutoCenter() {
        return autoCenter;
    }

    public boolean isScrollable() {
        return scrollable;
    }

    public float getStartGridX() {
        return startGridX;
    }

    public float getStartGridY() {
        return startGridY;
    }

    public static class Serializer extends UiComponentSerializer<AnchoredPowerTreeUiComponent> {
        @Override
        public MapCodec<AnchoredPowerTreeUiComponent> codec() {
            return AnchoredPowerTreeUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, AnchoredPowerTreeUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Anchored Power Tree")
                    .setDescription("Power tree with optional fixed origin and optional drag scrolling.")
                    .addOptional("power", TYPE_POWER, "Power to render; defaults to active power on a power screen.")
                    .addOptional("background", TYPE_UI_BACKGROUND, "Background drawn behind the tree.", RepeatingTextureBackground.RED_WOOL.toString())
                    .addOptional("auto_center", TYPE_BOOLEAN, "Use Palladium's auto-centering behavior.", "false")
                    .addOptional("scrollable", TYPE_BOOLEAN, "Allow drag panning.", "false")
                    .addOptional("start_grid_x", TYPE_FLOAT, "Initial grid X origin (when auto_center is false).", "0")
                    .addOptional("start_grid_y", TYPE_FLOAT, "Initial grid Y origin (when auto_center is false).", "0");
        }
    }

    public static class AnchoredPowerTreeWidget extends PowerTreeWidget {
        private static final int GRID_SIZE = 50;
        private static final Field INNER_WIDTH_FIELD = getField("innerWidth");
        private static final Field INNER_HEIGHT_FIELD = getField("innerHeight");
        private static final Field OFFSET_X_FIELD = getField("offsetX");
        private static final Field OFFSET_Y_FIELD = getField("offsetY");

        private final boolean autoCenter;
        private final boolean scrollable;
        private final float startGridX;
        private final float startGridY;
        private boolean initializedView;

        public AnchoredPowerTreeWidget(
                UiScreen parent,
                PowerInstance powerInstance,
                UiBackground background,
                int x,
                int y,
                int width,
                int height,
                boolean autoCenter,
                boolean scrollable,
                float startGridX,
                float startGridY
        ) {
            super(parent, powerInstance, background, x, y, width, height);
            this.autoCenter = autoCenter;
            this.scrollable = scrollable;
            this.startGridX = startGridX;
            this.startGridY = startGridY;
            this.initializedView = false;
            populate(powerInstance);
        }

        @Override
        public void populate(PowerInstance powerInstance) {
            this.abilities.clear();
            int innerWidth = Math.max(500, this.getWidth());
            int innerHeight = Math.max(500, this.getHeight());

            if (powerInstance != null) {
                PowerTreePopulator.generateTree(powerInstance, this.abilities);
                innerWidth = (int) Math.max(PowerTreePopulator.getHighestXGridValue(this.abilities) * GRID_SIZE + GRID_SIZE, (float) this.getWidth());
                innerHeight = (int) Math.max(PowerTreePopulator.getHighestYGridValue(this.abilities) * GRID_SIZE + GRID_SIZE, (float) this.getHeight());
                if (this.autoCenter) {
                    PowerTreePopulator.center(this.abilities, innerWidth, innerHeight);
                }
                PowerTreePopulator.generateConnections(this.abilities);
            }

            setInt(INNER_WIDTH_FIELD, this, innerWidth);
            setInt(INNER_HEIGHT_FIELD, this, innerHeight);

            if (!this.autoCenter && !this.initializedView) {
                int targetX = Math.round(this.startGridX * GRID_SIZE + (GRID_SIZE / 2.0F));
                int targetY = Math.round(this.startGridY * GRID_SIZE + (GRID_SIZE / 2.0F));
                int offsetX = targetX - (this.getWidth() / 2);
                int offsetY = targetY - (this.getHeight() / 2);
                setInt(OFFSET_X_FIELD, this, offsetX);
                setInt(OFFSET_Y_FIELD, this, offsetY);
                this.initializedView = true;
            }

            clampOffsets();
        }

        @Override
        public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double mouseX, double mouseY) {
            if (!this.scrollable) {
                return false;
            }
            return super.mouseDragged(event, mouseX, mouseY);
        }

        @Override
        public void drag(double dragX, double dragY) {
            super.drag(dragX, dragY);
            clampOffsets();
        }

        private void clampOffsets() {
            int innerWidth = getInt(INNER_WIDTH_FIELD, this);
            int innerHeight = getInt(INNER_HEIGHT_FIELD, this);
            int offsetX = getInt(OFFSET_X_FIELD, this);
            int offsetY = getInt(OFFSET_Y_FIELD, this);

            offsetX = Math.max(0, Math.min(offsetX, Math.max(0, innerWidth - this.getWidth())));
            offsetY = Math.max(0, Math.min(offsetY, Math.max(0, innerHeight - this.getHeight())));

            setInt(OFFSET_X_FIELD, this, offsetX);
            setInt(OFFSET_Y_FIELD, this, offsetY);
        }

        private static Field getField(String name) {
            try {
                Field field = PowerTreeWidget.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to access PowerTreeWidget field: " + name, exception);
            }
        }

        private static int getInt(Field field, Object target) {
            try {
                return field.getInt(target);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read PowerTreeWidget field: " + field.getName(), exception);
            }
        }

        private static void setInt(Field field, Object target, int value) {
            try {
                field.setInt(target, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to write PowerTreeWidget field: " + field.getName(), exception);
            }
        }
    }
}
