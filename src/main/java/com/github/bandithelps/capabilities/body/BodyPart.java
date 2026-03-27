package com.github.bandithelps.capabilities.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.threetag.palladium.entity.PlayerSlot;

import java.util.*;

public enum BodyPart {
    HEAD("head", true),
    CHEST("chest", true),
    LEFT_ARM("left_arm", true),
    RIGHT_ARM("right_arm", true),
    LEFT_LEG("left_leg", true),
    RIGHT_LEG("right_leg", true),
    LEFT_HAND("left_hand", true),
    RIGHT_HAND("right_hand", true),
    LEFT_FOOT("left_foot", true),
    RIGHT_FOOT("right_foot", true),
    MAIN_ARM("main_arm", false),
    OFF_ARM("off_arm", false);

    public static final Codec<BodyPart> CODEC = Codec.STRING.comapFlatMap(
            value -> {
                BodyPart parsed = fromId(value);
                if (parsed == null) {
                    return DataResult.error(() -> "Unknown body part: " + value);
                }
                return DataResult.success(parsed);
            },
            BodyPart::getId
    );

    private static final List<BodyPart> PHYSICAL_PARTS = Arrays.stream(values())
            .filter(BodyPart::isPhysical)
            .toList();

    private final String id;
    private final boolean physical;

    BodyPart(String id, boolean physical) {
        this.id = id;
        this.physical = physical;
    }

    public String getId() {
        return id;
    }

    public boolean isPhysical() {
        return physical;
    }

    public boolean isVirtualAlias() {
        return !physical;
    }

    public static BodyPart fromId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (BodyPart part : values()) {
            if (part.id.equals(normalized)) {
                return part;
            }
        }
        return null;
    }

    public static List<BodyPart> physicalParts() {
        return PHYSICAL_PARTS;
    }

    public static BodyPart resolveForPlayer(Player player, BodyPart requested) {
        if (requested == MAIN_ARM) {
            return player.getMainArm() == HumanoidArm.LEFT ? LEFT_ARM : RIGHT_ARM;
        }
        if (requested == OFF_ARM) {
            return player.getMainArm() == HumanoidArm.LEFT ? RIGHT_ARM : LEFT_ARM;
        }
        return requested;
    }

    /**
     * Used to reference inside the documentation the valid parts
     * @return
     */
    public static Collection<String> exampleValues() {
        List<String> parts = new ArrayList();
        parts.add(HEAD.getId());
        parts.add(CHEST.getId());
        parts.add(LEFT_ARM.getId());
        parts.add(RIGHT_ARM.getId());
        parts.add(LEFT_HAND.getId());
        parts.add(RIGHT_HAND.getId());
        parts.add(LEFT_LEG.getId());
        parts.add(RIGHT_LEG.getId());
        parts.add(LEFT_FOOT.getId());
        parts.add(RIGHT_FOOT.getId());
        parts.add(MAIN_ARM.getId());
        parts.add(OFF_ARM.getId());
        return parts;
    }
}
