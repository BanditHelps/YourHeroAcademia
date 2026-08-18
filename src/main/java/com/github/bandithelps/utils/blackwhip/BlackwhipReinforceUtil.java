package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipBodyReinforceAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipLimbReinforceAbility;
import com.github.bandithelps.capabilities.body.BodyDamageHelper;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;

/**
 * Runtime lookups for Blackwhip reinforce abilities via Palladium {@link AbilityUtil}.
 */
public final class BlackwhipReinforceUtil {
    public static final Identifier POWER_ID = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain");
    public static final String ARM_REINFORCE_KEY = "bw_arm_reinforce";
    public static final String LEG_REINFORCE_KEY = "bw_leg_reinforce";
    public static final String BODY_REINFORCE_KEY = "bw_body_reinforce";

    private BlackwhipReinforceUtil() {
    }

    public static float reductionFor(ServerPlayer player, BodyPart part) {
        if (player == null || part == null) {
            return 0.0f;
        }
        float arm = reductionFrom(player, ARM_REINFORCE_KEY, part);
        if (arm > 0.0f) {
            return arm;
        }
        return reductionFrom(player, LEG_REINFORCE_KEY, part);
    }

    public static boolean tryConvertStaminaToBodyDamage(ServerPlayer player, int staminaAmount) {
        if (player == null || staminaAmount <= 0) {
            return false;
        }
        AbilityInstance<?> instance = AbilityUtil.getInstance(player, POWER_ID, BODY_REINFORCE_KEY);
        if (instance == null || !instance.isEnabled()) {
            return false;
        }
        if (!(instance.getAbility() instanceof BlackwhipBodyReinforceAbility ability)) {
            return true;
        }

        float rate = Math.max(0.0f, ability.staminaToDamage.getAsFloat(DataContext.forAbility(player, instance)));
        float damage = staminaAmount * rate;
        if (damage > 0.0f && ability.parts != null) {
            boolean damaged = false;
            for (String partId : ability.parts) {
                BodyPart part = BodyPart.fromId(partId);
                if (part == null) {
                    continue;
                }
                BodyDamageHelper.damage(player, part, damage);
                damaged = true;
            }
            if (damaged) {
                BodySyncEvents.syncNow(player);
            }
        }
        return true;
    }

    private static float reductionFrom(ServerPlayer player, String abilityKey, BodyPart part) {
        AbilityInstance<?> instance = AbilityUtil.getInstance(player, POWER_ID, abilityKey);
        if (instance == null || !instance.isEnabled()) {
            return 0.0f;
        }
        if (!(instance.getAbility() instanceof BlackwhipLimbReinforceAbility ability)) {
            return 0.0f;
        }
        if (!ability.protects(player, part)) {
            return 0.0f;
        }
        return Mth.clamp(ability.reduction.getAsFloat(DataContext.forAbility(player, instance)), 0.0f, 1.0f);
    }
}
