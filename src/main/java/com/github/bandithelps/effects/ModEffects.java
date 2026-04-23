package com.github.bandithelps.effects;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOD_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, YourHeroAcademia.MODID);
    public static final Holder<MobEffect> SMOKE_BLIND = MOD_EFFECTS.register("smoke_blind", () -> new SmokeBlindEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> SUFFOCATION = MOD_EFFECTS.register("suffocation", () -> new SuffocationEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));

}
