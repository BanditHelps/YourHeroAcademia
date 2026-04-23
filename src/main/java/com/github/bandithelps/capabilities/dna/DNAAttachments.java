package com.github.bandithelps.capabilities.dna;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class DNAAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YourHeroAcademia.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DNAData>> DNA =
            ATTACHMENTS.register("dna", () -> AttachmentType.builder(DNAData::new)
                    .serialize(DNAData.CODEC)
                    .copyOnDeath()
                    .build());

    private DNAAttachments() {
    }

    public static IDNAData get(Player player) {
        return player.getData(DNA);
    }
}