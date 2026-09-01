package com.github.bandithelps.client.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Switches Float poses from local velocity: still = hover, moving = flight stance,
 * sinking = descend. Plays for any avatar that currently has Float enabled.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class FloatPlayerAnimation {

    public static final Identifier LAYER_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "float");
    public static final Identifier HOVER_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "hovering");
    public static final Identifier FLY_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "levitating");
    public static final Identifier DESCEND_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "descending");

    private static final Identifier PALLADIUM_HOVER =
            Identifier.fromNamespaceAndPath("palladium", "flight.hovering");
    private static final Identifier PALLADIUM_FLY =
            Identifier.fromNamespaceAndPath("palladium", "flight.levitating");

    private static final int LAYER_PRIORITY = 1900;
    private static final int BLEND_TICKS = 8;
    private static final double MOVE_THRESHOLD_SQR = 0.012d;
    private static final double DESCEND_Y = -0.04d;

    private FloatPlayerAnimation() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                LAYER_PRIORITY,
                FloatPlayerAnimation::createController));
    }

    private static PlayerAnimationController createController(Avatar avatar) {
        return new PlayerAnimationController(avatar, FloatPlayerAnimation::handle);
    }

    private static PlayState handle(
            AnimationController controller,
            AnimationData data,
            AnimationController.AnimationSetter setter) {
        if (!(controller instanceof PlayerAnimationController playerController)) {
            return PlayState.STOP;
        }
        Avatar avatar = playerController.getAvatar();
        if (!(avatar instanceof LivingEntity living) || !FloatAbility.isActive(living)) {
            return PlayState.STOP;
        }

        Identifier animationId = pickAnimation(living);
        Animation animation = PlayerAnimResources.getAnimation(animationId);
        if (animation == null) {
            return PlayState.STOP;
        }
        if (controller.hasAnimationFinished()) {
            controller.forceAnimationReset();
        }
        return setter.setAnimation(RawAnimation.begin().thenLoop(animation), BLEND_TICKS);
    }

    private static Identifier pickAnimation(LivingEntity entity) {
        Vec3 vel = entity.getDeltaMovement();
        if (vel.y < DESCEND_Y) {
            Animation descend = PlayerAnimResources.getAnimation(DESCEND_ID);
            return descend != null ? DESCEND_ID : hoverId();
        }
        if (vel.horizontalDistanceSqr() + vel.y * vel.y > MOVE_THRESHOLD_SQR) {
            return flyId();
        }
        return hoverId();
    }

    private static Identifier hoverId() {
        return PlayerAnimResources.getAnimation(PALLADIUM_HOVER) != null ? PALLADIUM_HOVER : HOVER_ID;
    }

    private static Identifier flyId() {
        return PlayerAnimResources.getAnimation(PALLADIUM_FLY) != null ? PALLADIUM_FLY : FLY_ID;
    }
}
