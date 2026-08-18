package com.github.bandithelps.gui.ui.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.EasingType;
import net.minecraft.util.Mth;
import net.threetag.palladium.client.gui.screen.DelayedRenderCallReceiver;
import net.threetag.palladium.client.gui.screen.power.PowerUiScreen;
import net.threetag.palladium.client.gui.ui.background.RepeatingTextureBackground;
import net.threetag.palladium.client.gui.ui.background.UiBackground;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.client.gui.widget.PowerTreeWidget;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.power.PowerInstance;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.registry.PalladiumRegistryKeys;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class ZoomablePowerTreeUiComponent extends UiWidget {
    public static final MapCodec<ZoomablePowerTreeUiComponent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceKey.codec(PalladiumRegistryKeys.POWER).optionalFieldOf("power").forGetter(c -> Optional.ofNullable(c.power)),
            UiBackground.Codecs.CODEC.optionalFieldOf("background", RepeatingTextureBackground.RED_WOOL).forGetter(c -> c.background),
            Codec.FLOAT.optionalFieldOf("default_zoom", 1.0F).forGetter(ZoomablePowerTreeUiComponent::getDefaultZoom),
            propertiesCodec()
    ).apply(instance, (power, background, defaultZoom, props) ->
            new ZoomablePowerTreeUiComponent((ResourceKey<Power>) power.orElse(null), background, defaultZoom, props)));

    @Nullable
    private final ResourceKey<Power> power;
    private final UiBackground background;
    private final float defaultZoom;

    public ZoomablePowerTreeUiComponent(
            @Nullable ResourceKey<Power> power,
            UiBackground background,
            float defaultZoom,
            UiWidgetProperties properties
    ) {
        super(properties);
        this.power = power;
        this.background = background;
        this.defaultZoom = defaultZoom;
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.ZOOMABLE_POWER_TREE;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle, DataContext context) {
        PowerInstance powerInstance = null;
        if (this.power != null) {
            powerInstance = PowerUtil.getPowerHandler(screen.getMinecraft().player).getPowerInstance(this.power.identifier());
        } else if (screen instanceof PowerUiScreen powerUiScreen) {
            powerInstance = powerUiScreen.getPowerInstance();
        }

        return new ZoomablePowerTreeWidget(
                screen,
                powerInstance,
                this.background,
                this.getX(rectangle, context),
                this.getY(rectangle, context),
                this.getWidth(context),
                this.getHeight(context),
                this.defaultZoom
        );
    }

    public float getDefaultZoom() {
        return this.defaultZoom;
    }

    public static class Serializer extends UiWidgetSerializer<ZoomablePowerTreeUiComponent> {
        @Override
        public MapCodec<ZoomablePowerTreeUiComponent> codec() {
            return ZoomablePowerTreeUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, ZoomablePowerTreeUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Zoomable Power Tree")
                    .setDescription("Power tree with scroll-wheel zoom. All other behavior matches palladium:power_tree.")
                    .addOptional("power", TYPE_POWER, "The power that will be displayed. If none is specified, it will use the from the current power screen (if this widget is used in one).")
                    .addOptional("background", TYPE_UI_BACKGROUND, "The background that is drawn for the power tree.", RepeatingTextureBackground.RED_WOOL.toString())
                    .addOptional("default_zoom", TYPE_FLOAT, "Initial zoom level. 1.0 is Palladium's default scale.", "1.0");
        }
    }

    public static class ZoomablePowerTreeWidget extends PowerTreeWidget {
        private static final float MIN_ZOOM = 0.4F;
        private static final float MAX_ZOOM = 2.5F;
        private static final float ZOOM_STEP = 1.1F;
        private static final int ABILITY_HIT_RADIUS = 13;
        private static final Field OFFSET_X_FIELD = getField("offsetX");
        private static final Field OFFSET_Y_FIELD = getField("offsetY");
        private static final Field INNER_WIDTH_FIELD = getField("innerWidth");
        private static final Field INNER_HEIGHT_FIELD = getField("innerHeight");
        private static final Field BACKGROUND_FIELD = getField("background");
        private static final Field PARENT_FIELD = getField("parent");
        private static final Field ENABLE_FADE_FIELD = getField("enableFade");
        private static final Field FADE_FIELD = getField("fade");
        private static final Field FADE0_FIELD = getField("fade0");
        private static final Method EXTRACT_INNER = getMethod(
                "extractInner",
                GuiGraphicsExtractor.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class
        );

        private float zoom;

        public ZoomablePowerTreeWidget(
                UiScreen parent,
                PowerInstance powerInstance,
                UiBackground background,
                int x,
                int y,
                int width,
                int height,
                float defaultZoom
        ) {
            super(parent, powerInstance, background, x, y, width, height);
            this.zoom = Mth.clamp(defaultZoom, MIN_ZOOM, MAX_ZOOM);
            clampOffsets();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (this.abilities.isEmpty()) {
                super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
                return;
            }

            guiGraphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
            Matrix3x2fStack pose = guiGraphics.pose();
            pose.pushMatrix();
            pose.translate(this.getX(), this.getY());
            pose.scale(this.zoom, this.zoom);
            pose.translate(-getInt(OFFSET_X_FIELD), -getInt(OFFSET_Y_FIELD));
            renderBackgroundOverVisibleArea(guiGraphics);
            invokeExtractInner(
                    guiGraphics,
                    0,
                    0,
                    getInt(INNER_WIDTH_FIELD),
                    getInt(INNER_HEIGHT_FIELD),
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE
            );
            pose.popMatrix();
            guiGraphics.disableScissor();

            guiGraphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    SPRITE_VIGNETTE,
                    this.getX(),
                    this.getY(),
                    this.getWidth(),
                    this.getHeight()
            );

            AbilityElement hovered = findHoveredAbility(mouseX, mouseY);
            if (hovered != null) {
                Screen parent = getParentScreen();
                if (parent instanceof DelayedRenderCallReceiver receiver) {
                    int screenX = Math.round(this.getX() + (hovered.getX() - getInt(OFFSET_X_FIELD)) * this.zoom);
                    int screenY = Math.round(this.getY() + (hovered.getY() - getInt(OFFSET_Y_FIELD)) * this.zoom);
                    receiver.renderDelayed(gui -> hovered.extractHovered(gui, screenX, screenY));
                    setBoolean(ENABLE_FADE_FIELD, true);
                }
            }

            float fade = EasingType.IN_OUT_CUBIC.apply(Mth.lerp(partialTick, getInt(FADE0_FIELD), getInt(FADE_FIELD)) / 10.0F) / 3.0F;
            if (fade > 0.0F) {
                guiGraphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), ARGB.colorFromFloat(fade, 0.0F, 0.0F, 0.0F));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
            if (!this.isActive() || !this.isValidClickButton(event.buttonInfo()) || !this.isMouseOver(event.x(), event.y())) {
                return false;
            }

            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            for (AbilityElement ability : this.abilities) {
                if (!isMouseOverAbilityZoomed(ability, mouseX, mouseY)) {
                    continue;
                }
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                boolean handled = ability.parentsUnlocked
                        && !ability.isUnlocked()
                        && ability.getAbilityInstance().getAbility().getStateManager().getUnlockingHandler()
                        .onClicked(getParentScreen().getMinecraft().player, ability.getAbilityInstance());
                if (!handled) {
                    ability.openModal(getParentScreen());
                }
                return true;
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!this.isMouseOver(mouseX, mouseY) || scrollY == 0.0) {
                return false;
            }

            float factor = scrollY > 0.0 ? ZOOM_STEP : (1.0F / ZOOM_STEP);
            float newZoom = Mth.clamp(this.zoom * factor, MIN_ZOOM, MAX_ZOOM);
            if (Mth.equal(newZoom, this.zoom)) {
                return true;
            }

            double focusX = (mouseX - this.getX()) / this.zoom + getInt(OFFSET_X_FIELD);
            double focusY = (mouseY - this.getY()) / this.zoom + getInt(OFFSET_Y_FIELD);
            this.zoom = newZoom;
            setInt(OFFSET_X_FIELD, (int) Math.round(focusX - (mouseX - this.getX()) / this.zoom));
            setInt(OFFSET_Y_FIELD, (int) Math.round(focusY - (mouseY - this.getY()) / this.zoom));
            clampOffsets();
            return true;
        }

        @Override
        public void drag(double dragX, double dragY) {
            setInt(OFFSET_X_FIELD, (int) Mth.clamp(
                    getInt(OFFSET_X_FIELD) - dragX / this.zoom,
                    minOffsetX(),
                    maxOffsetX()
            ));
            setInt(OFFSET_Y_FIELD, (int) Mth.clamp(
                    getInt(OFFSET_Y_FIELD) - dragY / this.zoom,
                    minOffsetY(),
                    maxOffsetY()
            ));
        }

        private void renderBackgroundOverVisibleArea(GuiGraphicsExtractor guiGraphics) {
            int offsetX = getInt(OFFSET_X_FIELD);
            int offsetY = getInt(OFFSET_Y_FIELD);
            int innerWidth = getInt(INNER_WIDTH_FIELD);
            int innerHeight = getInt(INNER_HEIGHT_FIELD);
            int viewWidth = Mth.ceil(this.getWidth() / this.zoom);
            int viewHeight = Mth.ceil(this.getHeight() / this.zoom);
            int tileWidth = 16;
            int tileHeight = 16;
            UiBackground background = getBackground();
            if (background instanceof RepeatingTextureBackground repeating) {
                tileWidth = Math.max(1, repeating.getWidth());
                tileHeight = Math.max(1, repeating.getHeight());
            }

            int viewLeft = offsetX;
            int viewTop = offsetY;
            int viewRight = offsetX + viewWidth;
            int viewBottom = offsetY + viewHeight;
            int bgLeft = Math.floorDiv(Math.min(0, viewLeft), tileWidth) * tileWidth;
            int bgTop = Math.floorDiv(Math.min(0, viewTop), tileHeight) * tileHeight;
            int bgRight = Math.ceilDiv(Math.max(innerWidth, viewRight), tileWidth) * tileWidth;
            int bgBottom = Math.ceilDiv(Math.max(innerHeight, viewBottom), tileHeight) * tileHeight;
            background.render(guiGraphics, bgLeft, bgTop, bgRight - bgLeft, bgBottom - bgTop);
        }

        private AbilityElement findHoveredAbility(int mouseX, int mouseY) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return null;
            }
            for (AbilityElement ability : this.abilities) {
                if (isMouseOverAbilityZoomed(ability, mouseX, mouseY)) {
                    return ability;
                }
            }
            return null;
        }

        private boolean isMouseOverAbilityZoomed(AbilityElement ability, int mouseX, int mouseY) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return false;
            }
            int screenX = Math.round(this.getX() + (ability.getX() - getInt(OFFSET_X_FIELD)) * this.zoom);
            int screenY = Math.round(this.getY() + (ability.getY() - getInt(OFFSET_Y_FIELD)) * this.zoom);
            int radius = Math.round(ABILITY_HIT_RADIUS * this.zoom);
            return mouseX >= screenX - radius
                    && mouseX <= screenX + radius
                    && mouseY >= screenY - radius
                    && mouseY <= screenY + radius;
        }

        private void clampOffsets() {
            setInt(OFFSET_X_FIELD, (int) Mth.clamp(getInt(OFFSET_X_FIELD), minOffsetX(), maxOffsetX()));
            setInt(OFFSET_Y_FIELD, (int) Mth.clamp(getInt(OFFSET_Y_FIELD), minOffsetY(), maxOffsetY()));
        }

        private double minOffsetX() {
            return Math.min(0.0, getInt(INNER_WIDTH_FIELD) - this.getWidth() / this.zoom);
        }

        private double maxOffsetX() {
            return Math.max(0.0, getInt(INNER_WIDTH_FIELD) - this.getWidth() / this.zoom);
        }

        private double minOffsetY() {
            return Math.min(0.0, getInt(INNER_HEIGHT_FIELD) - this.getHeight() / this.zoom);
        }

        private double maxOffsetY() {
            return Math.max(0.0, getInt(INNER_HEIGHT_FIELD) - this.getHeight() / this.zoom);
        }

        private UiBackground getBackground() {
            try {
                return (UiBackground) BACKGROUND_FIELD.get(this);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read PowerTreeWidget field: background", exception);
            }
        }

        private Screen getParentScreen() {
            try {
                return (Screen) PARENT_FIELD.get(this);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read PowerTreeWidget field: parent", exception);
            }
        }

        private void invokeExtractInner(GuiGraphicsExtractor gui, int x, int y, int width, int height, int mouseX, int mouseY) {
            try {
                EXTRACT_INNER.invoke(this, gui, x, y, width, height, mouseX, mouseY);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke PowerTreeWidget.extractInner", exception);
            }
        }

        private int getInt(Field field) {
            try {
                return field.getInt(this);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read PowerTreeWidget field: " + field.getName(), exception);
            }
        }

        private void setInt(Field field, int value) {
            try {
                field.setInt(this, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to write PowerTreeWidget field: " + field.getName(), exception);
            }
        }

        private void setBoolean(Field field, boolean value) {
            try {
                field.setBoolean(this, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to write PowerTreeWidget field: " + field.getName(), exception);
            }
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

        private static Method getMethod(String name, Class<?>... parameterTypes) {
            try {
                Method method = PowerTreeWidget.class.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to access PowerTreeWidget method: " + name, exception);
            }
        }
    }
}
