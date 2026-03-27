package com.github.bandithelps.capabilities.body;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BodyAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YourHeroAcademia.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BodyData>> BODY =
            ATTACHMENTS.register("body_data", () -> AttachmentType.builder(BodyData::new)
                    .serialize(BodyData.CODEC)
                    .copyOnDeath()
                    .build());

    private BodyAttachments() {
    }

    public static IBodyData get(Player player) {
        return player.getData(BODY);
    }
}
