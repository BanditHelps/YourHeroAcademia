package com.github.bandithelps.loot;

import com.github.bandithelps.Config;
import com.github.bandithelps.YourHeroAcademia;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class AddBookOfKnowledgeLootModifier extends LootModifier {
    public static final MapCodec<AddBookOfKnowledgeLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            LootModifier.codecStart(inst).apply(inst, AddBookOfKnowledgeLootModifier::new));

    private static final Set<Identifier> CHEST_TABLES = Set.of(
            Identifier.parse("minecraft:chests/simple_dungeon"),
            Identifier.parse("minecraft:chests/abandoned_mineshaft"),
            Identifier.parse("minecraft:chests/stronghold_library"),
            Identifier.parse("minecraft:chests/desert_pyramid"),
            Identifier.parse("minecraft:chests/jungle_temple"),
            Identifier.parse("minecraft:chests/woodland_mansion"),
            Identifier.parse("minecraft:chests/nether_bridge"),
            Identifier.parse("minecraft:chests/end_city_treasure"),
            Identifier.parse("minecraft:chests/ancient_city"),
            Identifier.parse("minecraft:chests/buried_treasure"),
            Identifier.parse("minecraft:chests/shipwreck_treasure"),
            Identifier.parse("minecraft:chests/shipwreck_map"),
            Identifier.parse("minecraft:chests/shipwreck_supply")
    );

    public AddBookOfKnowledgeLootModifier(LootItemCondition[] conditions, int priority) {
        super(conditions, priority);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Identifier tableId = context.getQueriedLootTableId();
        if (tableId == null || !CHEST_TABLES.contains(tableId)) {
            return generatedLoot;
        }
        if (context.getRandom().nextDouble() >= Config.CREATION_BOOK_OF_KNOWLEDGE_CHANCE.get()) {
            return generatedLoot;
        }
        generatedLoot.add(new ItemStack(YourHeroAcademia.BOOK_OF_KNOWLEDGE.get()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
