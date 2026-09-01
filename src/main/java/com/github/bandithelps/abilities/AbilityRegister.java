package com.github.bandithelps.abilities;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blockdisplayanims.BDBodyPartEmitterAbility;
import com.github.bandithelps.abilities.blockdisplayanims.BDDomeAbility;
import com.github.bandithelps.abilities.blockdisplayanims.BDShockwaveAbility;
import com.github.bandithelps.abilities.blockdisplayanims.BDTrailAbility;
import com.github.bandithelps.abilities.bodydata.*;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipBodyReinforceAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipChargeZipAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipDetachAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipMagnetAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipBlockTossAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipDisarmAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipMoveTaggedAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipRestrictAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipSwingAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipTagAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipZipAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipLimbReinforceAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipWebSwingAbility;
import com.github.bandithelps.abilities.decay.DecayFistAbility;
import com.github.bandithelps.abilities.decay.DecayInstabilityAbility;
import com.github.bandithelps.abilities.decay.EnvironmentDecayAbility;
import com.github.bandithelps.abilities.decay.RotAbility;
import com.github.bandithelps.abilities.common.PlaySoundAbility;
import com.github.bandithelps.abilities.common.PotionGeneratorAbility;
import com.github.bandithelps.abilities.common.SmokeCanisterChargeAbility;
import com.github.bandithelps.abilities.common.SprayAttackAbility;
import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import com.github.bandithelps.abilities.movement.DashAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class AbilityRegister {

    public static final DeferredRegister<AbilitySerializer<?>> ABILITIES;

    /*=========================
     |    BODY DATA           |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, DamageBodyPartAbility.Serializer> DAMAGE_BODY_PART;
    public static final DeferredHolder<AbilitySerializer<?>, HealBodyPartAbility.Serializer> HEAL_BODY_PART;
    public static final DeferredHolder<AbilitySerializer<?>, BodyPartValueTickAbility.Serializer> CHANGE_BODY_VALUE;
    public static final DeferredHolder<AbilitySerializer<?>, DisplayBodyBarAbility.Serializer> DISPLAY_BODY_BAR;
    public static final DeferredHolder<AbilitySerializer<?>, SetBodyStringAbility.Serializer> SET_BODY_STRING;
    public static final DeferredHolder<AbilitySerializer<?>, SetBodyFloatAbility.Serializer> SET_BODY_FLOAT;

    /*=========================
     |    Common              |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, DashAbility.Serializer> DASH;
    public static final DeferredHolder<AbilitySerializer<?>, PotionGeneratorAbility.Serializer> POTION_GEN;
    public static final DeferredHolder<AbilitySerializer<?>, SprayAttackAbility.Serializer> SPRAY_ATTACK;
    public static final DeferredHolder<AbilitySerializer<?>, PlaySoundAbility.Serializer> PLAY_SOUND;

    /*=========================
     |    Decay               |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, DecayFistAbility.Serializer> DECAY_FIST;
    public static final DeferredHolder<AbilitySerializer<?>, EnvironmentDecayAbility.Serializer> ENVIRONMENT_DECAY;
    public static final DeferredHolder<AbilitySerializer<?>, RotAbility.Serializer> ROT_WAVE;
    public static final DeferredHolder<AbilitySerializer<?>, DecayInstabilityAbility.Serializer> DECAY_INSTABILITY;

    /*=========================
     |    Blackwhip           |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipTagAbility.Serializer> BLACKWHIP_TAG;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipDisarmAbility.Serializer> BLACKWHIP_DISARM;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipDetachAbility.Serializer> BLACKWHIP_DETACH;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipRestrictAbility.Serializer> BLACKWHIP_RESTRICT;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipMoveTaggedAbility.Serializer> BLACKWHIP_MOVE_TAGGED;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipSwingAbility.Serializer> BLACKWHIP_SWING;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipWebSwingAbility.Serializer> BLACKWHIP_WEB_SWING;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipZipAbility.Serializer> BLACKWHIP_ZIP;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipChargeZipAbility.Serializer> BLACKWHIP_CHARGE_ZIP;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipMagnetAbility.Serializer> BLACKWHIP_MAGNET;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipBlockTossAbility.Serializer> BLACKWHIP_BLOCK_TOSS;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipLimbReinforceAbility.Serializer> BLACKWHIP_LIMB_REINFORCE;
    public static final DeferredHolder<AbilitySerializer<?>, BlackwhipBodyReinforceAbility.Serializer> BLACKWHIP_BODY_REINFORCE;

    /*=========================
     |    Float               |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, FloatAbility.Serializer> FLOAT;

    /*=========================
     |    Smokescreen         |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, SmokeCanisterChargeAbility.Serializer> SMOKE_CANISTER_CHARGE;

    /*=========================
     |    Block Displays      |
     =========================*/
    public static final DeferredHolder<AbilitySerializer<?>, BDShockwaveAbility.Serializer> BD_SHOCKWAVE;
    public static final DeferredHolder<AbilitySerializer<?>, BDDomeAbility.Serializer> BD_DOME;
    public static final DeferredHolder<AbilitySerializer<?>, BDBodyPartEmitterAbility.Serializer> BD_BODY_PART_EMITTER;
    public static final DeferredHolder<AbilitySerializer<?>, BDTrailAbility.Serializer> BD_TRAIL;

    static {
        ABILITIES = DeferredRegister.create(PalladiumRegistryKeys.ABILITY_SERIALIZER, YourHeroAcademia.MODID);
        DASH = ABILITIES.register("dash", DashAbility.Serializer::new);
        DAMAGE_BODY_PART = ABILITIES.register("damage_body_part", DamageBodyPartAbility.Serializer::new);
        HEAL_BODY_PART = ABILITIES.register("heal_body_part", HealBodyPartAbility.Serializer::new);
        CHANGE_BODY_VALUE = ABILITIES.register("change_body_value", BodyPartValueTickAbility.Serializer::new);
        DISPLAY_BODY_BAR = ABILITIES.register("display_body_bar", DisplayBodyBarAbility.Serializer::new);
        BD_SHOCKWAVE = ABILITIES.register("bd_shockwave", BDShockwaveAbility.Serializer::new);
        BD_DOME = ABILITIES.register("bd_dome", BDDomeAbility.Serializer::new);
        BD_BODY_PART_EMITTER = ABILITIES.register("bd_body_part_emitter", BDBodyPartEmitterAbility.Serializer::new);
        BD_TRAIL = ABILITIES.register("bd_trail", BDTrailAbility.Serializer::new);
        POTION_GEN = ABILITIES.register("potion_gen", PotionGeneratorAbility.Serializer::new);
        SPRAY_ATTACK = ABILITIES.register("spray_attack", SprayAttackAbility.Serializer::new);
        PLAY_SOUND = ABILITIES.register("play_sound", PlaySoundAbility.Serializer::new);
        SMOKE_CANISTER_CHARGE = ABILITIES.register("smoke_canister_charge", SmokeCanisterChargeAbility.Serializer::new);
        SET_BODY_STRING = ABILITIES.register("set_body_string", SetBodyStringAbility.Serializer::new);
        SET_BODY_FLOAT = ABILITIES.register("set_body_float", SetBodyFloatAbility.Serializer::new);
        DECAY_FIST = ABILITIES.register("decay_fist", DecayFistAbility.Serializer::new);
        ENVIRONMENT_DECAY = ABILITIES.register("environment_decay", EnvironmentDecayAbility.Serializer::new);
        ROT_WAVE = ABILITIES.register("rot_wave", RotAbility.Serializer::new);
        DECAY_INSTABILITY = ABILITIES.register("decay_instability", DecayInstabilityAbility.Serializer::new);
        BLACKWHIP_TAG = ABILITIES.register("blackwhip_tag", BlackwhipTagAbility.Serializer::new);
        BLACKWHIP_DISARM = ABILITIES.register("blackwhip_disarm", BlackwhipDisarmAbility.Serializer::new);
        BLACKWHIP_DETACH = ABILITIES.register("blackwhip_detach", BlackwhipDetachAbility.Serializer::new);
        BLACKWHIP_RESTRICT = ABILITIES.register("blackwhip_restrict", BlackwhipRestrictAbility.Serializer::new);
        BLACKWHIP_MOVE_TAGGED = ABILITIES.register("blackwhip_move_tagged", BlackwhipMoveTaggedAbility.Serializer::new);
        BLACKWHIP_SWING = ABILITIES.register("blackwhip_swing", BlackwhipSwingAbility.Serializer::new);
        BLACKWHIP_WEB_SWING = ABILITIES.register("blackwhip_web_swing", BlackwhipWebSwingAbility.Serializer::new);
        BLACKWHIP_ZIP = ABILITIES.register("blackwhip_zip", BlackwhipZipAbility.Serializer::new);
        BLACKWHIP_CHARGE_ZIP = ABILITIES.register("blackwhip_charge_zip", BlackwhipChargeZipAbility.Serializer::new);
        BLACKWHIP_MAGNET = ABILITIES.register("blackwhip_magnet", BlackwhipMagnetAbility.Serializer::new);
        BLACKWHIP_BLOCK_TOSS = ABILITIES.register("blackwhip_block_toss", BlackwhipBlockTossAbility.Serializer::new);
        BLACKWHIP_LIMB_REINFORCE = ABILITIES.register("blackwhip_limb_reinforce", BlackwhipLimbReinforceAbility.Serializer::new);
        BLACKWHIP_BODY_REINFORCE = ABILITIES.register("blackwhip_body_reinforce", BlackwhipBodyReinforceAbility.Serializer::new);
        FLOAT = ABILITIES.register("float", FloatAbility.Serializer::new);
    }

}
