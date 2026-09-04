package com.github.bandithelps.mixin;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.creation.CreationQuickSlotAbility;
import com.github.bandithelps.client.creation.ClientCreationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.threetag.palladium.client.renderer.icon.IconRenderer;
import net.threetag.palladium.icon.Icon;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.power.ability.AbilityInstance;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IconRenderer.class)
public interface IconRendererMixin {
    @Inject(
            method = "drawIcon(Lnet/threetag/palladium/icon/Icon;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/threetag/palladium/logic/context/DataContext;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void yha$drawCreationQuickSlotItem(
            Icon icon,
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            DataContext context,
            int x,
            int y,
            CallbackInfo ci
    ) {
        if (drawAssigned(minecraft, gui, context, x, y, 16, 16)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "drawIcon(Lnet/threetag/palladium/icon/Icon;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/threetag/palladium/logic/context/DataContext;IIII)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void yha$drawCreationQuickSlotItemSized(
            Icon icon,
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            DataContext context,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo ci
    ) {
        if (drawAssigned(minecraft, gui, context, x, y, width, height)) {
            ci.cancel();
        }
    }

    private static boolean drawAssigned(
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            DataContext context,
            int x,
            int y,
            int width,
            int height
    ) {
        if (context == null || minecraft == null || gui == null) {
            return false;
        }
        AbilityInstance<?> instance = context.getAbility();
        if (instance == null || !(instance.getAbility() instanceof CreationQuickSlotAbility ability)) {
            return false;
        }
        ItemStack stack = ClientCreationState.quickSlotStack(
                ability.slot(),
                minecraft.level == null ? null : minecraft.level.registryAccess()
        );
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        float scaleX = width <= 0 ? 1.0f : width / 16.0f;
        float scaleY = height <= 0 ? 1.0f : height / 16.0f;
        Matrix3x2fStack pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scaleX, scaleY);
        gui.item(stack, 0, 0);
        gui.blit(
                RenderPipelines.GUI_TEXTURED,
                Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/icons/creation/lipid_corner.png"),
                0,
                0,
                0.0F,
                0.0F,
                16,
                16,
                16,
                16
        );
        pose.popMatrix();
        return true;
    }
}
