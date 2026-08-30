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
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class CreationSyncEvents {
    private static final Map<UUID, AbilitySnapshot> LAST_ABILITIES = new ConcurrentHashMap<>();

    private CreationSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (!CreationUtil.hasCreation(player)) {
            LAST_ABILITIES.remove(player.getUUID());
            return;
        }
        AbilitySnapshot next = AbilitySnapshot.of(player);
        AbilitySnapshot previous = LAST_ABILITIES.get(player.getUUID());
        if (next.equals(previous)) {
            return;
        }
        LAST_ABILITIES.put(player.getUUID(), next);
        syncNow(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreationCatalog.getInstance().rebuildResolved();
            LAST_ABILITIES.put(player.getUUID(), AbilitySnapshot.of(player));
            syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_ABILITIES.put(player.getUUID(), AbilitySnapshot.of(player));
            syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_ABILITIES.put(player.getUUID(), AbilitySnapshot.of(player));
            syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ABILITIES.remove(event.getEntity().getUUID());
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
        LAST_ABILITIES.put(player.getUUID(), AbilitySnapshot.of(player));
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
                CreationUtil.allowsConflictingEnchants(player),
                CreationUtil.sacrificesRequired(),
                CreationUtil.getLipids(player),
                CreationUtil.getMaxLipids(player)
        ));
    }

    private record AbilitySnapshot(long[] bits) {
        AbilitySnapshot {
            bits = bits == null ? new long[0] : bits.clone();
        }

        static AbilitySnapshot of(ServerPlayer player) {
            boolean hasPower = CreationUtil.hasCreation(player);
            BitSet unlocked = new BitSet();
            int index = 0;
            index = appendFlags(player, hasPower, unlocked, index, CreationUtil.GEAR_UNLOCK_ABILITIES);
            index = appendFlags(player, hasPower, unlocked, index, CreationUtil.QUICK_SLOT_ABILITIES);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.CHEMICAL_1);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.CHEMICAL_SPLASH);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.CHEMICAL_LINGER);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.CHEMICAL_TIMING);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.CHEMICAL_POTENCY);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.CHEMICAL_3);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.FLETCHER_ARROW_EFFECTS);
            index = appendFlag(player, hasPower, unlocked, index, CreationUtil.ENCHANT_CONFLICTS);
            index = appendKeys(player, hasPower, unlocked, index, CreationCatalog.getInstance().abilityKeys());
            index = appendKeys(player, hasPower, unlocked, index, CreationEnchantCatalog.getInstance().abilityKeys());
            appendKeys(player, hasPower, unlocked, index, CreationPotionCatalog.getInstance().abilityKeys());
            return new AbilitySnapshot(unlocked.toLongArray());
        }

        private static int appendFlags(ServerPlayer player, boolean hasPower, BitSet bits, int start, String[] keys) {
            int index = start;
            for (String key : keys) {
                index = appendFlag(player, hasPower, bits, index, key);
            }
            return index;
        }

        private static int appendKeys(ServerPlayer player, boolean hasPower, BitSet bits, int start, Set<String> keys) {
            int index = start;
            for (String key : keys) {
                index = appendFlag(player, hasPower, bits, index, key);
            }
            return index;
        }

        private static int appendFlag(ServerPlayer player, boolean hasPower, BitSet bits, int index, String key) {
            if (hasPower && CreationUtil.isAbilityUnlocked(player, key)) {
                bits.set(index);
            }
            return index + 1;
        }
    }
}
