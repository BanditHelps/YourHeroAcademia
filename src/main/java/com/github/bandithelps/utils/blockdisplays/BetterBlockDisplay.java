package com.github.bandithelps.utils.blockdisplays;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class BetterBlockDisplay extends Display.BlockDisplay{
    public BetterBlockDisplay(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setBlock(BlockState state) {
        this.getEntityData().set(DATA_BLOCK_STATE_ID, state);
    }

    public void setTranslation(Vector3f translation) { this.getEntityData().set(DATA_TRANSLATION_ID, translation); }

    public void setScale(Vector3f scale) {
        this.getEntityData().set(DATA_SCALE_ID, scale);
    }

    public void setRightRotation(Quaternionf rotation) { this.getEntityData().set(DATA_RIGHT_ROTATION_ID, rotation); }

    public void setLeftRotation(Quaternionf rotation) { this.getEntityData().set(DATA_LEFT_ROTATION_ID, rotation); }

    public void setInterpolation(int interpolation) { this.getEntityData().set(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, interpolation); }

    public void startInterpolation() {
        this.getEntityData().set(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, -1, true);
    }
}
