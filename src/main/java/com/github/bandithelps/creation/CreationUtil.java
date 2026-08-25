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
import java.util.ArrayList;
import java.util.List;
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
    public static final String[] GEAR_UNLOCK_ABILITIES = {
            "cr_know_tool_1", "cr_know_armor_1", "cr_know_weapon_1", "cr_know_fletcher_1"
    };
    public static final String[] QUICK_SLOT_ABILITIES = {
            "cr_quick_slot_1", "cr_quick_slot_2", "cr_quick_slot_3"
    };
    public static final String[] EFFICIENCY_ABILITIES = {
            "cr_efficiency_1", "cr_efficiency_2", "cr_efficiency_3", "cr_efficiency_4"
    };
    public static final float[] EFFICIENCY_MULTIPLIERS = {1.2f, 1.45f, 1.7f, 2.0f};
    public static final String[] KNOWLEDGE_BOOST_ABILITIES = {
            "cr_knowledge_boost_1", "cr_knowledge_boost_2"
    };

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

    public static void setLipids(ServerPlayer player, float value) {
        float clamped = Mth.clamp(value, 0.0f, Config.CREATION_MAX_LIPIDS.get());
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
        int base = entry != null ? entry.lipidCost() : Config.CREATION_DEFAULT_LIPID_COST.get();
        float efficiency = efficiencyMultiplier(entity);
        return Math.max(1, Mth.ceil(base / efficiency));
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

    public static List<CreationEntry> researchableEntries(LivingEntity entity) {
        List<CreationEntry> result = new ArrayList<>();
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            if (isResearchable(entity, entry)) {
                result.add(entry);
            }
        }
        return result;
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

    public static boolean tryCreate(ServerPlayer player, Identifier itemId) {
        if (!hasCreation(player) || itemId == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        CreationEntry entry = CreationCatalog.getInstance().get(itemId).orElse(null);
        CreationData data = CreationAttachments.get(player);
        if (entry == null || !data.isUnlocked(itemId)) {
            return false;
        }
        int cost = creationCost(player, entry);
        if (getLipids(player) + 0.0001f < cost) {
            player.sendSystemMessage(Component.translatable("gui.yha.creation.not_enough_lipids"));
            return false;
        }
        setLipids(player, getLipids(player) - cost);
        ItemStack created = entry.stack().copy();
        created.setCount(1);
        spawnCreatedItem(serverLevel, player, created);
        CreationSyncEvents.syncNow(player);
        return true;
    }

    public static boolean tryAssignQuickSlot(ServerPlayer player, int slot, Identifier itemId) {
        int unlocked = unlockedQuickSlotCount(player);
        if (slot < 0 || slot >= unlocked) {
            return false;
        }
        CreationData data = CreationAttachments.get(player);
        if (itemId != null && !data.isUnlocked(itemId)) {
            return false;
        }
        data.setQuickSlot(slot, itemId);
        CreationSyncEvents.syncNow(player);
        return true;
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
