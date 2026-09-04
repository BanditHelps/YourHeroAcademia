package com.github.bandithelps.capabilities.creation;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CreationAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YourHeroAcademia.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CreationData>> CREATION =
            ATTACHMENTS.register("creation_data", () -> AttachmentType.builder(CreationData::new)
                    .serialize(CreationData.CODEC)
                    .copyOnDeath()
                    .build());

    private CreationAttachments() {
    }

    public static CreationData get(Player player) {
        return player.getData(CREATION);
    }
}
