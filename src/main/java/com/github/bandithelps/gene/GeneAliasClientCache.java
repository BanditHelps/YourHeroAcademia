package com.github.bandithelps.gene;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GeneAliasClientCache {
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();

    private GeneAliasClientCache() {
    }

    public static String getAlias(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return ALIASES.getOrDefault(key, "");
    }

    public static void replaceAll(String[] keys, String[] values) {
        ALIASES.clear();
        applyUpdates(keys, values);
    }

    public static void applyUpdates(String[] keys, String[] values) {
        if (keys == null || values == null) {
            return;
        }
        int count = Math.min(keys.length, values.length);
        for (int i = 0; i < count; i++) {
            String key = keys[i];
            String value = values[i];
            if (key == null || key.isBlank()) {
                continue;
            }
            if (value == null || value.isBlank()) {
                ALIASES.remove(key);
            } else {
                ALIASES.put(key, value);
            }
        }
    }
}
