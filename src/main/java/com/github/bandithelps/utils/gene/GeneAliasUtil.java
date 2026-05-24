package com.github.bandithelps.utils.gene;

import com.github.bandithelps.gene.Gene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class GeneAliasUtil {
    private static final String ROOT_TAG = "yha_gene_aliases";

    private GeneAliasUtil() {
    }

    public static String getAlias(Player player, String sourceUuid, String geneTypeId) {
        if (player == null || sourceUuid == null || sourceUuid.isEmpty() || geneTypeId == null || geneTypeId.isEmpty()) {
            return "";
        }
        CompoundTag root = getRoot(player);
        return root.getString(makeKey(sourceUuid, geneTypeId)).orElse("");
    }

    public static void setAlias(Player player, String sourceUuid, String geneTypeId, String alias) {
        if (player == null || sourceUuid == null || sourceUuid.isEmpty() || geneTypeId == null || geneTypeId.isEmpty()) {
            return;
        }
        CompoundTag root = getRoot(player);
        String key = makeKey(sourceUuid, geneTypeId);
        if (alias == null || alias.isBlank()) {
            root.remove(key);
        } else {
            root.putString(key, alias.trim());
        }
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static Gene applyAlias(Player player, String sourceUuid, Gene gene) {
        if (gene == null) {
            return null;
        }
        String alias = getAlias(player, sourceUuid, gene.getType().getId());
        if (alias.isEmpty()) {
            return gene;
        }
        return new Gene(
                gene.getId(),
                alias,
                gene.getCategory(),
                gene.getType(),
                gene.getDescription(),
                gene.getQuality(),
                gene.getSideEffects()
        );
    }

    private static String makeKey(String sourceUuid, String geneTypeId) {
        return sourceUuid + "|" + geneTypeId;
    }

    private static CompoundTag getRoot(Player player) {
        return player.getPersistentData().getCompound(ROOT_TAG).orElse(new CompoundTag());
    }
}
