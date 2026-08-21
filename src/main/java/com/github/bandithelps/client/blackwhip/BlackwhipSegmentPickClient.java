package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.BlackwhipSegmentEntity;
import net.minecraft.client.Minecraft;

/**
 * Client-only pick filter so owner/target click through their own chain segments.
 */
public final class BlackwhipSegmentPickClient {

    private BlackwhipSegmentPickClient() {
    }

    public static boolean isPickableForLocalPlayer(BlackwhipSegmentEntity segment) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return true;
        }
        BlackwhipChainEntity chain = segment.getChain();
        if (chain != null && chain.isParticipant(mc.player)) {
            return false;
        }
        return true;
    }
}
