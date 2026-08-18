package com.github.bandithelps.capabilities.body;

import com.github.bandithelps.utils.blackwhip.BlackwhipReinforceUtil;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies body-part damage with Blackwhip limb-reinforce reduction applied first.
 * Callers that damage several parts should invoke {@link #damage} per part, then
 * {@link BodySyncEvents#syncNow(ServerPlayer)} once.
 */
public final class BodyDamageHelper {
    private BodyDamageHelper() {
    }

    public static void damage(ServerPlayer player, BodyPart part, float amount) {
        if (player == null || part == null || amount <= 0.0f || !player.isAlive()) {
            return;
        }
        float reduction = BlackwhipReinforceUtil.reductionFor(player, part);
        float applied = amount * (1.0f - reduction);
        if (applied <= 0.0f) {
            return;
        }
        BodyAttachments.get(player).damagePart(player, part, applied);
    }

    public static void damageAndSync(ServerPlayer player, BodyPart part, float amount) {
        damage(player, part, amount);
        BodySyncEvents.syncNow(player);
    }
}
