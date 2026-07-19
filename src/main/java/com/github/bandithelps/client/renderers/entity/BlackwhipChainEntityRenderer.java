package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.blackwhip.BlackwhipWaistBoneHelper;
import com.github.bandithelps.client.renderers.entity.state.BlackwhipChainRenderState;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.BlackwhipSegmentEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a continuous Blackwhip ribbon through server-driven segment joint positions.
 */
public class BlackwhipChainEntityRenderer extends EntityRenderer<BlackwhipChainEntity, BlackwhipChainRenderState> {

    private static final Identifier TEXTURE = Identifier.parse("minecraft:textures/block/white_concrete.png");
    private static final RenderType GLOW_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);
    private static final RenderType CORE_TYPE = RenderTypes.entityTranslucent(TEXTURE);
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final Map<Integer, Integer> INACTIVE_SINCE = new HashMap<>();

    public BlackwhipChainEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    protected boolean affectedByCulling(BlackwhipChainEntity entity) {
        return false;
    }

    @Override
    public BlackwhipChainRenderState createRenderState() {
        return new BlackwhipChainRenderState();
    }

    @Override
    public void extractRenderState(BlackwhipChainEntity entity, BlackwhipChainRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);

        state.coreColor = entity.getCoreColor();
        state.outerColor = entity.getOuterColor();
        state.glowColor = entity.getGlowColor();
        state.thickness = Math.max(0.05f, entity.getThickness());
        state.active = entity.isActive();
        state.seed = entity.getSeed();
        state.hurtTick = entity.getHurtTick();
        state.ageTicks = entity.tickCount + partial;

        long gameTime = entity.level() != null ? entity.level().getGameTime() : entity.tickCount;
        state.time = gameTime + partial;

        int travel = Math.max(1, entity.getTravelTicks());
        state.extendProgress = Mth.clamp((entity.tickCount + partial) / (float) travel, 0f, 1f);

        if (!state.active) {
            int since = INACTIVE_SINCE.computeIfAbsent(entity.getId(), k -> entity.tickCount);
            float rt = Math.max(1, entity.getRetractTicks());
            state.retractProgress = Mth.clamp((entity.tickCount - since + partial) / rt, 0f, 1f);
        } else {
            INACTIVE_SINCE.remove(entity.getId());
            state.retractProgress = 0f;
        }

        state.joints.clear();
        List<BlackwhipSegmentEntity> segments = entity.collectSegments();
        for (BlackwhipSegmentEntity seg : segments) {
            double sx = Mth.lerp(partial, seg.xOld, seg.getX());
            double sy = Mth.lerp(partial, seg.yOld, seg.getY());
            double sz = Mth.lerp(partial, seg.zOld, seg.getZ());
            state.joints.add(new Vec3(sx, sy, sz));
        }

        // renderOrigin must match the entity PoseStack translation (interpolated entity pos).
        // First-person hand is only a world-space joint lock — using it as renderOrigin shifted
        // the whole ribbon (including the mid-hitbox wrap) down relative to the target.
        double ex = Mth.lerp(partial, entity.xOld, entity.getX());
        double ey = Mth.lerp(partial, entity.yOld, entity.getY());
        double ez = Mth.lerp(partial, entity.zOld, entity.getZ());
        state.renderOrigin = new Vec3(ex, ey, ez);

        // Glue both ends every frame (classic Blackwhip attach pattern): live wrist + live tip.
        // Segment positions only seed mid-rope shape / hitboxes — endpoints are render-authored.
        Entity owner = entity.getOwnerId() >= 0 && entity.level() != null
                ? entity.level().getEntity(entity.getOwnerId()) : null;
        Vec3 wrist = null;
        if (owner != null && state.joints.size() >= 2) {
            wrist = resolveVisualRoot(owner, partial);
            Vec3 seg0 = state.joints.getFirst();
            Vec3 offset = wrist.subtract(seg0);
            for (int i = 0; i < state.joints.size(); i++) {
                state.joints.set(i, state.joints.get(i).add(offset));
            }
        }

        state.hasBoneTip = false;
        state.coilAppended = false;
        LivingEntity target = entity.getTargetLiving();
        if (target != null && state.joints.size() >= 2 && state.active && wrist != null) {
            state.coilAppended = BlackwhipWaistBoneHelper.attachTipAndCoil(
                    state.joints, target, wrist, entity.getWrapTurns(), state.extendProgress, partial);
            var bone = BlackwhipWaistBoneHelper.resolveWaistTipClient(entity.getTargetId(), partial);
            if (bone.isPresent()) {
                state.hasBoneTip = true;
                state.boneTip = bone.get();
            }
        }
    }

    /** Server IK wrist for others / third-person; first-person hand for local camera owner. */
    private static Vec3 resolveVisualRoot(Entity owner, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (owner instanceof LivingEntity living
                && mc.player != null
                && owner.getId() == mc.player.getId()
                && mc.options.getCameraType().isFirstPerson()) {
            return BlackwhipChainAnchors.resolveFirstPersonHand(living, partial);
        }
        return BlackwhipChainAnchors.resolveOwnerWrist(owner, partial);
    }

    @Override
    public void submit(BlackwhipChainRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.joints.size() < 2) {
            return;
        }

        Vector3f camLook = camera.orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        state.camForward = new Vec3(camLook.x(), camLook.y(), camLook.z()).normalize();

        List<Vec3> points = new ArrayList<>(state.joints);
        // Active path already encodes extend (rope tip + coil blend). Only truncate while retracting.
        if (!state.active) {
            float visible = Mth.clamp(1.0f - state.retractProgress, 0.05f, 1.0f);
            int keep = Math.max(2, Math.round((points.size() - 1) * visible) + 1);
            if (keep < points.size()) {
                points = new ArrayList<>(points.subList(0, keep));
            }
        }

        float base = Math.max(0.025f, state.thickness * 0.07f);
        // Pulse thicker briefly after taking damage.
        float hurtPulse = 0.0f;
        if (state.hurtTick > 0) {
            float sinceHurt = state.ageTicks - state.hurtTick;
            if (sinceHurt >= 0 && sinceHurt < 8.0f) {
                hurtPulse = (1.0f - sinceHurt / 8.0f) * 0.35f;
            }
        }
        base *= (1.0f + hurtPulse);

        float alphaScale = state.active ? 1.0f : smooth(1.0f - state.retractProgress);
        float spawnFlash = (float) Math.exp(-state.ageTicks * 0.18f);

        RibbonFrame frame = buildFrame(points, state, Math.min(base * 0.9f, 0.06f));
        if (frame == null) {
            return;
        }

        int glow = state.glowColor;
        int outer = state.outerColor;
        int core = state.coreColor;
        float finalBase = base * 1.1f;
        float finalAlpha = alphaScale * 2;
        float finalFlash = spawnFlash;

        // Base: Full thickness multiplier
        // Edge softness: Higher == Shorter Distance the glow moves

        // Halo = glow, mid body = outer, dark spine = inner/core.
        collector.submitCustomGeometry(poseStack, GLOW_TYPE, (pose, buffer) -> {
            emitLayer(buffer, pose, frame, finalBase * 2.6f, glow,
                    0.16f * finalAlpha * (1.0f + 0.6f * finalFlash), state, 0.0f, 0.8f, finalFlash);
            emitLayer(buffer, pose, frame, finalBase * 1.15f, outer,
                    0.95f * finalAlpha, state, 1.0f, 1.0f, finalFlash);
        });
        collector.submitCustomGeometry(poseStack, CORE_TYPE, (pose, buffer) ->
                emitLayer(buffer, pose, frame, finalBase * 0.5f, core,
                        finalAlpha, state, 0.0f, 1.2f, 0.0f));
    }

    private static final class RibbonFrame {
        final Vec3[] center;
        final Vec3[] normal;
        final float[] t;
        final float[] widthFactor;

        RibbonFrame(Vec3[] center, Vec3[] normal, float[] t, float[] widthFactor) {
            this.center = center;
            this.normal = normal;
            this.t = t;
            this.widthFactor = widthFactor;
        }
    }

    private static RibbonFrame buildFrame(List<Vec3> points, BlackwhipChainRenderState state, float shimmer) {
        int n = points.size();
        if (n < 2) {
            return null;
        }
        Vec3 cf = state.camForward;
        Vec3 origin = state.renderOrigin;
        Vec3[] center = new Vec3[n];
        Vec3[] normal = new Vec3[n];
        float[] tArr = new float[n];
        float[] widthFactor = new float[n];

        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            Vec3 p = points.get(i);
            Vec3 tangent;
            if (i == 0) {
                tangent = points.get(1).subtract(p);
            } else if (i == n - 1) {
                tangent = p.subtract(points.get(i - 1));
            } else {
                tangent = points.get(i + 1).subtract(points.get(i - 1));
            }
            if (tangent.lengthSqr() < 1e-6) {
                tangent = new Vec3(0, 1, 0);
            }
            tangent = tangent.normalize();

            Vec3 noise = computeNoise(tangent, t, state.time, shimmer);
            center[i] = p.add(noise).subtract(origin);

            Vec3 nrm = cf.cross(tangent);
            if (nrm.lengthSqr() < 1e-6) {
                nrm = new Vec3(0, 1, 0);
            }
            normal[i] = nrm.normalize();
            tArr[i] = t;
            widthFactor[i] = 0.30f + 0.70f * (float) Math.pow(1.0 - t, 0.8);
        }
        return new RibbonFrame(center, normal, tArr, widthFactor);
    }

    private static void emitLayer(VertexConsumer buffer, PoseStack.Pose pose, RibbonFrame f,
                                  float halfWidth, int argb, float alphaMul,
                                  BlackwhipChainRenderState state, float flow, float edgeSoftness, float tipBoost) {
        int n = f.center.length;
        if (n < 2) {
            return;
        }
        float baseA = ((argb >> 24) & 0xFF) / 255f;
        int r0 = (argb >> 16) & 0xFF;
        int g0 = (argb >> 8) & 0xFF;
        int b0 = argb & 0xFF;
        Vec3 cf = state.camForward;

        Vec3[] edgeL = new Vec3[n];
        Vec3[] edgeR = new Vec3[n];
        int[][] rgb = new int[n][3];
        float[] centerA = new float[n];

        for (int i = 0; i < n; i++) {
            float t = f.t[i];
            float hw = halfWidth * f.widthFactor[i];
            edgeL[i] = f.center[i].add(f.normal[i].scale(hw));
            edgeR[i] = f.center[i].add(f.normal[i].scale(-hw));

            float bright = 1.0f;
            float white = 0.0f;
            float a = baseA * alphaMul;

            if (flow > 0.0f) {
                double e1 = Math.sin(t * 11.0 - state.time * 0.45 + state.seed);
                double e2 = Math.sin(t * 26.0 - state.time * 0.80 + state.seed * 1.7);
                float energy = (float) Mth.clamp(0.5 + 0.5 * (0.62 * e1 + 0.38 * e2), 0.0, 1.0);
                float hot = (float) Math.pow(energy, 2.4);
                bright = 0.72f + 0.55f * energy;
                white = 0.65f * hot * flow;
                a *= 0.80f + 0.45f * energy;
            }

            float tipZone = Mth.clamp((t - 0.82f) / 0.18f, 0.0f, 1.0f);
            if (tipBoost > 0.0f && tipZone > 0.0f) {
                float s = smooth(tipZone) * tipBoost;
                bright += 0.8f * s;
                white = Math.min(1.0f, white + 0.7f * s);
                a += 0.5f * s * baseA;
            }

            a *= smooth(Mth.clamp(t / 0.06f, 0.0f, 1.0f));
            a *= smooth(Mth.clamp((1.0f - t) / 0.10f, 0.0f, 1.0f));

            rgb[i][0] = mixWhite(clampByte(Math.round(r0 * bright)), white);
            rgb[i][1] = mixWhite(clampByte(Math.round(g0 * bright)), white);
            rgb[i][2] = mixWhite(clampByte(Math.round(b0 * bright)), white);
            centerA[i] = Mth.clamp(a, 0.0f, 1.0f);
        }

        float edgeA = Mth.clamp(1.0f - edgeSoftness, 0.0f, 1.0f);
        for (int i = 0; i < n - 1; i++) {
            softQuad(buffer, pose, cf, FULL_BRIGHT,
                    edgeL[i], f.center[i], edgeL[i + 1], f.center[i + 1],
                    rgb[i], rgb[i + 1], centerA[i] * edgeA, centerA[i], centerA[i + 1] * edgeA, centerA[i + 1]);
            softQuad(buffer, pose, cf, FULL_BRIGHT,
                    f.center[i], edgeR[i], f.center[i + 1], edgeR[i + 1],
                    rgb[i], rgb[i + 1], centerA[i], centerA[i] * edgeA, centerA[i + 1], centerA[i + 1] * edgeA);
        }
    }

    private static void softQuad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 normal, int light,
                                 Vec3 a0, Vec3 b0, Vec3 a1, Vec3 b1,
                                 int[] ci, int[] cj, float aA, float aB, float aC, float aD) {
        vertex(buffer, pose, a0, ci, aA, normal, light, 0f, 0f);
        vertex(buffer, pose, b0, ci, aB, normal, light, 1f, 0f);
        vertex(buffer, pose, b1, cj, aD, normal, light, 1f, 1f);
        vertex(buffer, pose, a1, cj, aC, normal, light, 0f, 1f);
        vertex(buffer, pose, a1, cj, aC, normal, light, 0f, 1f);
        vertex(buffer, pose, b1, cj, aD, normal, light, 1f, 1f);
        vertex(buffer, pose, b0, ci, aB, normal, light, 1f, 0f);
        vertex(buffer, pose, a0, ci, aA, normal, light, 0f, 0f);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 pos, int[] rgb, float alpha,
                               Vec3 normal, int light, float u, float v) {
        buffer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(rgb[0], rgb[1], rgb[2], (int) (Mth.clamp(alpha, 0f, 1f) * 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 computeNoise(Vec3 tangent, double t, double time, float amplitude) {
        if (amplitude <= 0.0f) {
            return Vec3.ZERO;
        }
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 n1 = tangent.cross(up);
        if (n1.lengthSqr() < 1.0e-6) {
            n1 = tangent.cross(new Vec3(1, 0, 0));
        }
        n1 = n1.normalize();
        Vec3 n2 = tangent.cross(n1).normalize();
        double a1 = Math.sin(time * 0.7 + t * 10.0) * amplitude;
        double a2 = Math.cos(time * 0.95 + t * 17.0) * (amplitude * 0.7);
        return n1.scale(a1).add(n2.scale(a2));
    }

    private static int mixWhite(int channel, float white) {
        return clampByte(channel + Math.round((255 - channel) * Mth.clamp(white, 0f, 1f)));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static int clampByte(int v) {
        return Mth.clamp(v, 0, 255);
    }
}
