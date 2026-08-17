package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.loadout.ClientAbilityLoadoutState;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AbilityLoadoutSyncPayload(List<String> slots, Map<String, String> modes) implements CustomPacketPayload {
    public static final Type<AbilityLoadoutSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "ability_loadout_sync"));

    public static final StreamCodec<ByteBuf, AbilityLoadoutSyncPayload> STREAM_CODEC = StreamCodec.of(
            AbilityLoadoutSyncPayload::encode,
            AbilityLoadoutSyncPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbilityLoadoutSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientAbilityLoadoutState.apply(payload.slots(), payload.modes()));
    }

    private static void encode(ByteBuf buf, AbilityLoadoutSyncPayload payload) {
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, payload.slots());
        ByteBufCodecs.VAR_INT.encode(buf, payload.modes().size());
        payload.modes().forEach((key, value) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, key);
            ByteBufCodecs.STRING_UTF8.encode(buf, value);
        });
    }

    private static AbilityLoadoutSyncPayload decode(ByteBuf buf) {
        List<String> slots = new ArrayList<>(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf));
        int modeCount = ByteBufCodecs.VAR_INT.decode(buf);
        Map<String, String> modes = new LinkedHashMap<>();
        for (int i = 0; i < modeCount; i++) {
            String key = ByteBufCodecs.STRING_UTF8.decode(buf);
            String value = ByteBufCodecs.STRING_UTF8.decode(buf);
            modes.put(key, value);
        }
        return new AbilityLoadoutSyncPayload(slots, modes);
    }
}
