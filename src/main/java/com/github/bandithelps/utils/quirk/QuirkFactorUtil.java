package com.github.bandithelps.utils.quirk;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.attributes.QuirkAttributes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

public final class QuirkFactorUtil {

    private QuirkFactorUtil() {
    }

    public static Identifier sourceId(String sourcePath) {
        return Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, sourcePath);
    }

    public static double getQuirkFactor(Player player) {
        AttributeInstance instance = player.getAttribute(QuirkAttributes.QUIRK_FACTOR);
        if (instance == null) {
            return QuirkAttributes.QUIRK_FACTOR_DEFAULT;
        }

        return instance.getValue();
    }

    public static boolean setBaseQuirkFactor(ServerPlayer player, double baseValue) {
        AttributeInstance instance = player.getAttribute(QuirkAttributes.QUIRK_FACTOR);
        if (instance == null) {
            return false;
        }

        double clampedValue = Mth.clamp(
                baseValue,
                QuirkAttributes.QUIRK_FACTOR_MIN,
                QuirkAttributes.QUIRK_FACTOR_MAX
        );
        instance.setBaseValue(clampedValue);
        return true;
    }

    public static boolean addOrUpdateTransientModifier(ServerPlayer player, Identifier sourceId, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(QuirkAttributes.QUIRK_FACTOR);
        if (instance == null) {
            return false;
        }

        instance.addOrUpdateTransientModifier(new AttributeModifier(sourceId, amount, operation));
        return true;
    }

    public static boolean removeModifier(ServerPlayer player, Identifier sourceId) {
        AttributeInstance instance = player.getAttribute(QuirkAttributes.QUIRK_FACTOR);
        if (instance == null) {
            return false;
        }

        instance.removeModifier(sourceId);
        return true;
    }
}
