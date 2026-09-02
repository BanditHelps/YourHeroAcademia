package com.github.bandithelps.items;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.creation.CreationSyncEvents;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationKnowledgeRecipe;
import com.github.bandithelps.creation.CreationUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.network.OpenScreenPacket;

public class BookOfKnowledgeItem extends Item {
    public static final Identifier SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/book_of_knowledge");
    public static final int CHOICE_COUNT = 3;

    private static final String TAG_ROOT = "yha_book_of_knowledge";
    private static final String TAG_CHOICES = "choices";

    public BookOfKnowledgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!CreationUtil.hasCreation(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("gui.yha.creation.book.cannot_read"));
            return InteractionResult.FAIL;
        }
        CreationCatalog.getInstance().rebuildResolved();
        CreationSyncEvents.syncNow(serverPlayer);
        List<CreationKnowledgeRecipe> choices = stillLocked(serverPlayer, getChoices(stack));
        if (choices.isEmpty()) {
            choices = CreationUtil.rollKnowledgeChoices(serverPlayer, CHOICE_COUNT);
        }
        if (choices.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("gui.yha.creation.book.nothing_to_learn"));
            return InteractionResult.FAIL;
        }
        setChoices(stack, choices);
        PacketDistributor.sendToPlayer(serverPlayer, new OpenScreenPacket(SCREEN_ID));
        return InteractionResult.SUCCESS;
    }

    public static ItemStack findHeld(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (isBook(main)) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return isBook(offhand) ? offhand : ItemStack.EMPTY;
    }

    public static boolean isBook(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(YourHeroAcademia.BOOK_OF_KNOWLEDGE.get());
    }

    public static List<CreationKnowledgeRecipe> getChoices(ItemStack stack) {
        if (!isBook(stack)) {
            return List.of();
        }
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound(TAG_ROOT).orElse(new CompoundTag());
        ListTag list = root.getList(TAG_CHOICES).orElse(new ListTag());
        List<CreationKnowledgeRecipe> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CreationKnowledgeRecipe recipe = parseStored(list.getString(i).orElse(""));
            if (recipe != null) {
                result.add(recipe);
            }
        }
        return result;
    }

    public static void setChoices(ItemStack stack, List<CreationKnowledgeRecipe> choices) {
        if (!isBook(stack)) {
            return;
        }
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        if (choices != null) {
            for (CreationKnowledgeRecipe recipe : choices) {
                if (recipe == null || recipe.id() == null) {
                    continue;
                }
                list.add(StringTag.valueOf(recipe.kind().id() + "|" + recipe.id()));
            }
        }
        root.put(TAG_CHOICES, list);
        customTag.put(TAG_ROOT, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    private static CreationKnowledgeRecipe parseStored(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int split = raw.indexOf('|');
        if (split <= 0 || split >= raw.length() - 1) {
            return null;
        }
        try {
            return new CreationKnowledgeRecipe(
                    CreationKnowledgeRecipe.Kind.fromId(raw.substring(0, split)),
                    Identifier.parse(raw.substring(split + 1))
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static List<CreationKnowledgeRecipe> stillLocked(ServerPlayer player, List<CreationKnowledgeRecipe> stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        List<CreationKnowledgeRecipe> locked = CreationUtil.lockedResearchableRecipes(player);
        List<CreationKnowledgeRecipe> result = new ArrayList<>();
        for (CreationKnowledgeRecipe recipe : stored) {
            if (locked.contains(recipe)) {
                result.add(recipe);
            }
        }
        return result;
    }
}
