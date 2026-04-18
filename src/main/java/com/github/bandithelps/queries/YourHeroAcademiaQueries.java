package com.github.bandithelps.queries;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.attributes.QuirkAttributes;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.IBodyData;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.threetag.palladium.event.RegisterMoLangQueriesEvent;
import net.threetag.palladium.logic.molang.EntityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import team.unnamed.mocha.runtime.binding.Binding;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class YourHeroAcademiaQueries implements ObjectValue {
    private final EntityContext context;
    private final Map<String, Supplier<Object>> functions = new HashMap();


    @SubscribeEvent
    static void registerMoLang(RegisterMoLangQueriesEvent e) {
        e.register(YourHeroAcademia.MODID, YourHeroAcademiaQueries.class, YourHeroAcademiaQueries::new);
    }

    public YourHeroAcademiaQueries(EntityContext context) {
        this.context = context;
        this.functions.put("quirk_factor", this::quirk_factor);
        this.functions.put("head_health", this::head_health);
        this.functions.put("chest_health", this::chest_health);
        this.functions.put("left_arm_health", this::left_arm_health);
        this.functions.put("right_arm_health", this::right_arm_health);
        this.functions.put("left_leg_health", this::left_leg_health);
        this.functions.put("right_leg_health", this::right_leg_health);
        this.functions.put("left_hand_health", this::left_hand_health);
        this.functions.put("right_hand_health", this::right_hand_health);
        this.functions.put("left_foot_health", this::left_foot_health);
        this.functions.put("right_foot_health", this::right_foot_health);
        this.functions.put("main_arm_health", this::main_arm_health);
        this.functions.put("off_arm_health", this::off_arm_health);

        // Functions for Physical Stats
        this.functions.put("speed_current", this::speed_current);
        this.functions.put("strength_current", this::strength_current);
        this.functions.put("armor_current", this::armor_current);
        this.functions.put("max_health_current", this::max_health_current);
        this.functions.put("armor_toughness_current", this::armor_toughness_current);
    }

    public @NotNull Value get(@NonNull String name) {
        return this.functions.containsKey(name) ? Value.of(((Supplier)this.functions.get(name)).get()) : Value.nil();
    }

    @Binding({"quirk_factor"})
    public double quirk_factor() {
        if (context.entity() instanceof Player player) {
            return QuirkFactorUtil.getQuirkFactor(player);
        }
        return QuirkAttributes.QUIRK_FACTOR_DEFAULT;
    }

    @Binding({"head_health"})
    public double head_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.HEAD);
        }
        return 100;
    }

    @Binding({"chest_health"})
    public double chest_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.CHEST);
        }
        return 100;
    }

    @Binding({"left_arm_health"})
    public double left_arm_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.LEFT_ARM);
        }
        return 100;
    }

    @Binding({"right_arm_health"})
    public double right_arm_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.RIGHT_ARM);
        }
        return 100;
    }

    @Binding({"left_leg_health"})
    public double left_leg_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.LEFT_LEG);
        }
        return 100;
    }

    @Binding({"right_leg_health"})
    public double right_leg_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.RIGHT_LEG);
        }
        return 100;
    }

    @Binding({"left_hand_health"})
    public double left_hand_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.LEFT_HAND);
        }
        return 100;
    }

    @Binding({"right_hand_health"})
    public double right_hand_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.RIGHT_HAND);
        }
        return 100;
    }

    @Binding({"left_foot_health"})
    public double left_foot_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.LEFT_FOOT);
        }
        return 100;
    }

    @Binding({"right_foot_health"})
    public double right_foot_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.RIGHT_FOOT);
        }
        return 100;
    }

    @Binding({"main_arm_health"})
    public double main_arm_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.MAIN_ARM);
        }
        return 100;
    }

    @Binding({"off_arm_health"})
    public double off_arm_health() {
        if (context.entity() instanceof Player player) {
            IBodyData body = BodyAttachments.get(player);
            return body.getHealth(player, BodyPart.OFF_ARM);
        }
        return 100;
    }

    @Binding({"speed_current"})
    public double speed_current() {
        if (context.entity() instanceof Player player) {
            return fetchBodyPhysicalStat("speed_current", player);
        }
        return 0;
    }

    @Binding({"strength_current"})
    public double strength_current() {
        if (context.entity() instanceof Player player) {
            return fetchBodyPhysicalStat("strength_current", player);
        }
        return 0;
    }

    @Binding({"armor_current"})
    public double armor_current() {
        if (context.entity() instanceof Player player) {
            return fetchBodyPhysicalStat("armor_current", player);
        }
        return 0;
    }

    @Binding({"max_health_current"})
    public double max_health_current() {
        if (context.entity() instanceof Player player) {
            return fetchBodyPhysicalStat("max_health_current", player);
        }
        return 0;
    }

    @Binding({"armor_toughness_current"})
    public double armor_toughness_current() {
        if (context.entity() instanceof Player player) {
            return fetchBodyPhysicalStat("armor_toughness_current", player);
        }
        return 0;
    }

    @Override
    public @Nullable ObjectProperty getProperty(@NotNull String name) {
        return null;
    }

    private float fetchBodyPhysicalStat(String key, Player player) {
        IBodyData body = BodyAttachments.get(player);
        return body.getCustomFloat(player, BodyPart.CHEST, key, 0);
    }
}
