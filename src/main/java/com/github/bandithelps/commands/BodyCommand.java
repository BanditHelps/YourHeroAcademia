package com.github.bandithelps.commands;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyDisplayBar;
import com.github.bandithelps.capabilities.body.BodyDisplayBarType;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodyPartData;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.network.OpenBodyDebugScreenPayload;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registers the "body" commands to the /yha super command. Used AI to write this entire thing cause I couldn't be bothered
 * to add all of these conditions myself.
 */
public final class BodyCommand {
    private static final DynamicCommandExceptionType INVALID_PART_EXCEPTION = new DynamicCommandExceptionType(value ->
            Component.literal("Unknown body part '" + value + "'. Valid parts: " + validPartsCsv())
    );
    private static final DynamicCommandExceptionType INVALID_DISPLAY_BAR_TYPE_EXCEPTION = new DynamicCommandExceptionType(value ->
            Component.literal("Unknown display bar type '" + value + "'. Valid types: " + validDisplayBarTypesCsv())
    );
    private static final DynamicCommandExceptionType INVALID_HEX_COLOR_EXCEPTION = new DynamicCommandExceptionType(value ->
            Component.literal("Invalid hex color '" + value + "'. Use #RRGGBB, RRGGBB, or 0xRRGGBB.")
    );
    private static final SimpleCommandExceptionType INVALID_BAR_RANGE_EXCEPTION = new SimpleCommandExceptionType(
            Component.literal("Display bar max must be greater than min.")
    );
    private static final SuggestionProvider<CommandSourceStack> BODY_PART_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(BodyPart.values()).map(BodyPart::getId), builder);
    private static final SuggestionProvider<CommandSourceStack> DISPLAY_BAR_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(BodyDisplayBarType.values()).map(BodyDisplayBarType::getId), builder);

    private BodyCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> body = Commands.literal("body");
        body.then(Commands.literal("debug")
                .executes(c -> openDebugScreen(c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> openDebugScreen(c.getSource(), EntityArgument.getPlayer(c, "player")))));

        body.then(Commands.literal("damage")
                .then(bodyPartArgument("part")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                .executes(c -> damagePart(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "amount")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> damagePart(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "amount")))))));

        body.then(Commands.literal("heal")
                .then(bodyPartArgument("part")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                .executes(c -> healPart(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "amount")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> healPart(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "amount")))))));

        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        set.then(Commands.literal("health")
                .then(bodyPartArgument("part")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F))
                                .executes(c -> setHealth(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "value")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setHealth(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "value")))))));
        set.then(Commands.literal("baseMaxHealth")
                .then(bodyPartArgument("part")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(1.0F))
                                .executes(c -> setBaseMaxHealth(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "value")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setBaseMaxHealth(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "value")))))));
        set.then(Commands.literal("maxHealthModifier")
                .then(bodyPartArgument("part")
                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                .executes(c -> setMaxHealthModifier(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "value")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setMaxHealthModifier(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), FloatArgumentType.getFloat(c, "value")))))));
        set.then(Commands.literal("prosthetic")
                .then(bodyPartArgument("part")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(c -> setProsthetic(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), BoolArgumentType.getBool(c, "enabled")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setProsthetic(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), BoolArgumentType.getBool(c, "enabled")))))));
        set.then(Commands.literal("float")
                .then(bodyPartArgument("part")
                        .then(customFloatKeyArgument("part")
                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                        .executes(c -> setCustomFloat(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key"), FloatArgumentType.getFloat(c, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> setCustomFloat(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key"), FloatArgumentType.getFloat(c, "value"))))))));
        set.then(Commands.literal("string")
                .then(bodyPartArgument("part")
                        .then(customStringKeyArgument("part")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(c -> setCustomString(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> setCustomString(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value"))))))));
        body.then(set);

        LiteralArgumentBuilder<CommandSourceStack> remove = Commands.literal("remove");
        remove.then(Commands.literal("float")
                .then(bodyPartArgument("part")
                        .then(customFloatKeyArgument("part")
                                .executes(c -> removeCustomFloat(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> removeCustomFloat(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key")))))));
        remove.then(Commands.literal("string")
                .then(bodyPartArgument("part")
                        .then(customStringKeyArgument("part")
                                .executes(c -> removeCustomString(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> removeCustomString(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"), StringArgumentType.getString(c, "key")))))));
        body.then(remove);

        body.then(buildDisplayBarCommand());

        body.then(Commands.literal("view")
                .then(bodyPartArgument("part")
                        .executes(c -> viewPart(c.getSource(), c.getSource().getPlayerOrException(), parseBodyPart(c, "part")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> viewPart(c.getSource(), EntityArgument.getPlayer(c, "player"), parseBodyPart(c, "part"))))));
        body.then(Commands.literal("parts").executes(c -> listParts(c.getSource())));

        builder.then(body);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDisplayBarCommand() {
        LiteralArgumentBuilder<CommandSourceStack> displayBars = Commands.literal("displaybar");

        var colorArgument = Commands.argument("color", StringArgumentType.word())
                .executes(c -> addDisplayBar(
                        c.getSource(),
                        c.getSource().getPlayerOrException(),
                        StringArgumentType.getString(c, "id"),
                        StringArgumentType.getString(c, "label"),
                        parseBodyPart(c, "part"),
                        StringArgumentType.getString(c, "key"),
                        FloatArgumentType.getFloat(c, "min"),
                        FloatArgumentType.getFloat(c, "max"),
                        StringArgumentType.getString(c, "color"),
                        BodyDisplayBarType.FILL
                ))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> addDisplayBar(
                                c.getSource(),
                                EntityArgument.getPlayer(c, "player"),
                                StringArgumentType.getString(c, "id"),
                                StringArgumentType.getString(c, "label"),
                                parseBodyPart(c, "part"),
                                StringArgumentType.getString(c, "key"),
                                FloatArgumentType.getFloat(c, "min"),
                                FloatArgumentType.getFloat(c, "max"),
                                StringArgumentType.getString(c, "color"),
                                BodyDisplayBarType.FILL
                        )))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(DISPLAY_BAR_TYPE_SUGGESTIONS)
                        .executes(c -> addDisplayBar(
                                c.getSource(),
                                c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "id"),
                                StringArgumentType.getString(c, "label"),
                                parseBodyPart(c, "part"),
                                StringArgumentType.getString(c, "key"),
                                FloatArgumentType.getFloat(c, "min"),
                                FloatArgumentType.getFloat(c, "max"),
                                StringArgumentType.getString(c, "color"),
                                parseDisplayBarType(c, "type")
                        ))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> addDisplayBar(
                                        c.getSource(),
                                        EntityArgument.getPlayer(c, "player"),
                                        StringArgumentType.getString(c, "id"),
                                        StringArgumentType.getString(c, "label"),
                                        parseBodyPart(c, "part"),
                                        StringArgumentType.getString(c, "key"),
                                        FloatArgumentType.getFloat(c, "min"),
                                        FloatArgumentType.getFloat(c, "max"),
                                        StringArgumentType.getString(c, "color"),
                                        parseDisplayBarType(c, "type")
                                ))));

        displayBars.then(Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("label", StringArgumentType.string())
                                .then(bodyPartArgument("part")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("min", FloatArgumentType.floatArg())
                                                        .then(Commands.argument("max", FloatArgumentType.floatArg())
                                                                .then(colorArgument))))))));

        displayBars.then(Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(c -> removeDisplayBar(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> removeDisplayBar(c.getSource(), EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "id"))))));

        displayBars.then(Commands.literal("list")
                .executes(c -> listDisplayBars(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> listDisplayBars(c.getSource(), EntityArgument.getPlayer(c, "player")))));

        return displayBars;
    }

    private static int openDebugScreen(CommandSourceStack source) throws CommandSyntaxException {
        return openDebugScreen(source, source.getPlayerOrException());
    }

    private static int openDebugScreen(CommandSourceStack source, ServerPlayer player) {
        BodySyncEvents.syncNow(player);
        PacketDistributor.sendToPlayer(player, OpenBodyDebugScreenPayload.INSTANCE);
        source.sendSuccess(() -> Component.literal("Opened body debug screen for " + player.getName().getString() + "."), false);
        return 1;
    }

    private static int damagePart(CommandSourceStack source, ServerPlayer player, BodyPart part, float amount) {
        BodyAttachments.get(player).damagePart(player, part, amount);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Damaged " + part.getId() + " by " + amount + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int healPart(CommandSourceStack source, ServerPlayer player, BodyPart part, float amount) {
        BodyAttachments.get(player).healPart(player, part, amount);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Healed " + part.getId() + " by " + amount + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setHealth(CommandSourceStack source, ServerPlayer player, BodyPart part, float value) {
        BodyAttachments.get(player).setHealth(player, part, value);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set health of " + part.getId() + " to " + value + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setBaseMaxHealth(CommandSourceStack source, ServerPlayer player, BodyPart part, float value) {
        BodyAttachments.get(player).setBaseMaxHealth(player, part, value);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set baseMaxHealth of " + part.getId() + " to " + value + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setMaxHealthModifier(CommandSourceStack source, ServerPlayer player, BodyPart part, float value) {
        BodyAttachments.get(player).setMaxHealthModifier(player, part, value);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set maxHealthModifier of " + part.getId() + " to " + value + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setProsthetic(CommandSourceStack source, ServerPlayer player, BodyPart part, boolean enabled) {
        BodyAttachments.get(player).setProsthetic(player, part, enabled);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set prosthetic of " + part.getId() + " to " + enabled + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setCustomFloat(CommandSourceStack source, ServerPlayer player, BodyPart part, String key, float value) {
        BodyAttachments.get(player).setCustomFloat(player, part, key, value);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set float key '" + key + "' on " + part.getId() + " to " + value + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int removeCustomFloat(CommandSourceStack source, ServerPlayer player, BodyPart part, String key) {
        BodyAttachments.get(player).removeCustomFloat(player, part, key);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Removed float key '" + key + "' from " + part.getId() + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setCustomString(CommandSourceStack source, ServerPlayer player, BodyPart part, String key, String value) {
        BodyAttachments.get(player).setCustomString(player, part, key, value);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set string key '" + key + "' on " + part.getId() + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int removeCustomString(CommandSourceStack source, ServerPlayer player, BodyPart part, String key) {
        BodyAttachments.get(player).removeCustomString(player, part, key);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Removed string key '" + key + "' from " + part.getId() + " for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int addDisplayBar(
            CommandSourceStack source,
            ServerPlayer player,
            String id,
            String label,
            BodyPart part,
            String key,
            float min,
            float max,
            String colorHex,
            BodyDisplayBarType type
    ) throws CommandSyntaxException {
        if (max <= min) {
            throw INVALID_BAR_RANGE_EXCEPTION.create();
        }

        int color = parseHexColor(colorHex);
        BodyDisplayBar displayBar = new BodyDisplayBar(id, label, part, key, min, max, color, type);
        BodyAttachments.get(player).setDisplayBar(displayBar);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal(
                "Set display bar '" + displayBar.id() + "' for " + player.getName().getString()
                        + " (part=" + displayBar.part().getId()
                        + ", key=" + displayBar.key()
                        + ", min=" + displayBar.minValue()
                        + ", max=" + displayBar.maxValue()
                        + ", color=#" + String.format("%06X", displayBar.colorRgb())
                        + ", type=" + displayBar.type().getId() + ")."
        ), true);
        return 1;
    }

    private static int removeDisplayBar(CommandSourceStack source, ServerPlayer player, String id) {
        BodyAttachments.get(player).removeDisplayBar(id);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Removed display bar '" + id + "' for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int listDisplayBars(CommandSourceStack source, ServerPlayer player) {
        Map<String, BodyDisplayBar> displayBars = BodyAttachments.get(player).getDisplayBarsView();
        if (displayBars.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No display bars set for " + player.getName().getString() + "."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Display bars for " + player.getName().getString() + ":"), false);
        for (BodyDisplayBar displayBar : displayBars.values()) {
            source.sendSuccess(() -> Component.literal(
                    "- " + displayBar.id()
                            + " | label=\"" + displayBar.label() + "\""
                            + " | part=" + displayBar.part().getId()
                            + " | key=" + displayBar.key()
                            + " | min=" + displayBar.minValue()
                            + " | max=" + displayBar.maxValue()
                            + " | color=#" + String.format("%06X", displayBar.colorRgb())
                            + " | type=" + displayBar.type().getId()
            ), false);
        }
        return 1;
    }

    private static int viewPart(CommandSourceStack source, ServerPlayer player, BodyPart part) {
        BodyPartData data = BodyAttachments.get(player).getPartData(player, part);
        float maxHealth = Math.max(1.0F, data.getMaxHealth());
        float percent = (data.getCurrentHealth() / maxHealth) * 100.0F;
        source.sendSuccess(() -> Component.literal(
                "Body part " + part.getId() + " for " + player.getName().getString()
                        + " | health " + String.format("%.2f", data.getCurrentHealth()) + "/" + String.format("%.2f", maxHealth)
                        + " (" + String.format("%.2f", percent) + "%)"
                        + " | state " + data.getDamageState().name().toLowerCase()
                        + " | prosthetic " + data.isProsthetic()
        ), false);

        String floatFields = formatFieldMap(data.getCustomFloats());
        String stringFields = formatFieldMap(data.getCustomStrings());
        source.sendSuccess(() -> Component.literal("Custom floats: " + floatFields), false);
        source.sendSuccess(() -> Component.literal("Custom strings: " + stringFields), false);
        return 1;
    }

    private static int listParts(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Valid body parts: " + validPartsCsv()), false);
        return 1;
    }

    private static String validPartsCsv() {
        return java.util.Arrays.stream(BodyPart.values())
                .map(BodyPart::getId)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static BodyPart parseBodyPart(CommandContext<CommandSourceStack> context, String argumentName) throws CommandSyntaxException {
        String rawValue = StringArgumentType.getString(context, argumentName);
        BodyPart part = BodyPart.fromId(rawValue);
        if (part == null) {
            throw INVALID_PART_EXCEPTION.create(rawValue);
        }
        return part;
    }

    private static BodyDisplayBarType parseDisplayBarType(CommandContext<CommandSourceStack> context, String argumentName) throws CommandSyntaxException {
        String rawValue = StringArgumentType.getString(context, argumentName);
        BodyDisplayBarType type = BodyDisplayBarType.fromId(rawValue);
        if (type == null) {
            throw INVALID_DISPLAY_BAR_TYPE_EXCEPTION.create(rawValue);
        }
        return type;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> bodyPartArgument(String name) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests(BODY_PART_SUGGESTIONS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> customFloatKeyArgument(String bodyPartArgumentName) {
        return Commands.argument("key", StringArgumentType.word())
                .suggests((context, builder) -> suggestCustomKeys(context, builder, bodyPartArgumentName, true));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> customStringKeyArgument(String bodyPartArgumentName) {
        return Commands.argument("key", StringArgumentType.word())
                .suggests((context, builder) -> suggestCustomKeys(context, builder, bodyPartArgumentName, false));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCustomKeys(
            CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            String bodyPartArgumentName,
            boolean floatKeys
    ) {
        ServerPlayer player = getSuggestionTargetPlayer(context);
        if (player == null) {
            return builder.buildFuture();
        }

        BodyPart part;
        try {
            part = parseBodyPart(context, bodyPartArgumentName);
        } catch (CommandSyntaxException exception) {
            return builder.buildFuture();
        }

        BodyPartData data = BodyAttachments.get(player).getPartData(player, part);
        return SharedSuggestionProvider.suggest(floatKeys ? data.getCustomFloats().keySet() : data.getCustomStrings().keySet(), builder);
    }

    private static ServerPlayer getSuggestionTargetPlayer(CommandContext<CommandSourceStack> context) {
        try {
            return context.getArgument("player", ServerPlayer.class);
        } catch (IllegalArgumentException ignored) {
            try {
                return context.getSource().getPlayerOrException();
            } catch (CommandSyntaxException exception) {
                return null;
            }
        }
    }

    private static <T> String formatFieldMap(Map<String, T> values) {
        if (values.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, T>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        return entries.stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static int parseHexColor(String colorHex) throws CommandSyntaxException {
        String normalized = colorHex.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() != 6 || !normalized.matches("[0-9A-Fa-f]{6}")) {
            throw INVALID_HEX_COLOR_EXCEPTION.create(colorHex);
        }
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ex) {
            throw INVALID_HEX_COLOR_EXCEPTION.create(colorHex);
        }
    }

    private static String validDisplayBarTypesCsv() {
        return Arrays.stream(BodyDisplayBarType.values())
                .map(BodyDisplayBarType::getId)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
