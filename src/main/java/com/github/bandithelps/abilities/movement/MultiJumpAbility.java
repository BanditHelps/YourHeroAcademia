package com.github.bandithelps.abilities.movement;

import com.github.bandithelps.abilities.AbilityRegister;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

public class MultiJumpAbility extends Ability {
    private static final double DEFAULT_JUMP_STRENGTH = 1.5D;
    static final String JUMPS_USED_KEY = "YhaMultiJumpsUsed";

    public static final MapCodec<MultiJumpAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Codec.INT.optionalFieldOf("max_jumps", 2).forGetter((ab) -> ab.maxJumps),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, MultiJumpAbility::new));

    public final int maxJumps;

    public MultiJumpAbility(int maxJumps, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.maxJumps = Math.max(1, maxJumps);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        this.tryPerformJump(player);
    }

    public boolean tryPerformJump(ServerPlayer player) {
        if (player.onGround()) {
            resetJumps(player);
            return false;
        }

        int jumpsUsed = getJumpsUsed(player);
        if (jumpsUsed >= this.maxJumps) {
            return false;
        }

        Vec3 currentVelocity = player.getDeltaMovement();
        double vertical = player.getAttributeValue(Attributes.JUMP_STRENGTH);
        if (vertical <= 0.0D) {
            vertical = DEFAULT_JUMP_STRENGTH;
        }
        player.setDeltaMovement(currentVelocity.x, vertical + (jumpsUsed == 0 ? 0 : 0.1), currentVelocity.z);
        player.fallDistance = 0.0F;
        setJumpsUsed(player, jumpsUsed + 1);

        // Movement changes done server-side need an explicit packet to keep client prediction in sync.
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = true;
        return true;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.MULTI_JUMP.get();
    }

    static void resetJumps(ServerPlayer player) {
        player.getPersistentData().remove(JUMPS_USED_KEY);
    }

    private static int getJumpsUsed(ServerPlayer player) {
        return player.getPersistentData().getInt(JUMPS_USED_KEY).orElse(0);
    }

    private static void setJumpsUsed(ServerPlayer player, int jumpsUsed) {
        player.getPersistentData().putInt(JUMPS_USED_KEY, jumpsUsed);
    }

    public static class Serializer extends AbilitySerializer<MultiJumpAbility> {
        @Override
        public MapCodec<MultiJumpAbility> codec() {
            return MultiJumpAbility.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, MultiJumpAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Lets the player jump multiple times before touching the ground.")
                    .add("max_jumps", TYPE_INT, "Maximum number of jumps allowed before landing resets the counter")
                    .addExampleObject(new MultiJumpAbility(2, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
