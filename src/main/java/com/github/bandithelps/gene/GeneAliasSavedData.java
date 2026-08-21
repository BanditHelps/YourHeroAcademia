package com.github.bandithelps.gene;

import com.github.bandithelps.YourHeroAcademia;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GeneAliasSavedData extends SavedData {
    public static final SavedDataType<GeneAliasSavedData> ID = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_aliases"),
            GeneAliasSavedData::new,
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .xmap(GeneAliasSavedData::new, GeneAliasSavedData::aliases)
    );

    private final Map<String, String> aliases = new HashMap<>();

    public static GeneAliasSavedData get(ServerLevel level) {
        return level.getServer().getDataStorage().computeIfAbsent(ID);
    }

    public GeneAliasSavedData() {
    }

    private GeneAliasSavedData(Map<String, String> aliases) {
        if (aliases == null) {
            return;
        }
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                this.aliases.put(key, value);
            }
        }
    }

    public String getAlias(String key) {
        return aliases.getOrDefault(key, "");
    }

    public void setAlias(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null || value.isBlank()) {
            aliases.remove(key);
        } else {
            aliases.put(key, value);
        }
        setDirty();
    }

    public Map<String, String> getAliasesView() {
        return Collections.unmodifiableMap(aliases);
    }

    private Map<String, String> aliases() {
        return this.aliases;
    }
}
