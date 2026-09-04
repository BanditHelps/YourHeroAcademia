package com.github.bandithelps.mixin;

import com.github.bandithelps.gui.tree.TreeConnectionPath;
import com.github.bandithelps.gui.tree.TreeConnectionPaths;
import com.github.bandithelps.gui.tree.TreeConnectionRenderer;
import com.github.bandithelps.gui.tree.TreeEditorStateSync;
import com.github.bandithelps.gui.tree.TreeConnectionRenderer.Pixel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec2;
import net.threetag.palladium.client.gui.widget.PowerTreeWidget;
import net.threetag.palladium.client.gui.widget.PowerTreeWidget.AbilityElement;
import net.threetag.palladium.power.ability.AbilityProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PowerTreeWidget.AbilityConnection.class)
public abstract class AbilityConnectionMixin {

    @Shadow
    public AbilityElement parent;

    @Shadow
    public List<AbilityElement> children;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void yha$drawCustomConnections(GuiGraphicsExtractor guiGraphics, int x, int y, boolean outline, Color color, CallbackInfo ci) {
        if (this.children == null || this.children.isEmpty() || this.parent == null) {
            return;
        }
        List<AbilityElement> custom = new ArrayList<>();
        List<AbilityElement> defaults = new ArrayList<>();
        for (AbilityElement child : this.children) {
            if (yha$pathOf(child, this.parent).isEmpty()) {
                defaults.add(child);
            } else {
                custom.add(child);
            }
        }
        if (custom.isEmpty()) {
            return;
        }
        ci.cancel();
        int colorCode = color.getRGB();
        if (!defaults.isEmpty()) {
            List<Integer> childXs = new ArrayList<>(defaults.size());
            List<Integer> childYs = new ArrayList<>(defaults.size());
            for (AbilityElement child : defaults) {
                childXs.add(x + child.getX());
                childYs.add(y + child.getY());
            }
            TreeConnectionRenderer.drawBus(
                    guiGraphics,
                    x + this.parent.getX(),
                    y + this.parent.getY(),
                    childXs,
                    childYs,
                    outline,
                    colorCode
            );
        }
        for (AbilityElement child : custom) {
            TreeConnectionRenderer.drawPolyline(guiGraphics, yha$polyline(x, y, this.parent, child), outline, colorCode);
        }
    }

    @Unique
    private static TreeConnectionPath yha$pathOf(AbilityElement child, AbilityElement parent) {
        AbilityProperties properties = child.getAbilityInstance().getAbility().getProperties();
        TreeConnectionPaths paths = TreeConnectionPaths.fromProperties(properties);
        String parentKey = parent.getAbilityInstance().getAbility().getKey();
        TreeConnectionPath path = paths.get(parentKey);
        if (!path.isEmpty()) {
            return path;
        }
        String local = TreeEditorStateSync.localAbilityKey(parentKey);
        return local == null || local.equals(parentKey) ? TreeConnectionPath.EMPTY : paths.get(local);
    }

    @Unique
    private static List<Pixel> yha$polyline(int originX, int originY, AbilityElement parent, AbilityElement child) {
        TreeConnectionPath path = yha$pathOf(child, parent);
        int shiftX = yha$layoutShiftX(child);
        int shiftY = yha$layoutShiftY(child);
        List<Pixel> points = new ArrayList<>(path.size() + 2);
        points.add(new Pixel(originX + parent.getX(), originY + parent.getY()));
        for (Vec2 waypoint : path.waypoints()) {
            points.add(new Pixel(
                    originX + TreeConnectionRenderer.palladiumPixel(waypoint.x) + shiftX,
                    originY + TreeConnectionRenderer.palladiumPixel(waypoint.y) + shiftY
            ));
        }
        points.add(new Pixel(originX + child.getX(), originY + child.getY()));
        return points;
    }

    @Unique
    private static int yha$layoutShiftX(AbilityElement element) {
        Vec2 original = element.getAbilityInstance().getAbility().getProperties().getGuiPosition();
        if (original == null) {
            return 0;
        }
        return element.getX() - TreeConnectionRenderer.palladiumPixel(original.x);
    }

    @Unique
    private static int yha$layoutShiftY(AbilityElement element) {
        Vec2 original = element.getAbilityInstance().getAbility().getProperties().getGuiPosition();
        if (original == null) {
            return 0;
        }
        return element.getY() - TreeConnectionRenderer.palladiumPixel(original.y);
    }
}
