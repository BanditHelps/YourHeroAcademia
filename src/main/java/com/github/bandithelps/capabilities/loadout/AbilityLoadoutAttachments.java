package com.github.bandithelps.capabilities.loadout;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class AbilityLoadoutAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YourHeroAcademia.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AbilityLoadoutData>> LOADOUT =
            ATTACHMENTS.register("ability_loadout", () -> AttachmentType.builder(AbilityLoadoutData::new)
                    .serialize(AbilityLoadoutData.CODEC)
                    .copyOnDeath()
                    .build());

    private AbilityLoadoutAttachments() {
    }

    public static AbilityLoadoutData get(Player player) {
        return player.getData(LOADOUT);
    }
}
