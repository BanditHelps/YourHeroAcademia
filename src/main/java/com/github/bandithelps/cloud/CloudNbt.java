package com.github.bandithelps.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CloudNbt {
    private static final String KEY_VOLUME_ID = "volumeId";
    private static final String KEY_ORIGIN_X = "originX";
    private static final String KEY_ORIGIN_Y = "originY";
    private static final String KEY_ORIGIN_Z = "originZ";
    private static final String KEY_CELL_SIZE = "cellSize";
    private static final String KEY_MODE = "mode";
    private static final String KEY_TTL = "ttl";
    private static final String KEY_CELLS = "cells";

    private CloudNbt() {
    }

    public static CompoundTag writeSpawn(CloudVolume volume) {
        CompoundTag tag = new CompoundTag();
        writeVolumeHeader(tag, volume);

        ListTag cells = new ListTag();
        for (Map.Entry<CloudCellPos, Float> entry : volume.allCellsView().entrySet()) {
            cells.add(writeCell(entry.getKey(), entry.getValue()));
        }
        tag.put(KEY_CELLS, cells);
        return tag;
    }

    public static CompoundTag writeDelta(CloudVolume volume, List<CloudCellDelta> deltas) {
        CompoundTag tag = new CompoundTag();
        writeVolumeHeader(tag, volume);

        ListTag cells = new ListTag();
        for (CloudCellDelta delta : deltas) {
            cells.add(writeCell(delta.pos(), delta.density()));
        }
        tag.put(KEY_CELLS, cells);
        return tag;
    }

    public static CompoundTag writeRemove(UUID volumeId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_VOLUME_ID, volumeId.toString());
        return tag;
    }

    public static UUID readVolumeId(CompoundTag tag) {
        return UUID.fromString(tag.getString(KEY_VOLUME_ID).orElseThrow());
    }

    public static VolumeHeader readHeader(CompoundTag tag) {
        UUID id = readVolumeId(tag);
        BlockPos origin = new BlockPos(
                tag.getInt(KEY_ORIGIN_X).orElse(0),
                tag.getInt(KEY_ORIGIN_Y).orElse(0),
                tag.getInt(KEY_ORIGIN_Z).orElse(0)
        );
        double cellSize = tag.getDouble(KEY_CELL_SIZE).orElse(1.0D);
        CloudMode mode = parseMode(tag.getString(KEY_MODE).orElse(CloudMode.DIFFUSE.getId()));
        int ttl = tag.getInt(KEY_TTL).orElse(CloudSimConfig.defaultLifetimeTicks());
        return new VolumeHeader(id, origin, cellSize, mode, ttl);
    }

    public static List<CloudCellDelta> readDeltas(CompoundTag tag) {
        List<CloudCellDelta> deltas = new ArrayList<>();
        ListTag cells = tag.getList(KEY_CELLS).orElse(new ListTag());
        for (Tag raw : cells) {
            if (!(raw instanceof CompoundTag cellTag)) {
                continue;
            }
            int x = cellTag.getInt("x").orElse(0);
            int y = cellTag.getInt("y").orElse(0);
            int z = cellTag.getInt("z").orElse(0);
            float density = cellTag.getFloat("density").orElse(0.0F);
            deltas.add(new CloudCellDelta(new CloudCellPos(x, y, z), density));
        }
        return deltas;
    }

    private static void writeVolumeHeader(CompoundTag tag, CloudVolume volume) {
        tag.putString(KEY_VOLUME_ID, volume.id().toString());
        tag.putInt(KEY_ORIGIN_X, volume.origin().getX());
        tag.putInt(KEY_ORIGIN_Y, volume.origin().getY());
        tag.putInt(KEY_ORIGIN_Z, volume.origin().getZ());
        tag.putDouble(KEY_CELL_SIZE, volume.cellSize());
        tag.putString(KEY_MODE, volume.mode().getId());
        tag.putInt(KEY_TTL, volume.ttlTicks());
    }

    private static CompoundTag writeCell(CloudCellPos pos, float density) {
        CompoundTag cell = new CompoundTag();
        cell.putInt("x", pos.x());
        cell.putInt("y", pos.y());
        cell.putInt("z", pos.z());
        cell.putFloat("density", density);
        return cell;
    }

    private static CloudMode parseMode(String id) {
        for (CloudMode mode : CloudMode.values()) {
            if (mode.getId().equals(id)) {
                return mode;
            }
        }
        return CloudMode.DIFFUSE;
    }

    public record VolumeHeader(UUID id, BlockPos origin, double cellSize, CloudMode mode, int ttl) {
    }
}
