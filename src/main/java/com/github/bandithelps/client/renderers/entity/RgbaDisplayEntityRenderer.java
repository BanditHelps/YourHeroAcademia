package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.renderers.entity.state.RgbaDisplayRenderState;
import com.github.bandithelps.entities.RgbaDisplayEntity;
import com.github.bandithelps.utils.blockdisplays.RgbaBlendMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

public class RgbaDisplayEntityRenderer extends EntityRenderer<RgbaDisplayEntity, RgbaDisplayRenderState> {
    private static final Identifier TEXTURE = Identifier.parse("minecraft:textures/block/white_concrete.png");
    private static final RenderType RENDER_TYPE_NORMAL = RenderTypes.entityTranslucent(TEXTURE);
    private static final RenderType RENDER_TYPE_ADDITIVE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    public RgbaDisplayEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void submit(RgbaDisplayRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        Quaternionf rotation = state.rotation;
        poseStack.mulPose(rotation);
        var scale = state.scale;
        poseStack.scale(scale.x, scale.y, scale.z);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        int color = state.argb;
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        RenderType renderType = state.blendMode == RgbaBlendMode.ADDITIVE ? RENDER_TYPE_ADDITIVE : RENDER_TYPE_NORMAL;
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            addQuad(buffer, pose, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, r, g, b, a, state.lightCoords); // South
            addQuad(buffer, pose, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 0f, -1f, r, g, b, a, state.lightCoords); // North
            addQuad(buffer, pose, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0f, -1f, 0f, 0f, r, g, b, a, state.lightCoords); // West
            addQuad(buffer, pose, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, r, g, b, a, state.lightCoords); // East
            addQuad(buffer, pose, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, r, g, b, a, state.lightCoords); // Up
            addQuad(buffer, pose, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, -1f, 0f, r, g, b, a, state.lightCoords); // Down
        });

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz,
            int r, int g, int b, int a,
            int packedLight) {
        addVertex(consumer, pose, x1, y1, z1, 0.0f, 1.0f, nx, ny, nz, r, g, b, a, packedLight);
        addVertex(consumer, pose, x2, y2, z2, 1.0f, 1.0f, nx, ny, nz, r, g, b, a, packedLight);
        addVertex(consumer, pose, x3, y3, z3, 1.0f, 0.0f, nx, ny, nz, r, g, b, a, packedLight);
        addVertex(consumer, pose, x4, y4, z4, 0.0f, 0.0f, nx, ny, nz, r, g, b, a, packedLight);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x, float y, float z,
            float u, float v,
            float nx, float ny, float nz,
            int r, int g, int b, int a,
            int packedLight) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public RgbaDisplayRenderState createRenderState() {
        return new RgbaDisplayRenderState();
    }

    @Override
    public void extractRenderState(RgbaDisplayEntity entity, RgbaDisplayRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.argb = entity.getArgbColor();
        state.scale = entity.getScaleVector();
        state.rotation = entity.getRotationQuaternion();
        state.blendMode = entity.getBlendMode();
    }
}
