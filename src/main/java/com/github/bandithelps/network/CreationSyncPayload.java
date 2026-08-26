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
        List<ClientEnchantEntry> enchants,
        List<ClientPotionEntry> potions,
        int unlockedQuickSlots,
        boolean gearTabUnlocked,
        boolean alchemyTabUnlocked,
        boolean potionSplash,
        boolean potionLinger,
        boolean potionArrow,
        boolean potionTiming,
        boolean potionPotency,
        boolean potionMaster,
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
        ByteBufCodecs.VAR_INT.encode(buf, payload.enchants().size());
        for (ClientEnchantEntry enchant : payload.enchants()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, enchant.enchantId());
            ByteBufCodecs.VAR_INT.encode(buf, enchant.lipidCostPerLevel());
            ByteBufCodecs.VAR_INT.encode(buf, enchant.lipidCosts().size());
            for (int cost : enchant.lipidCosts()) {
                ByteBufCodecs.VAR_INT.encode(buf, cost);
            }
            ByteBufCodecs.VAR_INT.encode(buf, enchant.maxLevel());
            ByteBufCodecs.VAR_INT.encode(buf, enchant.researchCost());
            ByteBufCodecs.VAR_INT.encode(buf, enchant.progress());
            ByteBufCodecs.BOOL.encode(buf, enchant.unlocked());
            ByteBufCodecs.BOOL.encode(buf, enchant.researchable());
        }
        ByteBufCodecs.VAR_INT.encode(buf, payload.potions().size());
        for (ClientPotionEntry potion : payload.potions()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, potion.effectId());
            ByteBufCodecs.STRING_UTF8.encode(buf, blankToEmpty(potion.groupId()));
            ByteBufCodecs.STRING_UTF8.encode(buf, blankToEmpty(potion.groupIcon()));
            ByteBufCodecs.VAR_INT.encode(buf, potion.lipidCost());
            ByteBufCodecs.VAR_INT.encode(buf, potion.lipidCostPerAmplifier());
            ByteBufCodecs.VAR_INT.encode(buf, potion.researchCost());
            ByteBufCodecs.VAR_INT.encode(buf, potion.progress());
            ByteBufCodecs.BOOL.encode(buf, potion.unlocked());
            ByteBufCodecs.BOOL.encode(buf, potion.researchable());
            ByteBufCodecs.VAR_INT.encode(buf, potion.maxDurationSeconds());
            ByteBufCodecs.BOOL.encode(buf, potion.instant());
        }
        ByteBufCodecs.VAR_INT.encode(buf, payload.unlockedQuickSlots());
        ByteBufCodecs.BOOL.encode(buf, payload.gearTabUnlocked());
        ByteBufCodecs.BOOL.encode(buf, payload.alchemyTabUnlocked());
        ByteBufCodecs.BOOL.encode(buf, payload.potionSplash());
        ByteBufCodecs.BOOL.encode(buf, payload.potionLinger());
        ByteBufCodecs.BOOL.encode(buf, payload.potionArrow());
        ByteBufCodecs.BOOL.encode(buf, payload.potionTiming());
        ByteBufCodecs.BOOL.encode(buf, payload.potionPotency());
        ByteBufCodecs.BOOL.encode(buf, payload.potionMaster());
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
        int enchantCount = ByteBufCodecs.VAR_INT.decode(buf);
        List<ClientEnchantEntry> enchants = new ArrayList<>(enchantCount);
        for (int i = 0; i < enchantCount; i++) {
            String enchantId = ByteBufCodecs.STRING_UTF8.decode(buf);
            int lipidCostPerLevel = ByteBufCodecs.VAR_INT.decode(buf);
            int costCount = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> lipidCosts = new ArrayList<>(costCount);
            for (int c = 0; c < costCount; c++) {
                lipidCosts.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            enchants.add(new ClientEnchantEntry(
                    enchantId,
                    lipidCostPerLevel,
                    lipidCosts,
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            ));
        }
        int potionCount = ByteBufCodecs.VAR_INT.decode(buf);
        List<ClientPotionEntry> potions = new ArrayList<>(potionCount);
        for (int i = 0; i < potionCount; i++) {
            potions.add(new ClientPotionEntry(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            ));
        }
        return new CreationSyncPayload(
                unlocked,
                quickSlots,
                entries,
                enchants,
                potions,
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
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

    public record ClientEnchantEntry(
            String enchantId,
            int lipidCostPerLevel,
            List<Integer> lipidCosts,
            int maxLevel,
            int researchCost,
            int progress,
            boolean unlocked,
            boolean researchable
    ) {
        public int costForLevel(int level) {
            if (level <= 0) {
                return 0;
            }
            if (lipidCosts != null && !lipidCosts.isEmpty()) {
                int index = Math.min(level, lipidCosts.size()) - 1;
                return Math.max(1, lipidCosts.get(index));
            }
            return Math.max(1, lipidCostPerLevel * level);
        }
    }

    public record ClientPotionEntry(
            String effectId,
            String groupId,
            String groupIcon,
            int lipidCost,
            int lipidCostPerAmplifier,
            int researchCost,
            int progress,
            boolean unlocked,
            boolean researchable,
            int maxDurationSeconds,
            boolean instant
    ) {
        public int costFor(int extraAmplifier, int durationSeconds, float formFactor) {
            int extra = Math.max(0, extraAmplifier);
            double durationFactor = instant ? 1.0 : Math.max(1.0, Math.max(1, durationSeconds) / 15.0);
            return Math.max(1, (int) Math.ceil((lipidCost + extra * lipidCostPerAmplifier) * durationFactor * Math.max(0.01f, formFactor)));
        }
    }
}
