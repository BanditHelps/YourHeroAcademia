package com.github.bandithelps.gui.screens;

import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodyPartData;
import com.github.bandithelps.capabilities.body.DamageState;
import com.github.bandithelps.client.body.ClientBodyState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BodyDebugScreen extends Screen {
    private static final List<PartRegion> PART_REGIONS = List.of(
            new PartRegion(BodyPart.HEAD, 44, 6, 32, 26),
            new PartRegion(BodyPart.CHEST, 34, 36, 52, 44),
            new PartRegion(BodyPart.LEFT_ARM, 8, 36, 24, 44),
            new PartRegion(BodyPart.RIGHT_ARM, 88, 36, 24, 44),
            new PartRegion(BodyPart.LEFT_HAND, 8, 82, 24, 18),
            new PartRegion(BodyPart.RIGHT_HAND, 88, 82, 24, 18),
            new PartRegion(BodyPart.LEFT_LEG, 36, 84, 22, 44),
            new PartRegion(BodyPart.RIGHT_LEG, 62, 84, 22, 44),
            new PartRegion(BodyPart.LEFT_FOOT, 34, 130, 24, 16),
            new PartRegion(BodyPart.RIGHT_FOOT, 62, 130, 24, 16),
            new PartRegion(BodyPart.MAIN_ARM, 120, 36, 54, 22),
            new PartRegion(BodyPart.OFF_ARM, 120, 62, 54, 22)
    );

    public BodyDebugScreen() {
        super(Component.literal("YHA Body Debug"));
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(this.width - 74, 8, 64, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int modelX = this.width / 2 - 90;
        int modelY = this.height / 2 - 80;
        graphics.fill(modelX - 8, modelY - 8, modelX + 186, modelY + 158, 0x88222222);

        PartRegion hovered = null;
        for (PartRegion region : PART_REGIONS) {
            BodyPartData data = getPartData(region.part());
            int x1 = modelX + region.x();
            int y1 = modelY + region.y();
            int x2 = x1 + region.width();
            int y2 = y1 + region.height();
            int color = stateColor(data.getDamageState());
            graphics.fill(x1, y1, x2, y2, color);
            graphics.fill(x1, y1, x2, y1 + 1, 0xFF000000);
            graphics.fill(x1, y2 - 1, x2, y2, 0xFF000000);
            graphics.fill(x1, y1, x1 + 1, y2, 0xFF000000);
            graphics.fill(x2 - 1, y1, x2, y2, 0xFF000000);

            float percent = (data.getCurrentHealth() / Math.max(1.0F, data.getMaxHealth()));
            int barWidth = Math.max(0, Math.min(region.width(), Math.round(region.width() * percent)));
            graphics.fill(x1, y2 - 3, x1 + barWidth, y2 - 1, 0xAA00FF66);

            graphics.centeredText(this.font, Component.literal(shortName(region.part())), x1 + region.width() / 2, y1 + 2, 0xFFFFFF);
            if (isMouseInside(mouseX, mouseY, x1, y1, x2, y2)) {
                hovered = region;
            }
        }

        drawLegend(graphics, modelX + 186 + 8, modelY);
        if (hovered != null) {
            drawHoverPanel(graphics, mouseX, mouseY, hovered.part(), getPartData(hovered.part()));
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawLegend(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(this.font, Component.literal("State Colors"), x, y, 0xFFFFFF, false);
        drawLegendRow(graphics, x, y + 14, "Healthy", 0xAA1FA85A);
        drawLegendRow(graphics, x, y + 28, "Sprained", 0xAAC4B72D);
        drawLegendRow(graphics, x, y + 42, "Broken", 0xAAD97C28);
        drawLegendRow(graphics, x, y + 56, "Destroyed", 0xAA9E2F2F);
    }

    private void drawLegendRow(GuiGraphicsExtractor graphics, int x, int y, String label, int color) {
        graphics.fill(x, y + 2, x + 10, y + 12, color);
        graphics.fill(x, y + 2, x + 10, y + 3, 0xFF000000);
        graphics.fill(x, y + 11, x + 10, y + 12, 0xFF000000);
        graphics.fill(x, y + 2, x + 1, y + 12, 0xFF000000);
        graphics.fill(x + 9, y + 2, x + 10, y + 12, 0xFF000000);
        graphics.text(this.font, Component.literal(label), x + 14, y + 2, 0xFFFFFF, false);
    }

    private void drawHoverPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, BodyPart part, BodyPartData data) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(formatPartName(part)));
        float maxHealth = Math.max(1.0F, data.getMaxHealth());
        float percent = (data.getCurrentHealth() / maxHealth) * 100.0F;
        lines.add(Component.literal(String.format("Health: %.1f / %.1f (%.1f%%)", data.getCurrentHealth(), maxHealth, percent)));
        lines.add(Component.literal("State: " + data.getDamageState().name().toLowerCase()));
        lines.add(Component.literal("Prosthetic: " + (data.isProsthetic() ? "yes" : "no")));
        lines.add(Component.literal("Custom Floats:"));
        if (data.getCustomFloats().isEmpty()) {
            lines.add(Component.literal("- none"));
        } else {
            data.getCustomFloats().entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> lines.add(Component.literal("- " + entry.getKey() + ": " + entry.getValue())));
        }
        lines.add(Component.literal("Custom Strings:"));
        if (data.getCustomStrings().isEmpty()) {
            lines.add(Component.literal("- none"));
        } else {
            data.getCustomStrings().entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> lines.add(Component.literal("- " + entry.getKey() + ": " + entry.getValue())));
        }
        List<ClientTooltipComponent> tooltipLines = lines.stream()
                .flatMap(line -> this.font.split(line, 240).stream())
                .map(ClientTooltipComponent::create)
                .collect(Collectors.toList());
        graphics.tooltip(this.font, tooltipLines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, (Identifier) null);
    }

    private BodyPartData getPartData(BodyPart part) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        BodyPart resolved = player == null ? fallbackPart(part) : BodyPart.resolveForPlayer(player, part);
        return ClientBodyState.get(resolved);
    }

    private BodyPart fallbackPart(BodyPart part) {
        if (part == BodyPart.MAIN_ARM) {
            return BodyPart.RIGHT_ARM;
        }
        if (part == BodyPart.OFF_ARM) {
            return BodyPart.LEFT_ARM;
        }
        return part;
    }

    private int stateColor(DamageState state) {
        return switch (state) {
            case HEALTHY -> 0xAA1FA85A;
            case SPRAINED -> 0xAAC4B72D;
            case BROKEN -> 0xAAD97C28;
            case DESTROYED -> 0xAA9E2F2F;
        };
    }

    private boolean isMouseInside(int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    private String shortName(BodyPart part) {
        return switch (part) {
            case HEAD -> "Head";
            case CHEST -> "Chest";
            case LEFT_ARM -> "L Arm";
            case RIGHT_ARM -> "R Arm";
            case LEFT_LEG -> "L Leg";
            case RIGHT_LEG -> "R Leg";
            case LEFT_HAND -> "L Hand";
            case RIGHT_HAND -> "R Hand";
            case LEFT_FOOT -> "L Foot";
            case RIGHT_FOOT -> "R Foot";
            case MAIN_ARM -> "Main Arm";
            case OFF_ARM -> "Off Arm";
        };
    }

    private String formatPartName(BodyPart part) {
        return "Part: " + shortName(part);
    }

    private record PartRegion(BodyPart part, int x, int y, int width, int height) {
    }
}
