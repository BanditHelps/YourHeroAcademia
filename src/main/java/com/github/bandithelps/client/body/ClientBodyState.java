package com.github.bandithelps.client.body;

import com.github.bandithelps.capabilities.body.BodyData;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodyPartData;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ClientBodyState {
    private static final EnumMap<BodyPart, BodyPartData> PARTS = new EnumMap<>(BodyPart.class);

    static {
        resetDefaults();
    }

    private ClientBodyState() {
    }

    private static void resetDefaults() {
        PARTS.clear();
        for (BodyPart part : BodyPart.physicalParts()) {
            PARTS.put(part, new BodyPartData());
        }
    }

    public static synchronized void set(CompoundTag dataTag) {
        BodyData data = new BodyData();
        data.loadNBTData(dataTag.copy());
        PARTS.clear();
        for (Map.Entry<BodyPart, BodyPartData> entry : data.getPhysicalPartsView().entrySet()) {
            PARTS.put(entry.getKey(), entry.getValue().copy());
        }
    }

    public static synchronized BodyPartData get(BodyPart physicalPart) {
        BodyPartData data = PARTS.get(physicalPart);
        return data == null ? new BodyPartData() : data.copy();
    }

    public static synchronized Map<BodyPart, BodyPartData> getAll() {
        EnumMap<BodyPart, BodyPartData> copy = new EnumMap<>(BodyPart.class);
        for (Map.Entry<BodyPart, BodyPartData> entry : PARTS.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return Collections.unmodifiableMap(copy);
    }
}
