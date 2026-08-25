package com.github.bandithelps.client.creation;

import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.network.CreationSyncPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ClientCreationState {
    private static CreationSyncPayload latest = new CreationSyncPayload(
            List.of(), List.of(), List.of(), 0, false, 8
    );

    private ClientCreationState() {
    }

    public static void apply(CreationSyncPayload payload) {
        latest = payload;
    }

    public static CreationSyncPayload get() {
        return latest;
    }

    public static List<CreationSyncPayload.ClientEntry> entriesForTab(CreationTab tab, boolean unlockedOnly) {
        List<CreationSyncPayload.ClientEntry> result = new ArrayList<>();
        for (CreationSyncPayload.ClientEntry entry : latest.entries()) {
            if (CreationTab.fromId(entry.tab()) != tab) {
                continue;
            }
            if (unlockedOnly && !entry.unlocked()) {
                continue;
            }
            result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    public static CreationSyncPayload.ClientEntry find(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (CreationSyncPayload.ClientEntry entry : latest.entries()) {
            if (itemId.equals(entry.itemId())) {
                return entry;
            }
        }
        return null;
    }

    public static Identifier quickSlot(int index) {
        List<String> slots = latest.quickSlots();
        if (index < 0 || index >= slots.size()) {
            return null;
        }
        String raw = slots.get(index);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Identifier.parse(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ItemStack stackOf(String itemId) {
        try {
            return CreationCatalog.stackOf(Identifier.parse(itemId));
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
