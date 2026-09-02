package com.github.bandithelps.creation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record CreationKnowledgeRecipe(Kind kind, Identifier id) {
    public static final Codec<CreationKnowledgeRecipe> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("kind").forGetter(recipe -> recipe.kind().id()),
                    Identifier.CODEC.fieldOf("id").forGetter(CreationKnowledgeRecipe::id)
            ).apply(instance, (kind, id) -> new CreationKnowledgeRecipe(Kind.fromId(kind), id)));

    public static final StreamCodec<ByteBuf, CreationKnowledgeRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            recipe -> recipe.kind().id(),
            ByteBufCodecs.STRING_UTF8,
            recipe -> recipe.id().toString(),
            (kind, id) -> new CreationKnowledgeRecipe(Kind.fromId(kind), Identifier.parse(id))
    );

    public static CreationKnowledgeRecipe item(Identifier id) {
        return new CreationKnowledgeRecipe(Kind.ITEM, id);
    }

    public static CreationKnowledgeRecipe enchant(Identifier id) {
        return new CreationKnowledgeRecipe(Kind.ENCHANT, id);
    }

    public static CreationKnowledgeRecipe potion(Identifier id) {
        return new CreationKnowledgeRecipe(Kind.POTION, id);
    }

    public enum Kind {
        ITEM,
        ENCHANT,
        POTION;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind fromId(String value) {
            if (value == null || value.isBlank()) {
                return ITEM;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "enchant" -> ENCHANT;
                case "potion" -> POTION;
                default -> ITEM;
            };
        }
    }
}
