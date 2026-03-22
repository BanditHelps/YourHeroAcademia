package com.github.bandithelps.capabilities.stamina;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class StaminaAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YourHeroAcademia.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StaminaData>> STAMINA =
            ATTACHMENTS.register("stamina", () -> AttachmentType.builder(StaminaData::new)
                    .serialize(StaminaData.CODEC)
                    .copyOnDeath()
                    .build());

    private StaminaAttachments() {
    }

    public static IStaminaData get(Player player) {
        return player.getData(STAMINA);
    }
}
