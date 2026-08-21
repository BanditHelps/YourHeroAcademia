package com.github.bandithelps.abilities.common;

import com.github.bandithelps.abilities.AbilityRegister;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.threetag.palladium.util.PlayerUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaySoundAbility extends Ability {

    public static final MapCodec<PlaySoundAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Identifier.CODEC.fieldOf("sound").forGetter((ab) -> ab.sound),
                    ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("volume", 1.0F).forGetter((ab) -> ab.volume),
                    ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("pitch", 1.0F).forGetter((ab) -> ab.pitch),
                    Codec.BOOL.optionalFieldOf("looping", false).forGetter((ab) -> ab.looping),
                    Codec.INT.optionalFieldOf("loop_delay", 1).forGetter((ab) -> ab.loopDelay),
                    Codec.BOOL.optionalFieldOf("play_self", false).forGetter((ab) -> ab.playSelf),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, PlaySoundAbility::new));

    public final Identifier sound;
    public final float volume;
    public final float pitch;
    public final boolean looping;
    public final int loopDelay;
    public final boolean playSelf;

    private final Map<UUID, Long> nextLoopPlayTick = new HashMap<>();

    public PlaySoundAbility(
            Identifier sound,
            float volume,
            float pitch,
            boolean looping,
            int loopDelay,
            boolean playSelf,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.looping = looping;
        this.loopDelay = Math.max(0, loopDelay);
        this.playSelf = playSelf;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity.level().isClientSide()) {
            return;
        }

        this.play(entity);

        if (this.looping) {
            long nextTick = entity.level().getGameTime() + Math.max(1, this.loopDelay);
            this.nextLoopPlayTick.put(entity.getUUID(), nextTick);
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean dampened) {
        boolean keepsRunning = super.tick(entity, abilityInstance, dampened);
        if (!keepsRunning || !this.looping || entity.level().isClientSide()) {
            return keepsRunning;
        }

        UUID entityId = entity.getUUID();
        long now = entity.level().getGameTime();
        long next = this.nextLoopPlayTick.getOrDefault(entityId, now + Math.max(1, this.loopDelay));
        if (now >= next) {
            this.play(entity);
            this.nextLoopPlayTick.put(entityId, now + Math.max(1, this.loopDelay));
        }

        return keepsRunning;
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        this.nextLoopPlayTick.remove(entity.getUUID());
    }

    private void play(LivingEntity entity) {
        if (this.playSelf) {
            if (entity instanceof Player player) {
                PlayerUtil.playSound(player, entity.getX(), entity.getEyeY(), entity.getZ(), this.sound, entity.getSoundSource(), this.volume, this.pitch);
            }
        } else {
            PlayerUtil.playSoundToAll(entity.level(), entity.getX(), entity.getEyeY(), entity.getZ(), 100.0D, this.sound, entity.getSoundSource(), this.volume, this.pitch);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.PLAY_SOUND.get();
    }

    public static class Serializer extends AbilitySerializer<PlaySoundAbility> {
        @Override
        public MapCodec<PlaySoundAbility> codec() {
            return PlaySoundAbility.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, PlaySoundAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Plays a sound once or repeatedly while the ability remains enabled.")
                    .add("sound", TYPE_IDENTIFIER, "The sound that is played.")
                    .addOptional("volume", TYPE_FLOAT, "The volume for the played sound.", 1.0F)
                    .addOptional("pitch", TYPE_FLOAT, "The pitch for the played sound.", 1.0F)
                    .addOptional("looping", TYPE_BOOLEAN, "Whether the sound should replay while this ability is enabled.", false)
                    .addOptional("loop_delay", TYPE_INT, "Ticks to wait between each replay when looping is enabled.", 1)
                    .addOptional("play_self", TYPE_BOOLEAN, "Whether to only play for the entity using the ability, or all nearby players.", false)
                    .addExampleObject(new PlaySoundAbility(
                            Identifier.withDefaultNamespace("item.elytra.flying"),
                            1.0F,
                            1.0F,
                            true,
                            5,
                            false,
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            List.of()
                    ));
        }
    }
}
