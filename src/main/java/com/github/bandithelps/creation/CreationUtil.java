package com.github.bandithelps.creation;

import com.github.bandithelps.Config;
import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.creation.CreationAttachments;
import com.github.bandithelps.capabilities.creation.CreationData;
import com.github.bandithelps.capabilities.creation.CreationSyncEvents;
import com.github.bandithelps.entities.CreationProductEntity;
import com.github.bandithelps.network.CreationCreatePayload;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.ability.AbilityUtil;

public final class CreationUtil {
    public static final Identifier POWER_ID = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation");
    public static final String LIPIDS_KEY = "lipids";
    public static final String MAX_LIPIDS_KEY = "max_lipids";
    public static final String[] GEAR_UNLOCK_ABILITIES = {
            "cr_know_tool_1", "cr_know_armor_1", "cr_know_weapon_1", "cr_know_fletcher_1"
    };
    public static final String[] QUICK_SLOT_ABILITIES = {
            "cr_quick_slot_1", "cr_quick_slot_2", "cr_quick_slot_3",
            "cr_quick_slot_4", "cr_quick_slot_5", "cr_quick_slot_6"
    };
    public static final String[] EFFICIENCY_ABILITIES = {
            "cr_efficiency_1", "cr_efficiency_2", "cr_efficiency_3", "cr_efficiency_4"
    };
    public static final float[] EFFICIENCY_MULTIPLIERS = {1.2f, 1.45f, 1.7f, 2.0f};
    public static final String[] KNOWLEDGE_BOOST_ABILITIES = {
            "cr_knowledge_boost_1", "cr_knowledge_boost_2"
    };
    public static final String CHEMICAL_1 = "cr_know_chemical_1";
    public static final String CHEMICAL_2 = "cr_know_chemical_2";
    public static final String CHEMICAL_3 = "cr_know_chemical_3";
    public static final String CHEMICAL_SPLASH = "cr_know_chemical_splash";
    public static final String CHEMICAL_LINGER = "cr_know_chemical_linger";
    public static final String CHEMICAL_TIMING = "cr_know_chemical_timing";
    public static final String CHEMICAL_POTENCY = "cr_know_chemical_potentcy";
    public static final String CHEMICAL_EXPERIENCE = "cr_know_chemical_experience";
    public static final String FLETCHER_ARROW_EFFECTS = "cr_know_fletcher_arrow_effects";
    public static final String DYE_KNOWLEDGE = "cr_know_dye";
    public static final String ENCHANT_CONFLICTS = "cr_know_enchant_conflicts";
    public static final String ENCHANT_EVOLVE = "cr_know_enchant_evolve";
    public static final String TECH_1 = "cr_know_tech_1";
    public static final int CONFLICTING_ENCHANT_COST_MULTIPLIER = 2;
    public static final int CUSTOM_NAME_MAX_LENGTH = 50;

    private CreationUtil() {
    }

    public static boolean hasCreation(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return PowerUtil.getPowerHandler(entity).getPowerInstance(POWER_ID) != null;
    }

    public static boolean isAbilityUnlocked(LivingEntity entity, String abilityKey) {
        return entity != null && AbilityUtil.isUnlocked(entity, POWER_ID, abilityKey);
    }

    public static boolean isGearTabUnlocked(LivingEntity entity) {
        for (String ability : GEAR_UNLOCK_ABILITIES) {
            if (isAbilityUnlocked(entity, ability)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAlchemyTabUnlocked(LivingEntity entity) {
        return isAbilityUnlocked(entity, CHEMICAL_1);
    }

    public static boolean isTechnologyTabUnlocked(LivingEntity entity) {
        return isAbilityUnlocked(entity, TECH_1);
    }

    public static boolean allowsConflictingEnchants(LivingEntity entity) {
        return isAbilityUnlocked(entity, ENCHANT_CONFLICTS);
    }

    public static boolean allowsEnchantEvolve(LivingEntity entity) {
        return isAbilityUnlocked(entity, ENCHANT_EVOLVE);
    }

    public static boolean evolveEnchantLevels(RandomSource random, Map<Identifier, Integer> levels) {
        if (random == null || levels == null || levels.isEmpty()) {
            return false;
        }
        if (random.nextDouble() < Config.CREATION_ENCHANT_RAINBOW_CHANCE.get()) {
            bumpEnchantLevels(levels);
            return true;
        }
        double evolveChance = Config.CREATION_ENCHANT_EVOLVE_CHANCE.get();
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            if (random.nextDouble() < evolveChance) {
                entry.setValue(entry.getValue() + 1);
            }
        }
        return false;
    }

    private static void bumpEnchantLevels(Map<Identifier, Integer> levels) {
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                entry.setValue(entry.getValue() + 1);
            }
        }
    }

    public static int maxPotionAmplifier(LivingEntity entity) {
        if (isAbilityUnlocked(entity, CHEMICAL_3)) {
            return 2;
        }
        if (isAbilityUnlocked(entity, CHEMICAL_POTENCY)) {
            return 1;
        }
        return 0;
    }

    public static int unlockedQuickSlotCount(LivingEntity entity) {
        int count = 0;
        for (String ability : QUICK_SLOT_ABILITIES) {
            if (isAbilityUnlocked(entity, ability)) {
                count++;
            }
        }
        return count;
    }

    public static float efficiencyMultiplier(LivingEntity entity) {
        float multiplier = 1.0f;
        for (int i = 0; i < EFFICIENCY_ABILITIES.length; i++) {
            if (isAbilityUnlocked(entity, EFFICIENCY_ABILITIES[i])) {
                multiplier = EFFICIENCY_MULTIPLIERS[i];
            }
        }
        return multiplier;
    }

    public static float lipidsFromFood(Player player, float saturation) {
        if (player == null || saturation <= 0.0f) {
            return 0.0f;
        }
        double quirkBonus = 1.0D + QuirkFactorUtil.getQuirkFactor(player) * Config.CREATION_QUIRK_FACTOR_LIPID_BONUS.get();
        return (float) (saturation
                * Config.CREATION_SATURATION_TO_LIPIDS.get()
                * efficiencyMultiplier(player)
                * Math.max(0.0D, quirkBonus));
    }

    public static String sanitizeCustomName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("§", "").replace("\n", "").replace("\r", "").trim();
        if (cleaned.length() > CUSTOM_NAME_MAX_LENGTH) {
            return cleaned.substring(0, CUSTOM_NAME_MAX_LENGTH);
        }
        return cleaned;
    }

    public static float knowledgeBoostChance(LivingEntity entity) {
        float chance = 0.0f;
        for (String ability : KNOWLEDGE_BOOST_ABILITIES) {
            if (isAbilityUnlocked(entity, ability)) {
                chance += 0.25f;
            }
        }
        return Mth.clamp(chance, 0.0f, 1.0f);
    }

    public static float getLipids(Player player) {
        if (player == null) {
            return 0.0f;
        }
        return Mth.clamp(
                BodyAttachments.get(player).getCustomFloat(player, BodyPart.CHEST, LIPIDS_KEY, 0.0f),
                0.0f,
                getMaxLipids(player)
        );
    }

    public static float getMaxLipids(Player player) {
        if (player == null) {
            return Config.CREATION_MAX_LIPIDS.get();
        }
        Float stored = BodyAttachments.get(player).getCustomFloats(player, BodyPart.CHEST).get(MAX_LIPIDS_KEY);
        if (stored == null || stored <= 0.0f) {
            return Config.CREATION_MAX_LIPIDS.get();
        }
        return Math.max(Config.CREATION_MAX_LIPIDS.get(), stored);
    }

    public static void setLipids(ServerPlayer player, float value) {
        float clamped = Mth.clamp(value, 0.0f, getMaxLipids(player));
        BodyAttachments.get(player).setCustomFloat(player, BodyPart.CHEST, LIPIDS_KEY, clamped);
        BodySyncEvents.syncNow(player);
    }

    public static void setMaxLipids(ServerPlayer player, float value) {
        float clamped = Math.max(1.0f, value);
        BodyAttachments.get(player).setCustomFloat(player, BodyPart.CHEST, MAX_LIPIDS_KEY, clamped);
        float lipids = BodyAttachments.get(player).getCustomFloat(player, BodyPart.CHEST, LIPIDS_KEY, 0.0f);
        if (lipids > clamped) {
            BodyAttachments.get(player).setCustomFloat(player, BodyPart.CHEST, LIPIDS_KEY, clamped);
        }
        BodySyncEvents.syncNow(player);
    }

    public static void addLipids(ServerPlayer player, float amount) {
        if (amount <= 0.0f) {
            return;
        }
        setLipids(player, getLipids(player) + amount);
    }

    public static int creationCost(LivingEntity entity, CreationEntry entry) {
        return creationCost(entity, entry, CreationForm.BASE);
    }

    public static int creationCost(LivingEntity entity, CreationEntry entry, CreationForm form) {
        int base = entry != null ? entry.lipidCost() : Config.CREATION_DEFAULT_LIPID_COST.get();
        CreationForm resolved = form != null ? form : CreationForm.BASE;
        return resolved.scaledCost(Math.max(1, base));
    }

    public static int sacrificesRequired() {
        return Config.CREATION_RESEARCH_SACRIFICES.get();
    }

    public static int sacrificesRequired(CreationEntry entry) {
        return entry != null ? entry.researchCost() : sacrificesRequired();
    }

    public static boolean isResearchable(LivingEntity entity, CreationEntry entry) {
        return entry != null && isAbilityUnlocked(entity, entry.abilityKey());
    }

    public static boolean isEnchantResearchable(LivingEntity entity, CreationEnchantEntry entry) {
        return entry != null && isAbilityUnlocked(entity, entry.abilityKey());
    }

    public static boolean isPotionResearchable(LivingEntity entity, CreationPotionEntry entry) {
        return entry != null && isAbilityUnlocked(entity, entry.abilityKey());
    }

    public static List<CreationEntry> researchableEntries(LivingEntity entity) {
        List<CreationEntry> result = new ArrayList<>();
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            if (isResearchable(entity, entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static int enchantCost(LivingEntity entity, CreationEnchantEntry entry, int level, boolean conflicting) {
        if (entry == null || level <= 0) {
            return 0;
        }
        int cost = Math.max(1, entry.lipidCostForLevel(level));
        if (conflicting) {
            return cost * CONFLICTING_ENCHANT_COST_MULTIPLIER;
        }
        return cost;
    }

    public static int potionCost(LivingEntity entity, CreationPotionEntry entry, int extraAmplifier, int durationSeconds, CreationPotionForm form) {
        if (entry == null) {
            return Config.CREATION_DEFAULT_LIPID_COST.get();
        }
        return Math.max(1, entry.lipidCostFor(extraAmplifier, durationSeconds, form));
    }

    public static boolean trySacrifice(ServerPlayer player, Identifier itemId) {
        if (!hasCreation(player) || itemId == null) {
            return false;
        }
        CreationEntry entry = CreationCatalog.getInstance().get(itemId).orElse(null);
        if (!isResearchable(player, entry)) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        if (data.isUnlocked(itemId)) {
            return false;
        }
        boolean consumed = entry.isWoodUnlock()
                ? consumeAny(player, entry.familyIds())
                : consumeOne(player, entry.stack());
        if (!consumed) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.missing_sacrifice"));
            return false;
        }
        int gained = 1;
        if (player.getRandom().nextFloat() < knowledgeBoostChance(player)) {
            gained++;
        }
        int next = data.getProgress(itemId) + gained;
        int required = sacrificesRequired(entry);
        if (next >= required) {
            data.unlock(itemId);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
            player.sendSystemMessage(Component.translatable("gui.yha.creation.researched", entry.stack().getHoverName()));
        } else {
            data.setProgress(itemId, next);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 0.7f);
        }
        CreationSyncEvents.syncNow(player);
        return true;
    }

    public static boolean trySacrificeEnchant(ServerPlayer player, Identifier enchantId) {
        if (!hasCreation(player) || enchantId == null) {
            return false;
        }
        CreationEnchantEntry selected = CreationEnchantCatalog.getInstance().get(enchantId).orElse(null);
        if (!isEnchantResearchable(player, selected)) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        if (data.isEnchantUnlocked(enchantId)) {
            return false;
        }
        ItemStack book = CreationEnchantments.consumeBookContaining(player, enchantId);
        if (book.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.missing_enchant_book"));
            return false;
        }
        int gained = 1;
        if (player.getRandom().nextFloat() < knowledgeBoostChance(player)) {
            gained++;
        }
        boolean learnedAny = false;
        boolean progressed = false;
        for (Identifier id : CreationEnchantments.storedEnchantIds(book)) {
            CreationEnchantEntry entry = CreationEnchantCatalog.getInstance().get(id).orElse(null);
            if (!isEnchantResearchable(player, entry) || data.isEnchantUnlocked(id)) {
                continue;
            }
            int next = data.getEnchantProgress(id) + gained;
            if (next >= entry.researchCost()) {
                data.unlockEnchant(id);
                learnedAny = true;
                player.sendSystemMessage(Component.translatable(
                        "gui.yha.creation.researched",
                        CreationEnchantments.displayName(player.registryAccess(), id)));
            } else {
                data.setEnchantProgress(id, next);
                progressed = true;
            }
        }
        if (learnedAny) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        } else if (progressed) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 0.7f);
        }
        CreationSyncEvents.syncNow(player);
        return learnedAny || progressed;
    }

    public static boolean trySacrificePotion(ServerPlayer player, Identifier effectId) {
        if (!hasCreation(player) || effectId == null) {
            return false;
        }
        CreationPotionEntry entry = CreationPotionCatalog.getInstance().get(effectId).orElse(null);
        if (!isPotionResearchable(player, entry)) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        if (data.isPotionUnlocked(effectId)) {
            return false;
        }
        if (!CreationPotions.consumeOneContaining(player, effectId)) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.missing_potion"));
            return false;
        }
        return grantPotionProgress(player, entry, data);
    }

    public static boolean tryProgressPotionFromEffect(ServerPlayer player, Identifier effectId) {
        if (!hasCreation(player) || effectId == null || !isAbilityUnlocked(player, CHEMICAL_EXPERIENCE)) {
            return false;
        }
        CreationPotionEntry entry = CreationPotionCatalog.getInstance().get(effectId).orElse(null);
        if (!isPotionResearchable(player, entry)) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        if (data.isPotionUnlocked(effectId)) {
            return false;
        }
        float chance = entry.experientialChance() != null
                ? entry.experientialChance()
                : Config.CREATION_EXPERIENTIAL_RESEARCH_CHANCE.get().floatValue();
        if (player.getRandom().nextFloat() >= chance) {
            return false;
        }
        return grantPotionProgress(player, entry, data);
    }

    public static boolean tryCreatePotion(
            ServerPlayer player,
            Identifier effectId,
            CreationPotionForm form,
            int durationTicks,
            int amplifier
    ) {
        if (!hasCreation(player) || effectId == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        CreationPotionEntry entry = CreationPotionCatalog.getInstance().get(effectId).orElse(null);
        CreationData data = CreationAttachments.get(player);
        if (!isPotionResearchable(player, entry) || !data.isPotionUnlocked(effectId)) {
            return false;
        }
        CreationPotionForm resolved = form != null ? form : CreationPotionForm.DRINKABLE;
        if (resolved == CreationPotionForm.SPLASH && !isAbilityUnlocked(player, CHEMICAL_SPLASH)) {
            return false;
        }
        if (resolved == CreationPotionForm.LINGERING && !isAbilityUnlocked(player, CHEMICAL_LINGER)) {
            return false;
        }
        if (resolved == CreationPotionForm.ARROW && !isAbilityUnlocked(player, FLETCHER_ARROW_EFFECTS)) {
            return false;
        }
        boolean instant = CreationPotions.isInstant(effectId, entry.instantOverride());
        int maxAmplifier = maxPotionAmplifier(player);
        int clampedAmplifier = Mth.clamp(amplifier, 0, maxAmplifier);
        int defaultTicks = CreationPotionEntry.DEFAULT_DURATION_SECONDS * 20;
        int clampedTicks;
        if (instant) {
            clampedTicks = 1;
        } else if (!isAbilityUnlocked(player, CHEMICAL_TIMING)) {
            clampedTicks = defaultTicks;
        } else {
            int maxTicks = Math.max(20, entry.maxDurationSeconds() * 20);
            clampedTicks = Mth.clamp(durationTicks, 20, maxTicks);
        }
        ItemStack created = CreationPotions.stackOf(effectId, resolved, clampedTicks, clampedAmplifier);
        if (created.isEmpty()) {
            return false;
        }
        int cost = potionCost(player, entry, clampedAmplifier, Math.max(1, clampedTicks / 20), resolved);
        if (getLipids(player) + 0.0001f < cost) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.not_enough_lipids"));
            return false;
        }
        setLipids(player, getLipids(player) - cost);
        spawnCreatedItem(serverLevel, player, created);
        CreationSyncEvents.syncNow(player);
        return true;
    }

    private static boolean grantPotionProgress(ServerPlayer player, CreationPotionEntry entry, CreationData data) {
        int gained = 1;
        if (player.getRandom().nextFloat() < knowledgeBoostChance(player)) {
            gained++;
        }
        Identifier effectId = entry.effectId();
        int next = data.getPotionProgress(effectId) + gained;
        Component name = potionDisplayName(effectId);
        if (next >= entry.researchCost()) {
            data.unlockPotion(effectId);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
            player.sendSystemMessage(Component.translatable("gui.yha.creation.researched", name));
        } else {
            data.setPotionProgress(effectId, next);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 0.7f);
        }
        CreationSyncEvents.syncNow(player);
        return true;
    }

    public static Component potionDisplayName(Identifier effectId) {
        return CreationPotions.displayName(effectId);
    }

    private static boolean canCreateKnownItem(ServerPlayer player, CreationData data, CreationEntry parent, Identifier itemId) {
        if (parent == null || itemId == null || data == null || !parent.isKnownForm(itemId) || !data.isUnlocked(parent.itemId())) {
            return false;
        }
        if (parent.isWoodUnlock()) {
            return CreationWoodTypes.isWoodKnown(data.unlockedView(), itemId);
        }
        if (parent.isUnlockVariant(itemId) && !isAbilityUnlocked(player, parent.unlockAbility())) {
            return false;
        }
        return true;
    }

    public static boolean tryCreate(ServerPlayer player, Identifier itemId, List<CreationCreatePayload.EnchantChoice> requested) {
        return tryCreate(player, itemId, requested, "");
    }

    public static boolean tryCreate(
            ServerPlayer player,
            Identifier itemId,
            List<CreationCreatePayload.EnchantChoice> requested,
            String customName
    ) {
        if (!hasCreation(player) || itemId == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        CreationEntry parent = CreationCatalog.getInstance().parentOf(itemId).orElse(null);
        CreationData data = CreationAttachments.get(player);
        if (!canCreateKnownItem(player, data, parent, itemId)) {
            return false;
        }
        ItemStack created = CreationCatalog.stackOf(itemId);
        if (created.isEmpty()) {
            return false;
        }
        created = created.copy();
        created.setCount(1);
        applyRandomFirework(created, player);

        LinkedHashMap<Identifier, Integer> levels = new LinkedHashMap<>();
        if (requested != null) {
            for (CreationCreatePayload.EnchantChoice choice : requested) {
                if (choice == null || choice.enchantId() == null || choice.enchantId().isBlank() || choice.level() <= 0) {
                    continue;
                }
                Identifier enchantId;
                try {
                    enchantId = Identifier.parse(choice.enchantId());
                } catch (RuntimeException ignored) {
                    return false;
                }
                CreationEnchantEntry entry = CreationEnchantCatalog.getInstance().get(enchantId).orElse(null);
                if (!isEnchantResearchable(player, entry) || !data.isEnchantUnlocked(enchantId)) {
                    return false;
                }
                int maxLevel = entry.resolvedMaxLevel(
                        CreationEnchantments.vanillaMaxLevel(player.registryAccess(), enchantId));
                if (choice.level() > maxLevel) {
                    return false;
                }
                if (!CreationEnchantments.canEnchant(player.registryAccess(), enchantId, created)) {
                    continue;
                }
                if (!allowsConflictingEnchants(player)
                        && !CreationEnchantments.compatibleWith(player.registryAccess(), enchantId, levels)) {
                    player.sendSystemMessage(Component.translatable("gui.yha.creation.enchant_incompatible"));
                    return false;
                }
                levels.put(enchantId, choice.level());
            }
        }

        int cost = creationCost(player, parent, CreationForm.of(parent, itemId));
        boolean allowConflicts = allowsConflictingEnchants(player);
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            CreationEnchantEntry enchant = CreationEnchantCatalog.getInstance().get(entry.getKey()).orElse(null);
            boolean conflicting = allowConflicts
                    && CreationEnchantments.conflictsWithAny(player.registryAccess(), entry.getKey(), levels);
            cost += enchantCost(player, enchant, entry.getValue(), conflicting);
        }
        if (getLipids(player) + 0.0001f < cost) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.not_enough_lipids"));
            return false;
        }
        setLipids(player, getLipids(player) - cost);
        boolean rainbow = allowsEnchantEvolve(player) && !levels.isEmpty()
                && evolveEnchantLevels(player.getRandom(), levels);
        CreationEnchantments.apply(created, player.registryAccess(), levels);
        if (rainbow) {
            applyRainbowName(created, customName);
        } else {
            applyCustomName(created, customName);
        }
        spawnCreatedItem(serverLevel, player, created);
        CreationSyncEvents.syncNow(player);
        return true;
    }

    public static boolean tryAssignQuickSlot(ServerPlayer player, int slot, String encoded) {
        CreationQuickSlot recipe = encoded == null || encoded.isBlank() ? null : CreationQuickSlot.parse(encoded);
        return tryAssignQuickSlot(player, slot, recipe);
    }

    public static boolean tryAssignQuickSlot(ServerPlayer player, int slot, CreationQuickSlot recipe) {
        int unlocked = unlockedQuickSlotCount(player);
        if (slot < 0 || slot >= unlocked) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        if (recipe != null && !isValidQuickSlotRecipe(player, data, recipe)) {
            return false;
        }
        data.setQuickSlot(slot, recipe);
        CreationSyncEvents.syncNow(player);
        return true;
    }

    public static boolean tryActivateQuickSlot(ServerPlayer player, int slot) {
        if (!hasCreation(player) || slot < 0 || slot >= unlockedQuickSlotCount(player)) {
            return false;
        }
        CreationQuickSlot recipe = CreationAttachments.get(player).getQuickSlot(slot);
        if (recipe == null) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.quick_slot_empty"));
            return false;
        }
        if (recipe.isPotion()) {
            return tryCreatePotion(player, recipe.id(), recipe.form(), recipe.durationTicks(), recipe.amplifier());
        }
        return tryCreate(player, recipe.id(), recipe.enchants());
    }

    private static boolean isValidQuickSlotRecipe(ServerPlayer player, CreationData data, CreationQuickSlot recipe) {
        if (recipe.isPotion()) {
            Identifier effectId = recipe.id();
            CreationPotionEntry entry = CreationPotionCatalog.getInstance().get(effectId).orElse(null);
            if (!isPotionResearchable(player, entry) || !data.isPotionUnlocked(effectId)) {
                return false;
            }
            CreationPotionForm form = recipe.form();
            if (form == CreationPotionForm.SPLASH && !isAbilityUnlocked(player, CHEMICAL_SPLASH)) {
                return false;
            }
            if (form == CreationPotionForm.LINGERING && !isAbilityUnlocked(player, CHEMICAL_LINGER)) {
                return false;
            }
            if (form == CreationPotionForm.ARROW && !isAbilityUnlocked(player, FLETCHER_ARROW_EFFECTS)) {
                return false;
            }
            if (recipe.amplifier() > maxPotionAmplifier(player)) {
                return false;
            }
            return true;
        }
        Identifier itemId = recipe.id();
        CreationEntry parent = CreationCatalog.getInstance().parentOf(itemId).orElse(null);
        if (!canCreateKnownItem(player, data, parent, itemId)) {
            return false;
        }
        ItemStack preview = CreationCatalog.stackOf(itemId);
        if (preview.isEmpty()) {
            return false;
        }
        LinkedHashMap<Identifier, Integer> assignedEnchants = new LinkedHashMap<>();
        for (CreationCreatePayload.EnchantChoice choice : recipe.enchants()) {
            if (choice == null || choice.enchantId() == null || choice.enchantId().isBlank() || choice.level() <= 0) {
                continue;
            }
            Identifier enchantId;
            try {
                enchantId = Identifier.parse(choice.enchantId());
            } catch (RuntimeException ignored) {
                return false;
            }
            CreationEnchantEntry entry = CreationEnchantCatalog.getInstance().get(enchantId).orElse(null);
            if (!isEnchantResearchable(player, entry) || !data.isEnchantUnlocked(enchantId)) {
                return false;
            }
            int maxLevel = entry.resolvedMaxLevel(
                    CreationEnchantments.vanillaMaxLevel(player.registryAccess(), enchantId));
            if (choice.level() > maxLevel) {
                return false;
            }
            if (!CreationEnchantments.canEnchant(player.registryAccess(), enchantId, preview)) {
                return false;
            }
            if (!allowsConflictingEnchants(player)
                    && !CreationEnchantments.compatibleWith(player.registryAccess(), enchantId, assignedEnchants)) {
                return false;
            }
            assignedEnchants.put(enchantId, choice.level());
        }
        return true;
    }

    public static void migrateFormUnlocks(CreationData data) {
        if (data == null) {
            return;
        }
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            if (data.isUnlocked(entry.itemId())) {
                continue;
            }
            if (entry.nuggetId() != null && data.isUnlocked(entry.nuggetId())) {
                data.unlock(entry.itemId());
                continue;
            }
            if (entry.blockId() != null && data.isUnlocked(entry.blockId())) {
                data.unlock(entry.itemId());
                continue;
            }
            if (!entry.isWoodUnlock()) {
                continue;
            }
            boolean variantUnlocked = false;
            int combinedProgress = data.getProgress(entry.itemId());
            for (Identifier variantId : entry.unlockVariantIds()) {
                if (data.isUnlocked(variantId)) {
                    variantUnlocked = true;
                    break;
                }
                combinedProgress += data.getProgress(variantId);
            }
            if (variantUnlocked || combinedProgress >= entry.researchCost()) {
                data.unlock(entry.itemId());
            } else if (combinedProgress > data.getProgress(entry.itemId())) {
                data.setProgress(entry.itemId(), combinedProgress);
            }
        }
    }

    private static void applyCustomName(ItemStack stack, String customName) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String cleaned = sanitizeCustomName(customName);
        if (cleaned.isEmpty()) {
            return;
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(cleaned));
    }

    private static void applyRainbowName(ItemStack stack, String customName) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String cleaned = sanitizeCustomName(customName);
        if (cleaned.isEmpty()) {
            cleaned = stack.getHoverName().getString();
        }
        if (cleaned.isEmpty()) {
            return;
        }
        stack.set(DataComponents.CUSTOM_NAME, rainbowComponent(cleaned));
    }

    public static Component rainbowComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        MutableComponent result = Component.empty();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            float hue = length <= 1 ? 0.83f : (float) i / (length - 1);
            int rgb = Mth.hsvToRgb(hue, 1.0f, 1.0f) & 0xFFFFFF;
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }

    public static List<CreationKnowledgeRecipe> lockedResearchableRecipes(ServerPlayer player) {
        if (player == null || !hasCreation(player)) {
            return List.of();
        }
        CreationData data = CreationAttachments.get(player);
        List<CreationKnowledgeRecipe> result = new ArrayList<>();
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            if (isResearchable(player, entry) && !data.isUnlocked(entry.itemId())) {
                result.add(CreationKnowledgeRecipe.item(entry.itemId()));
            }
        }
        for (CreationEnchantEntry entry : CreationEnchantCatalog.getInstance().allEntries()) {
            if (isEnchantResearchable(player, entry) && !data.isEnchantUnlocked(entry.enchantId())) {
                result.add(CreationKnowledgeRecipe.enchant(entry.enchantId()));
            }
        }
        for (CreationPotionEntry entry : CreationPotionCatalog.getInstance().allEntries()) {
            if (isPotionResearchable(player, entry) && !data.isPotionUnlocked(entry.effectId())) {
                result.add(CreationKnowledgeRecipe.potion(entry.effectId()));
            }
        }
        return result;
    }

    public static List<CreationKnowledgeRecipe> rollKnowledgeChoices(ServerPlayer player, int count) {
        List<CreationKnowledgeRecipe> pool = new ArrayList<>(lockedResearchableRecipes(player));
        if (pool.isEmpty() || count <= 0) {
            return List.of();
        }
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = player.getRandom().nextInt(i + 1);
            Collections.swap(pool, i, j);
        }
        int limit = Math.min(count, pool.size());
        return List.copyOf(pool.subList(0, limit));
    }

    public static boolean tryLearnKnowledgeRecipe(ServerPlayer player, CreationKnowledgeRecipe recipe) {
        if (player == null || recipe == null || recipe.id() == null || !hasCreation(player)) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        Component name;
        switch (recipe.kind()) {
            case ENCHANT -> {
                CreationEnchantEntry entry = CreationEnchantCatalog.getInstance().get(recipe.id()).orElse(null);
                if (!isEnchantResearchable(player, entry) || data.isEnchantUnlocked(recipe.id())) {
                    return false;
                }
                data.unlockEnchant(recipe.id());
                name = CreationEnchantments.displayName(player.registryAccess(), recipe.id());
            }
            case POTION -> {
                CreationPotionEntry entry = CreationPotionCatalog.getInstance().get(recipe.id()).orElse(null);
                if (!isPotionResearchable(player, entry) || data.isPotionUnlocked(recipe.id())) {
                    return false;
                }
                data.unlockPotion(recipe.id());
                name = potionDisplayName(recipe.id());
            }
            default -> {
                CreationEntry entry = CreationCatalog.getInstance().get(recipe.id()).orElse(null);
                if (!isResearchable(player, entry) || data.isUnlocked(recipe.id())) {
                    return false;
                }
                data.unlock(recipe.id());
                name = CreationCatalog.stackOf(recipe.id()).getHoverName();
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.sendSystemMessage(Component.translatable("gui.yha.creation.researched", name));
        CreationSyncEvents.syncNow(player);
        return true;
    }

    public static void spawnCreatedItem(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int slot = CreationGrowthAnchors.nextSlot(level, player);
        float jitterSide = (player.getRandom().nextFloat() - 0.5f) * 0.16f;
        float jitterUp = (player.getRandom().nextFloat() - 0.5f) * 0.12f;
        CreationProductEntity product = new CreationProductEntity(
                level, player, stack, slot, jitterSide, jitterUp, Config.CREATION_GROW_TICKS.get());
        level.addFreshEntity(product);
        Vec3 origin = product.position();
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()),
                origin.x, origin.y, origin.z,
                12, 0.15, 0.15, 0.15, 0.04
        );
        level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 6, 0.12, 0.12, 0.12, 0.01);
        Vec3 chest = CreationGrowthAnchors.visualPos(player, CreationGrowthAnchors.SLOT_CHEST, 0.0f, 0.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 4, 0.08, 0.08, 0.08, 0.02);
        if (slot != CreationGrowthAnchors.SLOT_CHEST) {
            level.sendParticles(ParticleTypes.CLOUD, chest.x, chest.y, chest.z, 3, 0.08, 0.06, 0.08, 0.01);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    private static void applyRandomFirework(ItemStack stack, ServerPlayer player) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.FIREWORK_ROCKET) {
            return;
        }
        var random = player.getRandom();
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        DyeColor[] dyes = DyeColor.values();
        int colorCount = 2 + random.nextInt(4);
        IntArrayList colors = new IntArrayList(colorCount);
        for (int i = 0; i < colorCount; i++) {
            colors.add(dyes[random.nextInt(dyes.length)].getFireworkColor());
        }
        FireworkExplosion explosion = new FireworkExplosion(
                shapes[random.nextInt(shapes.length)],
                colors,
                new IntArrayList(),
                random.nextBoolean(),
                random.nextBoolean()
        );
        int flight = 1 + random.nextInt(2);
        stack.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of(explosion)));
    }

    private static boolean consumeAny(ServerPlayer player, List<Identifier> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        for (Identifier itemId : itemIds) {
            if (consumeOne(player, CreationCatalog.stackOf(itemId))) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeOne(ServerPlayer player, ItemStack match) {
        if (match.isEmpty()) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && slot.is(match.getItem())) {
                slot.shrink(1);
                return true;
            }
        }
        return false;
    }
}
