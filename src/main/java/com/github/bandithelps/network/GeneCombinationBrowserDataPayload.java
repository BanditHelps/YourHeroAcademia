package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.gene_combiner.ClientGeneCombinationBrowserState;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GeneCombinationBrowserDataPayload(List<String> lines) implements CustomPacketPayload {
    public static final Type<GeneCombinationBrowserDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_combination_browser_data"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, GeneCombinationBrowserDataPayload> STREAM_CODEC = StreamCodec.composite(
            STRING_LIST_CODEC,
            GeneCombinationBrowserDataPayload::lines,
            GeneCombinationBrowserDataPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneCombinationBrowserDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientGeneCombinationBrowserState.setRecipesFromPayload(payload.lines()));
    }
}
