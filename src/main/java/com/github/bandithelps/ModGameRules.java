package com.github.bandithelps;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModGameRules {
    public static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, YourHeroAcademia.MODID);

    /**
     * Master switch for explosive throwables breaking blocks. When false, no throwable
     * destroys terrain even if the item itself enables world damage.
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> THROWABLE_BLOCK_DAMAGE = GAME_RULES.register(
            "throwable_block_damage",
            () -> new GameRule<>(
                    GameRuleCategory.MISC,
                    GameRuleType.BOOL,
                    BoolArgumentType.bool(),
                    GameRuleTypeVisitor::visitBoolean,
                    Codec.BOOL,
                    value -> value ? 1 : 0,
                    true,
                    FeatureFlagSet.of()
            )
    );

    private ModGameRules() {
    }

    public static boolean throwableBlockDamage(ServerLevel level) {
        return level.getGameRules().get(THROWABLE_BLOCK_DAMAGE.get());
    }
}
