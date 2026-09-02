package com.github.bandithelps.network;

import com.github.bandithelps.creation.CreationKnowledgeRecipe;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.items.BookOfKnowledgeItem;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BookOfKnowledgeSelectPayload(int index) implements CustomPacketPayload {
    public static final Type<BookOfKnowledgeSelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("yha", "book_of_knowledge_select"));

    public static final StreamCodec<ByteBuf, BookOfKnowledgeSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            BookOfKnowledgeSelectPayload::index,
            BookOfKnowledgeSelectPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BookOfKnowledgeSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack book = BookOfKnowledgeItem.findHeld(player);
            if (!BookOfKnowledgeItem.isBook(book)) {
                return;
            }
            List<CreationKnowledgeRecipe> choices = BookOfKnowledgeItem.getChoices(book);
            if (payload.index() < 0 || payload.index() >= choices.size()) {
                return;
            }
            CreationKnowledgeRecipe recipe = choices.get(payload.index());
            if (!CreationUtil.tryLearnKnowledgeRecipe(player, recipe)) {
                return;
            }
            book.shrink(1);
            player.closeContainer();
        });
    }
}
