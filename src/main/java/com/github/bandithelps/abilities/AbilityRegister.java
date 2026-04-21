package com.github.bandithelps.abilities;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blockdisplayanims.BDBodyPartEmitterAbility;
import com.github.bandithelps.abilities.blockdisplayanims.BDDomeAbility;
import com.github.bandithelps.abilities.blockdisplayanims.BDShockwaveAbility;
import com.github.bandithelps.abilities.bodydata.DisplayBodyBarAbility;
import com.github.bandithelps.abilities.bodydata.BodyPartValueTickAbility;
import com.github.bandithelps.abilities.bodydata.DamageBodyPartAbility;
import com.github.bandithelps.abilities.bodydata.HealBodyPartAbility;
import com.github.bandithelps.abilities.common.PlaySoundAbility;
import com.github.bandithelps.abilities.common.PotionGeneratorAbility;
import com.github.bandithelps.abilities.common.SprayAttackAbility;
import com.github.bandithelps.abilities.movement.DashAbility;
import com.github.bandithelps.abilities.movement.MultiJumpAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class AbilityRegister {

    public static final DeferredRegister<AbilitySerializer<?>> ABILITIES;
    public static final DeferredHolder<AbilitySerializer<?>, DashAbility.Serializer> DASH;
    public static final DeferredHolder<AbilitySerializer<?>, MultiJumpAbility.Serializer> MULTI_JUMP;
    public static final DeferredHolder<AbilitySerializer<?>, DamageBodyPartAbility.Serializer> DAMAGE_BODY_PART;
    public static final DeferredHolder<AbilitySerializer<?>, HealBodyPartAbility.Serializer> HEAL_BODY_PART;
    public static final DeferredHolder<AbilitySerializer<?>, BodyPartValueTickAbility.Serializer> CHANGE_BODY_VALUE;
    public static final DeferredHolder<AbilitySerializer<?>, DisplayBodyBarAbility.Serializer> DISPLAY_BODY_BAR;
    public static final DeferredHolder<AbilitySerializer<?>, BDShockwaveAbility.Serializer> BD_SHOCKWAVE;
    public static final DeferredHolder<AbilitySerializer<?>, BDDomeAbility.Serializer> BD_DOME;
    public static final DeferredHolder<AbilitySerializer<?>, BDBodyPartEmitterAbility.Serializer> BD_BODY_PART_EMITTER;
    public static final DeferredHolder<AbilitySerializer<?>, PotionGeneratorAbility.Serializer> POTION_GEN;
    public static final DeferredHolder<AbilitySerializer<?>, SprayAttackAbility.Serializer> SPRAY_ATTACK;
    public static final DeferredHolder<AbilitySerializer<?>, PlaySoundAbility.Serializer> PLAY_SOUND;

    static {
        ABILITIES = DeferredRegister.create(PalladiumRegistryKeys.ABILITY_SERIALIZER, YourHeroAcademia.MODID);
        DASH = ABILITIES.register("dash", DashAbility.Serializer::new);
        MULTI_JUMP = ABILITIES.register("multi_jump", MultiJumpAbility.Serializer::new);
        DAMAGE_BODY_PART = ABILITIES.register("damage_body_part", DamageBodyPartAbility.Serializer::new);
        HEAL_BODY_PART = ABILITIES.register("heal_body_part", HealBodyPartAbility.Serializer::new);
        CHANGE_BODY_VALUE = ABILITIES.register("change_body_value", BodyPartValueTickAbility.Serializer::new);
        DISPLAY_BODY_BAR = ABILITIES.register("display_body_bar", DisplayBodyBarAbility.Serializer::new);
        BD_SHOCKWAVE = ABILITIES.register("bd_shockwave", BDShockwaveAbility.Serializer::new);
        BD_DOME = ABILITIES.register("bd_dome", BDDomeAbility.Serializer::new);
        BD_BODY_PART_EMITTER = ABILITIES.register("bd_body_part_emitter", BDBodyPartEmitterAbility.Serializer::new);
        POTION_GEN = ABILITIES.register("potion_gen", PotionGeneratorAbility.Serializer::new);
        SPRAY_ATTACK = ABILITIES.register("spray_attack", SprayAttackAbility.Serializer::new);
        PLAY_SOUND = ABILITIES.register("play_sound", PlaySoundAbility.Serializer::new);
    }

}
