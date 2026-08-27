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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        return BodyAttachments.get(player).getCustomFloat(player, BodyPart.CHEST, LIPIDS_KEY, 0.0f);
    }

    public static float getMaxLipids(Player player) {
        if (player == null) {
            return Config.CREATION_MAX_LIPIDS.get();
        }
        Float stored = BodyAttachments.get(player).getCustomFloats(player, BodyPart.CHEST).get(MAX_LIPIDS_KEY);
        if (stored == null || stored <= 0.0f) {
            return Config.CREATION_MAX_LIPIDS.get();
        }
        return stored;
    }

    public static void setLipids(ServerPlayer player, float value) {
        float clamped = Mth.clamp(value, 0.0f, getMaxLipids(player));
        BodyAttachments.get(player).setCustomFloat(player, BodyPart.CHEST, LIPIDS_KEY, clamped);
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
        float efficiency = efficiencyMultiplier(entity);
        int ingotCost = Math.max(1, Mth.ceil(base / efficiency));
        CreationForm resolved = form != null ? form : CreationForm.BASE;
        return resolved.scaledCost(ingotCost);
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

    public static List<CreationEnchantEntry> researchableEnchantEntries(LivingEntity entity) {
        List<CreationEnchantEntry> result = new ArrayList<>();
        for (CreationEnchantEntry entry : CreationEnchantCatalog.getInstance().allEntries()) {
            if (isEnchantResearchable(entity, entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<CreationPotionEntry> researchablePotionEntries(LivingEntity entity) {
        List<CreationPotionEntry> result = new ArrayList<>();
        for (CreationPotionEntry entry : CreationPotionCatalog.getInstance().allEntries()) {
            if (isPotionResearchable(entity, entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static int enchantCost(LivingEntity entity, CreationEnchantEntry entry, int level) {
        if (entry == null || level <= 0) {
            return 0;
        }
        return Math.max(1, Mth.ceil(entry.lipidCostForLevel(level) / efficiencyMultiplier(entity)));
    }

    public static int potionCost(LivingEntity entity, CreationPotionEntry entry, int extraAmplifier, int durationSeconds, CreationPotionForm form) {
        if (entry == null) {
            return Config.CREATION_DEFAULT_LIPID_COST.get();
        }
        return Math.max(1, Mth.ceil(entry.lipidCostFor(extraAmplifier, durationSeconds, form) / efficiencyMultiplier(entity)));
    }

    public static List<CreationEntry> unlockedEntries(Player player, CreationTab tab) {
        CreationData data = CreationAttachments.get(player);
        List<CreationEntry> result = new ArrayList<>();
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            if (entry.tab() == tab && data.isUnlocked(entry.itemId())) {
                result.add(entry);
            }
        }
        return result;
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
        if (!consumeOne(player, entry.stack())) {
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

    public static boolean tryCreate(ServerPlayer player, Identifier itemId) {
        return tryCreate(player, itemId, List.of());
    }

    public static boolean tryCreate(ServerPlayer player, Identifier itemId, List<CreationCreatePayload.EnchantChoice> requested) {
        if (!hasCreation(player) || itemId == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        CreationEntry parent = CreationCatalog.getInstance().parentOf(itemId).orElse(null);
        CreationData data = CreationAttachments.get(player);
        if (parent == null || !parent.isKnownForm(itemId) || !data.isUnlocked(parent.itemId())) {
            return false;
        }
        ItemStack created = CreationCatalog.stackOf(itemId);
        if (created.isEmpty()) {
            return false;
        }
        created = created.copy();
        created.setCount(1);

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
                    return false;
                }
                if (!CreationEnchantments.compatibleWith(player.registryAccess(), enchantId, levels)) {
                    player.sendSystemMessage(Component.translatable("gui.yha.creation.enchant_incompatible"));
                    return false;
                }
                levels.put(enchantId, choice.level());
            }
        }

        int cost = creationCost(player, parent, CreationForm.of(parent, itemId));
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            CreationEnchantEntry enchant = CreationEnchantCatalog.getInstance().get(entry.getKey()).orElse(null);
            cost += enchantCost(player, enchant, entry.getValue());
        }
        if (getLipids(player) + 0.0001f < cost) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.not_enough_lipids"));
            return false;
        }
        setLipids(player, getLipids(player) - cost);
        CreationEnchantments.apply(created, player.registryAccess(), levels);
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
        if (parent == null || !parent.isKnownForm(itemId) || !data.isUnlocked(parent.itemId())) {
            return false;
        }
        ItemStack preview = CreationCatalog.stackOf(itemId);
        if (preview.isEmpty()) {
            return false;
        }
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
            } else if (entry.blockId() != null && data.isUnlocked(entry.blockId())) {
                data.unlock(entry.itemId());
            }
        }
    }

    public static void spawnCreatedItem(ServerLevel level, ServerPlayer player, ItemStack stack) {
        Vec3 origin = randomBodyOffset(player);
        CreationProductEntity product = new CreationProductEntity(level, stack, origin, Config.CREATION_GROW_TICKS.get());
        level.addFreshEntity(product);
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()),
                origin.x, origin.y, origin.z,
                12, 0.15, 0.15, 0.15, 0.04
        );
        level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 6, 0.12, 0.12, 0.12, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    private static Vec3 randomBodyOffset(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        double side = (player.getRandom().nextDouble() - 0.5) * 0.7;
        double forward = (player.getRandom().nextDouble() - 0.35) * 0.35;
        double height = player.getBbHeight() * (0.25 + player.getRandom().nextDouble() * 0.55);
        double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * side;
        double dz = Math.cos(yaw) * forward + Math.sin(yaw) * side;
        return new Vec3(player.getX() + dx, player.getY() + height, player.getZ() + dz);
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
