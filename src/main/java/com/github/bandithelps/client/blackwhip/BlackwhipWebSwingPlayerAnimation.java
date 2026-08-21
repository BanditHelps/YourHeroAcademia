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
 * Plays {@code yha:swinging} on the local player while web swing is active.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipWebSwingPlayerAnimation {

    public static final Identifier LAYER_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_web_swing");
    public static final Identifier ANIMATION_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "swinging");

    /** High enough to override idle/movement layers while the swing is held. */
    private static final int LAYER_PRIORITY = 2000;
    private static final int BLEND_TICKS = 5;

    private BlackwhipWebSwingPlayerAnimation() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                LAYER_PRIORITY,
                BlackwhipWebSwingPlayerAnimation::createController));
    }

    private static PlayerAnimationController createController(Avatar avatar) {
        return new PlayerAnimationController(avatar, BlackwhipWebSwingPlayerAnimation::handle);
    }

    private static PlayState handle(
            AnimationController controller,
            AnimationData data,
            AnimationController.AnimationSetter setter) {
        if (!(controller instanceof PlayerAnimationController playerController)) {
            return PlayState.STOP;
        }
        if (!shouldPlay(playerController.getAvatar())) {
            return PlayState.STOP;
        }

        Animation animation = PlayerAnimResources.getAnimation(ANIMATION_ID);
        if (animation == null) {
            return PlayState.STOP;
        }
        // PAL skips setAnimation when the RawAnimation equals the previous one.
        // After PlayState.STOP, currentRawAnimation is kept, so follow-up activations
        // would no-op unless we force a reload.
        if (controller.hasAnimationFinished()) {
            controller.forceAnimationReset();
        }
        return setter.setAnimation(RawAnimation.begin().thenLoop(animation), BLEND_TICKS);
    }

    private static boolean shouldPlay(Avatar avatar) {
        if (!ClientBlackwhipWebSwingState.isActive()) {
            return false;
        }
        LocalPlayer local = Minecraft.getInstance().player;
        return local != null && local == avatar;
    }
}
