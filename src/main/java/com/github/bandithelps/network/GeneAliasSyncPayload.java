package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.gene.GeneAliasClientCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record GeneAliasSyncPayload(
        boolean replaceAll,
        String[] keys,
        String[] values
) implements CustomPacketPayload {
    public static final Type<GeneAliasSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_alias_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, String[]> STRING_ARRAY_CODEC =
            STRING_LIST_CODEC.map(
                    list -> list.toArray(new String[0]),
                    Arrays::asList
            );

    public GeneAliasSyncPayload {
        keys = sanitizeArray(keys);
        values = sanitizeArray(values);
    }

    public static GeneAliasSyncPayload full(Map<String, String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return new GeneAliasSyncPayload(true, new String[0], new String[0]);
        }
        String[] keys = new String[aliases.size()];
        String[] values = new String[aliases.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            keys[index] = entry.getKey();
            values[index] = entry.getValue();
            index++;
        }
        return new GeneAliasSyncPayload(true, keys, values);
    }

    public static GeneAliasSyncPayload singleUpdate(String key, String value) {
        return new GeneAliasSyncPayload(false, new String[]{key}, new String[]{value});
    }

    public static final StreamCodec<ByteBuf, GeneAliasSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            GeneAliasSyncPayload::replaceAll,
            STRING_ARRAY_CODEC,
            GeneAliasSyncPayload::keys,
            STRING_ARRAY_CODEC,
            GeneAliasSyncPayload::values,
            GeneAliasSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneAliasSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.replaceAll()) {
                GeneAliasClientCache.replaceAll(payload.keys(), payload.values());
            } else {
                GeneAliasClientCache.applyUpdates(payload.keys(), payload.values());
            }
        });
    }

    private static String[] sanitizeArray(String[] input) {
        if (input == null) {
            return new String[0];
        }
        String[] sanitized = new String[input.length];
        for (int i = 0; i < input.length; i++) {
            sanitized[i] = input[i] == null ? "" : input[i];
        }
        return sanitized;
    }
}
