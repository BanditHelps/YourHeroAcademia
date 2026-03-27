package com.github.bandithelps.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public record CloudCellPos(int x, int y, int z) {

    public static CloudCellPos fromWorld(Vec3 worldPos, double cellSize, BlockPos origin) {
        int cellX = floorToCell(worldPos.x - origin.getX(), cellSize);
        int cellY = floorToCell(worldPos.y - origin.getY(), cellSize);
        int cellZ = floorToCell(worldPos.z - origin.getZ(), cellSize);
        return new CloudCellPos(cellX, cellY, cellZ);
    }

    private static int floorToCell(double value, double cellSize) {
        return (int) Math.floor(value / cellSize);
    }

    public Vec3 toWorldCenter(BlockPos origin, double cellSize) {
        double worldX = origin.getX() + ((this.x + 0.5D) * cellSize);
        double worldY = origin.getY() + ((this.y + 0.5D) * cellSize);
        double worldZ = origin.getZ() + ((this.z + 0.5D) * cellSize);
        return new Vec3(worldX, worldY, worldZ);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", this.x);
        tag.putInt("y", this.y);
        tag.putInt("z", this.z);
        return tag;
    }

    public static CloudCellPos fromTag(CompoundTag tag) {
        return new CloudCellPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0));
    }
}
