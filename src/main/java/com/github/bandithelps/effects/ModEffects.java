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
    public static final Holder<MobEffect> DNA_FATIGUE = MOD_EFFECTS.register("dna_fatigue", () -> new DNAFatigueEffect(MobEffectCategory.HARMFUL, 0x9966CC));
    public static final Holder<MobEffect> DECAY = MOD_EFFECTS.register("decay", () -> new DecayEffect(MobEffectCategory.HARMFUL, 0x4B0082));

}
