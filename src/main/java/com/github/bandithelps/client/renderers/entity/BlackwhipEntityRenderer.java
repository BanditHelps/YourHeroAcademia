package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.renderers.entity.state.BlackwhipRenderState;
import com.github.bandithelps.entities.BlackwhipAnchor;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.entities.BlackwhipStyle;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class BlackwhipEntityRenderer extends EntityRenderer<BlackwhipEntity, BlackwhipRenderState> {

    private static final Identifier TEXTURE = Identifier.parse("minecraft:textures/block/white_concrete.png");
    private static final RenderType GLOW_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);
    private static final RenderType CORE_TYPE = RenderTypes.entityTranslucent(TEXTURE);

    /** Packed light value for full-bright rendering (block 15, sky 15). */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Tracks the client tick at which an entity was first seen inactive, to drive the retract animation. */
    private static final Map<Integer, Integer> INACTIVE_SINCE = new HashMap<>();

    public BlackwhipEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    protected boolean affectedByCulling(BlackwhipEntity entity) {
        // Whip geometry can extend far beyond the entity's tiny bounding box, so never cull it.
        return false;
    }

    @Override
    public BlackwhipRenderState createRenderState() {
        return new BlackwhipRenderState();
    }

    @Override
    public void extractRenderState(BlackwhipEntity entity, BlackwhipRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        Level level = entity.level();

        state.style = entity.getStyle();
        state.coreColor = entity.getCoreColor();
        state.glowColor = entity.getGlowColor();
        state.thickness = Math.max(0.05f, entity.getThickness());
        state.curve = entity.getCurve();
        state.jaggedness = entity.getJaggedness();
        state.length = entity.getLength();
        state.strands = Math.max(1, entity.getStrands());
        state.seed = entity.getSeed();
        state.forwardOffset = entity.getForwardOffset();
        state.active = entity.isActive();

        double ex = Mth.lerp(partial, entity.xOld, entity.getX());
        double ey = Mth.lerp(partial, entity.yOld, entity.getY());
        double ez = Mth.lerp(partial, entity.zOld, entity.getZ());
        state.renderOrigin = new Vec3(ex, ey, ez);

        long gameTime = level != null ? level.getGameTime() : entity.tickCount;
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

        Entity owner = entity.getOwnerId() >= 0 && level != null ? level.getEntity(entity.getOwnerId()) : null;
        state.start = resolveAnchor(owner, entity.getAnchor(), partial, state);

        int endMode = entity.getEndMode();
        if (endMode == BlackwhipEntity.END_ENTITY && level != null) {
            Entity target = level.getEntity(entity.getEndEntity());
            if (target != null) {
                state.end = attachPoint(target, partial);
                state.hasEnd = true;
                if (state.style == BlackwhipStyle.TETHER || state.style == BlackwhipStyle.WRAP) {
                    fillWrap(state, target);
                }
            }
        } else if (endMode == BlackwhipEntity.END_POINT) {
            state.end = entity.getEndPoint();
            state.hasEnd = true;
        }
    }

    @Override
    public void submit(BlackwhipRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vector3f camLook = camera.orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        state.camForward = new Vec3(camLook.x(), camLook.y(), camLook.z()).normalize();
        state.camPos = camera.pos;

        List<List<Vec3>> strands = new ArrayList<>();
        switch (state.style) {
            case AURA -> strands.addAll(buildAuraStrands(state));
            case BUBBLE -> strands.addAll(buildBubbleStrands(state));
            case WRAP -> { /* rings only */ }
            default -> {
                List<Vec3> rope = buildRope(state);
                if (rope.size() >= 2) {
                    strands.add(rope);
                }
            }
        }

        if (state.drawWrap) {
            strands.addAll(buildWrapRings(state));
        }

        if (strands.isEmpty()) {
            return;
        }

        float base = Math.max(0.02f, state.thickness * 0.065f);
        float noiseAmp = Math.min(base * 0.75f, 0.08f) * Math.max(0.15f, state.jaggedness * 1.4f);
        float alphaScale = state.active ? 1.0f : (1.0f - state.retractProgress);

        // Outer glow pass (wide, additive/emissive).
        collector.submitCustomGeometry(poseStack, GLOW_TYPE, (pose, buffer) -> {
            for (List<Vec3> pts : strands) {
                drawRibbon(buffer, pose, pts, base * 1.25f, state.glowColor, state, noiseAmp, alphaScale);
            }
        });
        // Inner dark core pass (narrow, translucent).
        collector.submitCustomGeometry(poseStack, CORE_TYPE, (pose, buffer) -> {
            for (List<Vec3> pts : strands) {
                drawRibbon(buffer, pose, pts, base * 0.54f, state.coreColor, state, noiseAmp, alphaScale);
            }
        });
    }

    // ---------------------------------------------------------------------
    // Strand construction
    // ---------------------------------------------------------------------

    private static List<Vec3> buildRope(BlackwhipRenderState state) {
        if (!state.hasEnd) {
            return List.of();
        }
        float reach = state.active ? state.extendProgress : (1.0f - state.retractProgress);
        Vec3 end = state.start.add(state.end.subtract(state.start).scale(reach));
        double len = end.subtract(state.start).length();
        int segments = Math.max(12, (int) Math.min(72, len * 6.0));
        float curve = state.style == BlackwhipStyle.SWING_ROPE ? state.curve * 0.35f : state.curve;
        return buildCurve(state.start, end, curve, segments);
    }

    private static List<List<Vec3>> buildAuraStrands(BlackwhipRenderState state) {
        List<List<Vec3>> out = new ArrayList<>();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 back = state.fwdYaw.scale(-1.0);
        Vec3 right = state.rightYaw;
        Vec3 backBase = state.ownerPos.add(0, state.ownerHeight * 0.5, 0);
        Random rng = new Random(state.seed);
        int n = state.strands;
        float prog = state.active ? state.extendProgress : (1.0f - state.retractProgress);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * (i / (double) Math.max(1, n)) + rng.nextDouble() * 0.9;
            double rx = 0.35 + rng.nextDouble() * 0.15;
            double x = Math.cos(a) * rx * 0.45;
            double y = 0.25 + (rng.nextDouble() - 0.3) * 0.25;
            double z = 0.05 + rng.nextDouble() * 0.12;
            double breathe = 0.05 * Math.sin(0.7 * i + state.time * 0.03);
            Vec3 anchor = backBase
                    .add(back.scale(0.15 + breathe + z))
                    .add(right.scale(x))
                    .add(up.scale(y));
            List<Vec3> pts = buildTendril(anchor, back, right, up, state, i, rng);
            int visible = Math.max(2, (int) Math.round(pts.size() * prog));
            out.add(new ArrayList<>(pts.subList(0, visible)));
        }
        return out;
    }

    private static List<Vec3> buildTendril(Vec3 anchor, Vec3 back, Vec3 right, Vec3 up, BlackwhipRenderState state, int index, Random rng) {
        Vec3 down = up.scale(-1.0);
        double sideBias = (rng.nextDouble() - 0.5) * 0.7;
        Vec3 dir = back.scale(0.55).add(up.scale(0.35)).add(right.scale(sideBias * 0.4)).normalize();
        double length = state.length * (0.85 + 0.3 * rng.nextDouble());
        int segments = Math.max(14, (int) Math.min(48, length * 8.0));
        List<Vec3> pts = new ArrayList<>(segments + 1);
        Vec3 p = anchor;
        Vec3 t = dir;
        double curve = Math.max(0.2, state.curve);
        double droop = 0.8 + 0.4 * rng.nextDouble();
        for (int i = 1; i <= segments; i++) {
            double u = i / (double) segments;
            double step = (length / segments) * (0.85 + 0.3 * u);
            Vec3 n1 = t.cross(up);
            if (n1.lengthSqr() < 1.0e-6) n1 = t.cross(right);
            n1 = n1.normalize();
            Vec3 n2 = t.cross(n1).normalize();
            double w1 = Math.sin(state.time * 0.35 + u * 9.0 + index * 0.7) * state.jaggedness;
            double w2 = Math.cos(state.time * 0.55 + u * 15.0 + index * 1.3) * (state.jaggedness * 0.6);
            Vec3 offset = n1.scale(w1).add(n2.scale(w2));
            Vec3 baseBend = down.scale(droop * curve).add(right.scale(sideBias * 0.7 * curve));
            double uLift = Math.pow(1.0 - u, 1.5);
            Vec3 shoulderPeek = up.scale(0.9 * curve * uLift);
            Vec3 desired = t.add(baseBend.add(shoulderPeek).scale(1.15 * (1.0 - u))).normalize();
            t = desired;
            p = p.add(t.scale(step)).add(offset.scale(0.5 * (1.0 - u)));
            pts.add(p);
        }
        pts.add(0, anchor);
        return pts;
    }

    private static List<List<Vec3>> buildBubbleStrands(BlackwhipRenderState state) {
        List<List<Vec3>> out = new ArrayList<>();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 fwd = state.fwdYaw;
        Vec3 right = state.rightYaw;
        Vec3 eye = state.ownerEye;
        float r = Math.max(0.5f, state.length);
        float forwardEff = Math.max(0.2f, state.forwardOffset);
        double down = Math.max(0.30, Math.min(0.9, state.ownerHeight * 0.35));
        Vec3 center = eye.add(0, -down, 0).add(fwd.scale(forwardEff));
        int n = state.strands;
        double baseArc = (2.0 * Math.PI) / Math.max(1, n);
        float prog = state.active ? state.extendProgress : (1.0f - state.retractProgress);
        Vec3 backBase = state.ownerPos.add(0, state.ownerHeight * 0.47, 0);
        Vec3 backYaw = fwd.scale(-1.0);
        for (int i = 0; i < n; i++) {
            double phi = i * baseArc + Math.sin((state.time * 0.05) + i * 1.7) * 0.06;
            Vec3 equatorDir = right.scale(Math.cos(phi)).add(up.scale(Math.sin(phi))).normalize();
            Vec3 anchor = backBase.add(backYaw.scale(0.38)).add(right.scale(Math.cos(phi) * 0.2)).add(up.scale(0.15));
            List<Vec3> points = new ArrayList<>();
            double thetaStart = Math.PI - 0.25;
            Vec3 backDir = fwd.scale(Math.cos(thetaStart)).add(equatorDir.scale(Math.sin(thetaStart))).normalize();
            Vec3 backContact = center.add(backDir.scale(r * 1.05));
            points.addAll(buildCurve(anchor, backContact, state.curve * 1.5f, 14));
            int seg = Math.max(24, (int) (r * 40));
            for (int s = 1; s <= seg; s++) {
                double tt = s / (double) seg;
                double theta = (1.0 - tt) * thetaStart;
                Vec3 pt = center.add(fwd.scale(Math.cos(theta) * r).add(equatorDir.scale(Math.sin(theta) * r)));
                points.add(pt);
            }
            int visible = Math.max(2, (int) Math.round(points.size() * prog));
            out.add(new ArrayList<>(points.subList(0, visible)));
        }
        return out;
    }

    private static List<List<Vec3>> buildWrapRings(BlackwhipRenderState state) {
        List<List<Vec3>> rings = new ArrayList<>();
        double radius = state.wrapRadius;
        if (radius <= 0) {
            return rings;
        }
        float prog = state.active ? state.extendProgress : (1.0f - state.retractProgress);
        if (prog < 0.5f) {
            return rings; // rings appear once the rope has mostly arrived
        }
        int bands = Math.max(2, Math.min(5, (int) Math.round((state.wrapMaxY - state.wrapMinY) / 0.5)));
        for (int i = 0; i < bands; i++) {
            double t = bands == 1 ? 0.5 : i / (double) (bands - 1);
            double y = state.wrapMinY + (state.wrapMaxY - state.wrapMinY) * t;
            double phase = state.time * 0.12 + i * 1.73;
            double jitter = radius * 0.06 * Math.sin(phase * 1.3);
            rings.add(buildRing(new Vec3(state.wrapCenter.x, y, state.wrapCenter.z), radius + jitter, 48));
        }
        return rings;
    }

    // ---------------------------------------------------------------------
    // Ribbon rendering
    // ---------------------------------------------------------------------

    private static void drawRibbon(VertexConsumer buffer, PoseStack.Pose pose, List<Vec3> points,
                                   float baseHalfWidth, int argb, BlackwhipRenderState state, float noiseAmp, float alphaScale) {
        int n = points.size();
        if (n < 2) {
            return;
        }
        int light = FULL_BRIGHT;
        int a0 = (argb >> 24) & 0xFF;
        int r0 = (argb >> 16) & 0xFF;
        int g0 = (argb >> 8) & 0xFF;
        int b0 = argb & 0xFF;

        Vec3 cf = state.camForward;
        Vec3 origin = state.renderOrigin;

        // Precompute ribbon edge vertices for each point.
        Vec3[] left = new Vec3[n];
        Vec3[] right = new Vec3[n];
        float[] alpha = new float[n];
        int[][] rgb = new int[n][3];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            Vec3 p = points.get(i);
            Vec3 tangent;
            if (i == 0) tangent = points.get(1).subtract(p);
            else if (i == n - 1) tangent = p.subtract(points.get(i - 1));
            else tangent = points.get(i + 1).subtract(points.get(i - 1));
            if (tangent.lengthSqr() < 1e-6) tangent = new Vec3(0, 1, 0);
            tangent = tangent.normalize();

            Vec3 noise = computeNoise(tangent, t, state.time, noiseAmp);
            Vec3 local = p.add(noise).subtract(origin);

            Vec3 normal = cf.cross(tangent);
            if (normal.lengthSqr() < 1e-6) normal = new Vec3(0, 1, 0);
            normal = normal.normalize();

            float halfWidth = (float) (baseHalfWidth * (0.85 + 0.15 * (1.0 - t)));
            left[i] = local.add(normal.scale(halfWidth));
            right[i] = local.add(normal.scale(-halfWidth));

            float speck = speckle(t, state.time);
            float shade = (speck - 0.5f) * 0.4f;
            rgb[i][0] = clampByte(Math.round(r0 * (1.0f + shade)));
            rgb[i][1] = clampByte(Math.round(g0 * (1.0f + shade)));
            rgb[i][2] = clampByte(Math.round(b0 * (1.0f + shade)));
            alpha[i] = (a0 / 255f) * alphaScale;
        }

        for (int i = 0; i < n - 1; i++) {
            emitQuad(buffer, pose, left[i], right[i], right[i + 1], left[i + 1],
                    rgb[i], rgb[i + 1], alpha[i], alpha[i + 1], cf, light);
        }
    }

    private static void emitQuad(VertexConsumer buffer, PoseStack.Pose pose,
                                 Vec3 li, Vec3 ri, Vec3 rj, Vec3 lj,
                                 int[] ci, int[] cj, float ai, float aj, Vec3 normal, int light) {
        // Front winding
        vertex(buffer, pose, li, ci, ai, normal, light, 0f, 0f);
        vertex(buffer, pose, ri, ci, ai, normal, light, 1f, 0f);
        vertex(buffer, pose, rj, cj, aj, normal, light, 1f, 1f);
        vertex(buffer, pose, lj, cj, aj, normal, light, 0f, 1f);
        // Back winding (so the ribbon is visible from both sides)
        vertex(buffer, pose, lj, cj, aj, normal, light, 0f, 1f);
        vertex(buffer, pose, rj, cj, aj, normal, light, 1f, 1f);
        vertex(buffer, pose, ri, ci, ai, normal, light, 1f, 0f);
        vertex(buffer, pose, li, ci, ai, normal, light, 0f, 0f);
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

    // ---------------------------------------------------------------------
    // Geometry + math helpers (ported from legacy renderer)
    // ---------------------------------------------------------------------

    private static List<Vec3> buildCurve(Vec3 start, Vec3 end, float curveAmount, int segments) {
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length < 1.0e-4) {
            List<Vec3> single = new ArrayList<>();
            single.add(start);
            return single;
        }
        dir = dir.scale(1.0 / length);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = up.cross(dir);
        if (side.lengthSqr() < 1.0e-6) side = new Vec3(1, 0, 0).cross(dir);
        side = side.normalize();
        double arc = length * curveAmount * 0.25;
        Vec3 control = start.add(dir.scale(length * 0.5)).add(up.scale(arc * 0.6)).add(side.scale(arc * 0.4));
        List<Vec3> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            pts.add(bezier(start, control, end, t));
        }
        return pts;
    }

    private static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, double t) {
        double it = 1.0 - t;
        double b0 = it * it;
        double b1 = 2 * it * t;
        double b2 = t * t;
        return new Vec3(
                b0 * p0.x + b1 * p1.x + b2 * p2.x,
                b0 * p0.y + b1 * p1.y + b2 * p2.y,
                b0 * p0.z + b1 * p1.z + b2 * p2.z
        );
    }

    private static List<Vec3> buildRing(Vec3 center, double radius, int segments) {
        int seg = Math.max(16, Math.min(96, segments));
        List<Vec3> pts = new ArrayList<>(seg + 1);
        for (int k = 0; k <= seg; k++) {
            double a = (2.0 * Math.PI) * (k / (double) seg);
            pts.add(new Vec3(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius));
        }
        return pts;
    }

    private static Vec3 computeNoise(Vec3 tangent, double t, double time, float amplitude) {
        if (amplitude <= 0.0f) {
            return Vec3.ZERO;
        }
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 n1 = tangent.cross(up);
        if (n1.lengthSqr() < 1.0e-6) n1 = tangent.cross(new Vec3(1, 0, 0));
        n1 = n1.normalize();
        Vec3 n2 = tangent.cross(n1).normalize();
        double a1 = Math.sin(time * 0.7 + t * 10.0) * amplitude;
        double a2 = Math.cos(time * 0.95 + t * 17.0) * (amplitude * 0.7);
        return n1.scale(a1).add(n2.scale(a2));
    }

    private static float speckle(double x, double time) {
        double v1 = Math.sin(x * 13.11 + time * 0.83) * Math.cos(x * 11.73 - time * 1.07);
        double v2 = Math.sin(x * 31.33 - time * 1.71) * Math.cos(x * 27.59 + time * 0.61);
        double v = 0.5 * (v1 * 0.5 + 0.5) + 0.5 * (v2 * 0.5 + 0.5);
        return (float) Mth.clamp(v, 0.0, 1.0);
    }

    private static int clampByte(int v) {
        return Mth.clamp(v, 0, 255);
    }

    // ---------------------------------------------------------------------
    // Anchor / target resolution
    // ---------------------------------------------------------------------

    private static Vec3 attachPoint(Entity target, float partial) {
        return target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0);
    }

    private static void fillWrap(BlackwhipRenderState state, Entity target) {
        AABB bb = target.getBoundingBox();
        double xs = bb.getXsize();
        double zs = bb.getZsize();
        double ys = bb.getYsize();
        if (xs <= 0 || zs <= 0 || ys <= 0) {
            return;
        }
        state.drawWrap = true;
        state.wrapCenter = new Vec3((bb.minX + bb.maxX) * 0.5, 0, (bb.minZ + bb.maxZ) * 0.5);
        state.wrapRadius = Math.max(xs, zs) * 0.5 + 0.08;
        state.wrapMinY = bb.minY + ys * 0.3;
        state.wrapMaxY = bb.minY + ys * 0.8;
    }

    private static Vec3 resolveAnchor(Entity owner, BlackwhipAnchor anchor, float partial, BlackwhipRenderState state) {
        if (!(owner instanceof Player player)) {
            if (owner != null) {
                return owner.getPosition(partial).add(0, owner.getBbHeight() * 0.5, 0);
            }
            return state.renderOrigin;
        }

        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 fwdYaw = Vec3.directionFromRotation(0, yaw).normalize();
        Vec3 rightYaw = fwdYaw.cross(new Vec3(0, 1, 0));
        if (rightYaw.lengthSqr() < 1.0e-6) rightYaw = new Vec3(1, 0, 0);
        rightYaw = rightYaw.normalize();

        state.ownerPos = player.getPosition(partial);
        state.ownerEye = player.getEyePosition(partial);
        state.fwdYaw = fwdYaw;
        state.rightYaw = rightYaw;
        state.ownerHeight = player.getBbHeight();

        double hipHeight = Math.max(0.30, Math.min(0.65, player.getBbHeight() * 0.48));
        double crouch = player.isCrouching() ? -0.18 : 0.0;
        Vec3 hipBase = state.ownerPos.add(0, hipHeight + crouch, 0);

        float side = switch (anchor) {
            case LEFT_HAND -> -1.0f;
            case RIGHT_HAND, RIGHT_HIGH -> 1.0f;
            default -> player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        };

        if (anchor == BlackwhipAnchor.RIGHT_HIGH) {
            return hipBase.add(rightYaw.scale(0.20 * side)).add(0, 0.35, 0).add(fwdYaw.scale(0.60));
        }

        return hipBase.add(rightYaw.scale(0.20 * side)).add(fwdYaw.scale(-0.05));
    }
}
