package com.github.bandithelps.client.loadout;

import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityBar;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityBarAlignment;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityListComponent;
import net.threetag.palladium.client.renderer.icon.IconRenderer;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.power.ability.AbilityColor;
import net.threetag.palladium.power.ability.AbilityInstance;

/**
 * Slides extra same-index mode icons out beside Palladium's ability bar, using the same slot art.
 */
public final class AbilityModeOverlay {
    private static final int SLOT_INNER = 18;
    private static final int SLOT_STEP = 22;
    private static final int SLOT_PAD = 3;
    private static final float SLIDE_SPEED = 0.18F;

    private static float progress;

    private AbilityModeOverlay() {
    }

    public static void render(
            AbilityListComponent component,
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            int barX,
            int barY,
            AbilityBarAlignment alignment
    ) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float target = AbilityLoadoutKeys.isModeSelectDown() && minecraft.screen == null ? 1.0F : 0.0F;
        progress = Mth.lerp(SLIDE_SPEED, progress, target);
        if (progress < 0.02F) {
            progress = target == 0.0F ? 0.0F : progress;
        }
        if (progress <= 0.02F) {
            return;
        }

        AbilityBar.AbilityList list = component.abilityList();
        Identifier texture = list.getTexture(DataContext.forPower(player, list.getPowerInstance()));
        float eased = progress * progress * (3.0F - 2.0F * progress);
        int direction = alignment.isLeft() ? 1 : -1;

        for (int slot = 0; slot < AbilityLoadoutData.SLOT_COUNT; slot++) {
            AbilityInstance<?> current = list.getAbility(slot);
            if (current == null || !current.isUnlocked()) {
                continue;
            }
            List<AbilityInstance<?>> extras = extrasFor(player, current);
            if (extras.isEmpty()) {
                continue;
            }

            int slotX = barX + SLOT_PAD;
            int slotY = barY + SLOT_PAD + (slot * SLOT_STEP);
            for (int i = 0; i < extras.size(); i++) {
                int destOffset = SLOT_STEP * (i + 1);
                int drawX = slotX + Math.round(direction * destOffset * eased);
                renderSlot(minecraft, gui, texture, extras.get(i), drawX, slotY);
            }
        }
    }

    private static List<AbilityInstance<?>> extrasFor(LocalPlayer player, AbilityInstance<?> current) {
        List<AbilityInstance<?>> extras = new ArrayList<>();
        String currentKey = current.getAbility().getKey();
        for (AbilityInstance<?> mode : AbilityLoadoutUtil.collectModes(player, current)) {
            if (!currentKey.equals(mode.getAbility().getKey())) {
                extras.add(mode);
            }
        }
        return extras;
    }

    private static void renderSlot(
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            Identifier texture,
            AbilityInstance<?> instance,
            int x,
            int y
    ) {
        gui.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                24.0F,
                56.0F,
                SLOT_INNER,
                SLOT_INNER,
                256,
                256
        );
        IconRenderer.drawIcon(
                instance.getAbility().getProperties().getIcon(),
                minecraft,
                gui,
                DataContext.forAbility(minecraft.player, instance),
                x + 1,
                y + 1
        );
        AbilityColor color = instance.getAbility().getProperties().getColor();
        gui.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x - SLOT_PAD,
                y - SLOT_PAD,
                color.getU(),
                color.getV(),
                24,
                24,
                256,
                256
        );
    }
}
