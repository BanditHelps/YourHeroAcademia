package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.creation.ClientCreationState;
import com.github.bandithelps.creation.CreationForm;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreationSyncPayload(
        List<String> unlocked,
        List<String> quickSlots,
        List<ClientEntry> entries,
        int unlockedQuickSlots,
        boolean gearTabUnlocked,
        int sacrificesRequired
) implements CustomPacketPayload {
    public static final Type<CreationSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_sync"));

    public static final StreamCodec<ByteBuf, CreationSyncPayload> STREAM_CODEC = StreamCodec.of(
            CreationSyncPayload::encode,
            CreationSyncPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreationSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCreationState.apply(payload));
    }

    private static void encode(ByteBuf buf, CreationSyncPayload payload) {
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, payload.unlocked());
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, payload.quickSlots());
        ByteBufCodecs.VAR_INT.encode(buf, payload.entries().size());
        for (ClientEntry entry : payload.entries()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemId());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.tab());
            ByteBufCodecs.VAR_INT.encode(buf, entry.lipidCost());
            ByteBufCodecs.VAR_INT.encode(buf, entry.researchCost());
            ByteBufCodecs.VAR_INT.encode(buf, entry.progress());
            ByteBufCodecs.BOOL.encode(buf, entry.unlocked());
            ByteBufCodecs.STRING_UTF8.encode(buf, blankToEmpty(entry.nuggetId()));
            ByteBufCodecs.STRING_UTF8.encode(buf, blankToEmpty(entry.blockId()));
        }
        ByteBufCodecs.VAR_INT.encode(buf, payload.unlockedQuickSlots());
        ByteBufCodecs.BOOL.encode(buf, payload.gearTabUnlocked());
        ByteBufCodecs.VAR_INT.encode(buf, payload.sacrificesRequired());
    }

    private static CreationSyncPayload decode(ByteBuf buf) {
        List<String> unlocked = new ArrayList<>(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf));
        List<String> quickSlots = new ArrayList<>(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf));
        int entryCount = ByteBufCodecs.VAR_INT.decode(buf);
        List<ClientEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(new ClientEntry(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            ));
        }
        return new CreationSyncPayload(
                unlocked,
                quickSlots,
                entries,
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf)
        );
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ClientEntry(
            String itemId,
            String tab,
            int lipidCost,
            int researchCost,
            int progress,
            boolean unlocked,
            String nuggetId,
            String blockId
    ) {
        public boolean hasNugget() {
            return nuggetId != null && !nuggetId.isBlank();
        }

        public boolean hasBlock() {
            return blockId != null && !blockId.isBlank();
        }

        public boolean hasForms() {
            return hasNugget() || hasBlock();
        }

        public boolean matches(String requested) {
            if (requested == null) {
                return false;
            }
            return requested.equals(itemId)
                    || (hasNugget() && requested.equals(nuggetId))
                    || (hasBlock() && requested.equals(blockId));
        }

        public List<String> formChoices() {
            List<String> ids = new ArrayList<>();
            if (hasNugget()) {
                ids.add(nuggetId);
            }
            ids.add(itemId);
            if (hasBlock()) {
                ids.add(blockId);
            }
            return ids;
        }

        public int formCost(String formId) {
            return CreationForm.of(itemId, nuggetId, blockId, formId).scaledCost(lipidCost);
        }
    }
}
