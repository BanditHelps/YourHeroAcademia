package com.github.bandithelps.client.renderers.entity.state;

import com.github.bandithelps.utils.blockdisplays.RgbaBlendMode;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RgbaDisplayRenderState extends EntityRenderState {
    public int argb = 0xFFFFFFFF;
    public Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);
    public Quaternionf rotation = new Quaternionf();
    public RgbaBlendMode blendMode = RgbaBlendMode.NORMAL;
}
