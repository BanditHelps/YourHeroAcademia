package com.github.bandithelps.capabilities.creation;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationEnchantCatalog;
import com.github.bandithelps.creation.CreationEnchantEntry;
import com.github.bandithelps.creation.CreationEnchantments;
import com.github.bandithelps.creation.CreationEntry;
import com.github.bandithelps.creation.CreationPotionCatalog;
import com.github.bandithelps.creation.CreationPotionEntry;
import com.github.bandithelps.creation.CreationPotions;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.network.CreationSyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
        List<CreationSyncPayload.ClientEntry> entries = new ArrayList<>();
        for (CreationEntry entry : CreationUtil.researchableEntries(player)) {
            List<String> unlockVariants = List.of();
            if (entry.isWoodUnlock()) {
                unlockVariants = new ArrayList<>();
                for (Identifier variantId : entry.unlockVariantIds()) {
                    unlockVariants.add(variantId.toString());
                }
            } else if (entry.unlockAbility() != null && CreationUtil.isAbilityUnlocked(player, entry.unlockAbility())) {
                unlockVariants = new ArrayList<>();
                for (Identifier variantId : entry.unlockVariantIds()) {
                    unlockVariants.add(variantId.toString());
                }
            }
            entries.add(new CreationSyncPayload.ClientEntry(
                    entry.itemId().toString(),
                    entry.tab().id(),
                    CreationUtil.creationCost(player, entry),
                    entry.researchCost(),
                    data.getProgress(entry.itemId()),
                    data.isUnlocked(entry.itemId()),
                    entry.nuggetId() == null ? "" : entry.nuggetId().toString(),
                    entry.blockId() == null ? "" : entry.blockId().toString(),
                    entry.groupId() == null ? "" : entry.groupId().toString(),
                    entry.groupIcon() == null ? "" : entry.groupIcon().toString(),
                    unlockVariants,
                    entry.isWoodUnlock()
            ));
        }
        List<CreationSyncPayload.ClientEnchantEntry> enchants = new ArrayList<>();
        for (CreationEnchantEntry entry : CreationEnchantCatalog.getInstance().allEntries()) {
            int vanillaMax = CreationEnchantments.vanillaMaxLevel(player.registryAccess(), entry.enchantId());
            int maxLevel = entry.resolvedMaxLevel(vanillaMax);
            List<Integer> costs = new ArrayList<>();
            if (entry.lipidCosts() != null) {
                for (int cost : entry.lipidCosts()) {
                    costs.add(Math.max(1, cost));
                }
            }
            enchants.add(new CreationSyncPayload.ClientEnchantEntry(
                    entry.enchantId().toString(),
                    Math.max(1, entry.lipidCostPerLevel()),
                    costs,
                    maxLevel,
                    entry.researchCost(),
                    data.getEnchantProgress(entry.enchantId()),
                    data.isEnchantUnlocked(entry.enchantId()),
                    CreationUtil.isEnchantResearchable(player, entry)
            ));
        }
        List<CreationSyncPayload.ClientPotionEntry> potions = new ArrayList<>();
        for (CreationPotionEntry entry : CreationPotionCatalog.getInstance().allEntries()) {
            boolean instant = CreationPotions.isInstant(entry.effectId(), entry.instantOverride());
            potions.add(new CreationSyncPayload.ClientPotionEntry(
                    entry.effectId().toString(),
                    entry.groupId() == null ? "" : entry.groupId().toString(),
                    entry.groupIcon() == null ? "" : entry.groupIcon().toString(),
                    Math.max(1, entry.lipidCost()),
                    Math.max(0, entry.lipidCostPerAmplifier()),
                    entry.researchCost(),
                    data.getPotionProgress(entry.effectId()),
                    data.isPotionUnlocked(entry.effectId()),
                    CreationUtil.isPotionResearchable(player, entry),
                    entry.maxDurationSeconds(),
                    instant
            ));
        }
        PacketDistributor.sendToPlayer(player, new CreationSyncPayload(
                data.encodedUnlocked(),
                data.encodedQuickSlots(),
                entries,
                enchants,
                potions,
                CreationUtil.unlockedQuickSlotCount(player),
                CreationUtil.isGearTabUnlocked(player),
                CreationUtil.isAlchemyTabUnlocked(player),
                CreationUtil.isAbilityUnlocked(player, CreationUtil.CHEMICAL_SPLASH),
                CreationUtil.isAbilityUnlocked(player, CreationUtil.CHEMICAL_LINGER),
                CreationUtil.isAbilityUnlocked(player, CreationUtil.FLETCHER_ARROW_EFFECTS),
                CreationUtil.isAbilityUnlocked(player, CreationUtil.CHEMICAL_TIMING),
                CreationUtil.isAbilityUnlocked(player, CreationUtil.CHEMICAL_POTENCY),
                CreationUtil.isAbilityUnlocked(player, CreationUtil.CHEMICAL_3),
                CreationUtil.sacrificesRequired()
        ));
        LAST_SENT.put(player.getUUID(), entries.size());
    }
}
