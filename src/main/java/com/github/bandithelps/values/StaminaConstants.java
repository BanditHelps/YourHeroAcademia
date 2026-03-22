package com.github.bandithelps.values;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;

public class StaminaConstants {

    public static int[] EXHAUSTION_LEVELS = {0, -10, -35, -60, -80};
    public static double[] EXHAUSTION_MULTIPLIERS = {1.0, 1.2, 1.5, 2.0, 3.0};
    public static float[] EXHAUSTION_DAMAGE_LEVELS = {0f, 0f, 3.0f, 6.0f, 20.0f};

    public static int EXHAUSTION_DEATH_LEVEL = 4; // The level the player will die if they attempt to surpass
    public static float STAMINA_GAIN_PERCENTAGE = 0.33f; // The percent that the stamina must be below to "gain" upgrade progress
    public static int UPGRADE_POINT_COST = 500; // The amount of "progress" required to redeem an upgrade point
    public static int UPGRADE_PROGRESS_COOLDOWN = 60; // Cooldown in stamina ticks (1 tick = 1 second)
    public static double STAMINA_GAIN_CHANCE = 0.05; // Chance to gain an extra max stamina point
    public static double STAMINA_GAIN_EXHAUSTED_CHANCE = 0.1; // Chance to gain an extra max stamina point when exhausted
    // How much stamina needs to be used in order to have a chance at increasing the max value
    public static int STAMINA_GAIN_REQ = 100;
    // The maximum amount that the stamina can increase after using STAMINA_GAIN_REQ total stamina
    public static int STAMINA_MAX_INCREASE = 3;

    public static double[] EXHAUSTION_SLOWNESS_MODIFIERS = {0, -0.15, -0.25, -0.35, -0.45}; // The slowness attribute scaling
    public static double[] EXHAUSTION_WEAKNESS_MODIFIERS = {0, -0.25, -0.50, -0.75, -1};
    public static double[] EXHAUSTION_DIG_SLOW_MODIFIERS = {0, -0.25, -0.50, -0.75, -1};
    public static double[] EXHAUSTION_ATTACK_SLOW_MODIFIERS = {0, -0.25, -0.50, -0.75, -1};
    public static double[] EXHAUSTION_JUMP_MODIFIERS = {0, 0, 0, -0.50, -1};

    public static double[] STAMINA_REGEN_RATE = {1, 0.5, 0.3, 0.2, 0.1};
    public static int[] STAMINA_REGEN_COOLDOWNS = {3, 6, 8, 9, 10}; // How long before stamina starts regenerating


    // Define the ids for the attribute modifiers for the exhaustion effects
    public static Identifier EXHAUSTION_MOVE_SLOW_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.slow");
    public static Identifier EXHAUSTION_WEAKNESS_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.weakness");
    public static Identifier EXHAUSTION_DIG_SLOW_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.dig");
    public static Identifier EXHAUSTION_ATTACK_SLOW_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.attack");
    public static Identifier EXHAUSTION_JUMP_ATTRIBUTE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion.jump");

}
