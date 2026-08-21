package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Plays zip punch animations from {@code yha:player_animations/blackwhip/zip_punch.animation.json}:
 * <ul>
 *   <li>{@code reel_in} while reeling a living target</li>
 *   <li>{@code flying_punch} when the reel slam connects</li>
 * </ul>
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipChainZipPlayerAnimation {

    public static final Identifier LAYER_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_zip");
    public static final Identifier REEL_ANIM_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "reel_in");
    public static final Identifier PUNCH_ANIM_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "flying_punch");

    /** Match / slightly above web-swing so zip poses win during the ability. */
    private static final int LAYER_PRIORITY = 2100;
    private static final int BLEND_TICKS = 3;

    private static int lastPunchToken = -1;

    private BlackwhipChainZipPlayerAnimation() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                LAYER_PRIORITY,
                BlackwhipChainZipPlayerAnimation::createController));
    }

    private static PlayerAnimationController createController(Avatar avatar) {
        return new PlayerAnimationController(avatar, BlackwhipChainZipPlayerAnimation::handle);
    }

    private static PlayState handle(
            AnimationController controller,
            AnimationData data,
            AnimationController.AnimationSetter setter) {
        if (!(controller instanceof PlayerAnimationController playerController)) {
            return PlayState.STOP;
        }
        if (!isLocalAvatar(playerController.getAvatar())) {
            return PlayState.STOP;
        }

        ClientBlackwhipChainZipAnimState.Phase phase = ClientBlackwhipChainZipAnimState.getPhase();
        if (phase == ClientBlackwhipChainZipAnimState.Phase.NONE) {
            return PlayState.STOP;
        }

        if (phase == ClientBlackwhipChainZipAnimState.Phase.PUNCH) {
            return playPunch(controller, setter);
        }
        return playReel(controller, setter);
    }

    private static PlayState playReel(AnimationController controller, AnimationController.AnimationSetter setter) {
        Animation animation = PlayerAnimResources.getAnimation(REEL_ANIM_ID);
        if (animation == null) {
            return PlayState.STOP;
        }
        // Hold last frame (matches JSON loop) for the whole reel.
        if (controller.hasAnimationFinished()) {
            controller.forceAnimationReset();
        }
        return setter.setAnimation(RawAnimation.begin().thenPlayAndHold(animation), BLEND_TICKS);
    }

    private static PlayState playPunch(AnimationController controller, AnimationController.AnimationSetter setter) {
        Animation animation = PlayerAnimResources.getAnimation(PUNCH_ANIM_ID);
        if (animation == null) {
            ClientBlackwhipChainZipAnimState.clear();
            return PlayState.STOP;
        }

        int token = ClientBlackwhipChainZipAnimState.getPunchToken();
        if (token != lastPunchToken) {
            lastPunchToken = token;
            controller.forceAnimationReset();
        } else if (controller.hasAnimationFinished()) {
            ClientBlackwhipChainZipAnimState.clear();
            return PlayState.STOP;
        }

        return setter.setAnimation(RawAnimation.begin().thenPlay(animation), BLEND_TICKS);
    }

    private static boolean isLocalAvatar(Avatar avatar) {
        LocalPlayer local = Minecraft.getInstance().player;
        return local != null && local == avatar;
    }
}
