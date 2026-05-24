package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenGeneCombinationBrowserPayload(List<String> lines) implements CustomPacketPayload {
    public static final Type<OpenGeneCombinationBrowserPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "open_gene_combination_browser"));
    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    public static final StreamCodec<ByteBuf, OpenGeneCombinationBrowserPayload> STREAM_CODEC = StreamCodec.composite(
            STRING_LIST_CODEC,
            OpenGeneCombinationBrowserPayload::lines,
            OpenGeneCombinationBrowserPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGeneCombinationBrowserPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> stateClass = Class.forName("com.github.bandithelps.client.gene_combiner.ClientGeneCombinationBrowserState");
                stateClass.getMethod("setLines", List.class).invoke(null, payload.lines());
                Class<?> openerClass = Class.forName("com.github.bandithelps.client.ClientScreenOpener");
                openerClass.getMethod("openGeneCombinationBrowser").invoke(null);
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side does not include client classes.
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open gene combination browser on client", exception);
            }
        });
    }
}
