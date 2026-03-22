package com.github.bandithelps.values;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;

public class StaminaConstants {

    public static int[] EXHAUSTION_LEVELS = {0, -10, -35, -60, -80};
    public static double[] EXHAUSTION_MULTIPLIERS = {1.0, 1.2, 1.5, 2.0, 3.0};

    public static double[] EXHAUSTION_SLOWNESS_MODIFIERS = {0, -0.15, -0.25, -0.35, -0.45}; // The slowness attribute scaling
    public static double[] EXHAUSTION_WEAKNESS_MODIFIERS = {0, -0.25, -0.50, -0.75, -1};
    public static double[] EXHAUSTION_DIG_SLOW_MODIFIERS = {0, -0.25, -0.50, -0.75, -1};
    public static double[] EXHAUSTION_ATTACK_SLOW_MODIFIERS = {0, -0.25, -0.50, -0.75, -1};
    public static double[] EXHAUSTION_JUMP_MODIFIERS = {0, 0, 0, -0.50, -1};

    public static double[] STAMINA_REGEN_RATE = {1, 0.5, 0.3, 0.2, 0.1};

    // Define the ids for the attribute modifiers for the exhaustion effects
    public static Identifier EXHAUSTION_MOVE_SLOW_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.slow");
    public static Identifier EXHAUSTION_WEAKNESS_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.weakness");
    public static Identifier EXHAUSTION_DIG_SLOW_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.dig");
    public static Identifier EXHAUSTION_ATTACK_SLOW_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.attack");
    public static Identifier EXHAUSTION_JUMP_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.jump");

}
