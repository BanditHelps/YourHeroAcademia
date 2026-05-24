package com.github.bandithelps.utils.gene;

import com.github.bandithelps.gene.GeneAliasSavedData;
import com.github.bandithelps.gene.GeneAliasClientCache;
import com.github.bandithelps.gene.Gene;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class GeneAliasUtil {
    private GeneAliasUtil() {
    }

    public static String getAlias(Level level, String sourceUuid, String geneTypeId) {
        if (sourceUuid == null || sourceUuid.isEmpty() || geneTypeId == null || geneTypeId.isEmpty()) {
            return "";
        }
        String key = makeKey(sourceUuid, geneTypeId);
        if (level instanceof ServerLevel serverLevel) {
            return GeneAliasSavedData.get(serverLevel).getAlias(key);
        }
        return GeneAliasClientCache.getAlias(key);
    }

    public static void setAlias(Level level, String sourceUuid, String geneTypeId, String alias) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (sourceUuid == null || sourceUuid.isEmpty() || geneTypeId == null || geneTypeId.isEmpty()) {
            return;
        }
        String normalizedAlias = alias == null ? "" : alias.trim();
        GeneAliasSavedData.get(serverLevel).setAlias(makeKey(sourceUuid, geneTypeId), normalizedAlias);
    }

    public static Gene applyAlias(Level level, String sourceUuid, Gene gene) {
        if (gene == null) {
            return null;
        }
        String alias = getAlias(level, sourceUuid, gene.getType().getId());
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
}
