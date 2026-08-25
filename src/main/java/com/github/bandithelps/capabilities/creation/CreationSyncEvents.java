package com.github.bandithelps.capabilities.creation;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationEnchantCatalog;
import com.github.bandithelps.creation.CreationEnchantEntry;
import com.github.bandithelps.creation.CreationEnchantments;
import com.github.bandithelps.creation.CreationEntry;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.network.CreationSyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class CreationSyncEvents {
    private static final java.util.Map<UUID, Integer> LAST_SENT = new ConcurrentHashMap<>();

    private CreationSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreationCatalog.getInstance().rebuildResolved();
            syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void syncNow(ServerPlayer player) {
        CreationData data = CreationAttachments.get(player);
        CreationUtil.migrateFormUnlocks(data);
        float efficiency = CreationUtil.efficiencyMultiplier(player);
        List<CreationSyncPayload.ClientEntry> entries = new ArrayList<>();
        for (CreationEntry entry : CreationUtil.researchableEntries(player)) {
            entries.add(new CreationSyncPayload.ClientEntry(
                    entry.itemId().toString(),
                    entry.tab().id(),
                    CreationUtil.creationCost(player, entry),
                    entry.researchCost(),
                    data.getProgress(entry.itemId()),
                    data.isUnlocked(entry.itemId()),
                    entry.nuggetId() == null ? "" : entry.nuggetId().toString(),
                    entry.blockId() == null ? "" : entry.blockId().toString()
            ));
        }
        List<CreationSyncPayload.ClientEnchantEntry> enchants = new ArrayList<>();
        for (CreationEnchantEntry entry : CreationEnchantCatalog.getInstance().allEntries()) {
            int vanillaMax = CreationEnchantments.vanillaMaxLevel(player.registryAccess(), entry.enchantId());
            int maxLevel = entry.resolvedMaxLevel(vanillaMax);
            int scaledPerLevel = Math.max(1, Mth.ceil(entry.lipidCostPerLevel() / efficiency));
            List<Integer> scaledCosts = new ArrayList<>();
            if (entry.lipidCosts() != null) {
                for (int cost : entry.lipidCosts()) {
                    scaledCosts.add(Math.max(1, Mth.ceil(cost / efficiency)));
                }
            }
            enchants.add(new CreationSyncPayload.ClientEnchantEntry(
                    entry.enchantId().toString(),
                    scaledPerLevel,
                    scaledCosts,
                    maxLevel,
                    entry.researchCost(),
                    data.getEnchantProgress(entry.enchantId()),
                    data.isEnchantUnlocked(entry.enchantId()),
                    CreationUtil.isEnchantResearchable(player, entry)
            ));
        }
        PacketDistributor.sendToPlayer(player, new CreationSyncPayload(
                data.encodedUnlocked(),
                data.encodedQuickSlots(),
                entries,
                enchants,
                CreationUtil.unlockedQuickSlotCount(player),
                CreationUtil.isGearTabUnlocked(player),
                CreationUtil.sacrificesRequired()
        ));
        LAST_SENT.put(player.getUUID(), entries.size());
    }
}
