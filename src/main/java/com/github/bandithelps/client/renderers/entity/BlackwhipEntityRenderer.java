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

        state.ageTicks = entity.tickCount + partial;

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

        boolean ropeStyle;
        List<List<Vec3>> strands = new ArrayList<>();
        switch (state.style) {
            case AURA -> { strands.addAll(buildAuraStrands(state)); ropeStyle = false; }
            case BUBBLE -> { strands.addAll(buildBubbleStrands(state)); ropeStyle = false; }
            case WRAP -> ropeStyle = false;
            default -> {
                List<Vec3> rope = buildRope(state);
                if (rope.size() >= 2) {
                    strands.add(rope);
                }
                ropeStyle = true;
            }
        }

        boolean wrapStyle = false;
        if (state.drawWrap) {
            strands.addAll(buildWrapRings(state));
            wrapStyle = true;
        }

        if (strands.isEmpty()) {
            return;
        }

        // Build a camera-facing frame for each strand once, with a touch of organic shimmer baked in.
        // The frame is shared by every layer so the dark core, glow body and halo stay perfectly aligned.
        float base = Math.max(0.025f, state.thickness * 0.07f);
        float shimmer = Math.min(base * 0.9f, 0.06f) * Math.max(0.1f, state.jaggedness);
        float alphaScale = state.active ? 1.0f : smooth(1.0f - state.retractProgress);
        // Energy intensifies briefly on spawn (the "crack" flash) then settles to a steady glow.
        float spawnFlash = (float) Math.exp(-state.ageTicks * 0.18f);
        TaperMode taper = ropeStyle ? TaperMode.WHIP : (wrapStyle ? TaperMode.NONE : TaperMode.SPINDLE);

        List<RibbonFrame> frames = new ArrayList<>(strands.size());
        for (List<Vec3> pts : strands) {
            RibbonFrame f = buildFrame(pts, state, shimmer, taper);
            if (f != null) {
                frames.add(f);
            }
        }
        if (frames.isEmpty()) {
            return;
        }

        int glow = state.glowColor;
        int core = state.coreColor;

        // Emissive passes: a soft wide halo, then the glowing body with flowing energy bands.
        collector.submitCustomGeometry(poseStack, GLOW_TYPE, (pose, buffer) -> {
            for (RibbonFrame f : frames) {
                // Wide soft bloom halo - reads as light bleeding off the strand.
                emitLayer(buffer, pose, f, base * 2.6f, glow,
                        0.16f * alphaScale * (1.0f + 0.6f * spawnFlash), state, 0.0f, 0.55f, spawnFlash);
                // Main glowing body with bright energy pulses travelling toward the tip.
                emitLayer(buffer, pose, f, base * 1.15f, glow,
                        0.95f * alphaScale, state, 1.0f, 1.0f, spawnFlash);
            }
        });
        // Translucent pass: the near-black inner core that gives Blackwhip its signature dark spine.
        collector.submitCustomGeometry(poseStack, CORE_TYPE, (pose, buffer) -> {
            for (RibbonFrame f : frames) {
                emitLayer(buffer, pose, f, base * 0.5f, core,
                        alphaScale, state, 0.0f, 1.2f, 0.0f);
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
        // Snappy whip extend: shoot out fast, settle at the target (ease-out cubic).
        float raw = state.active ? state.extendProgress : (1.0f - state.retractProgress);
        float reach = state.active ? (1.0f - (float) Math.pow(1.0 - raw, 3.0)) : raw;
        Vec3 end = state.start.add(state.end.subtract(state.start).scale(reach));

        Vec3 axis = end.subtract(state.start);
        double len = axis.length();
        if (len < 1.0e-4) {
            return List.of();
        }
        Vec3 dir = axis.scale(1.0 / len);
        Vec3 side = new Vec3(0, 1, 0).cross(dir);
        if (side.lengthSqr() < 1.0e-6) {
            side = new Vec3(1, 0, 0).cross(dir);
        }
        side = side.normalize();
        Vec3 lift = dir.cross(side).normalize();

        int segments = Math.max(16, (int) Math.min(96, len * 7.0));
        float curve = state.style == BlackwhipStyle.SWING_ROPE ? state.curve * 0.3f : state.curve;
        double arc = len * curve * 0.18;
        // Whip-crack shockwave: a transverse ripple that races toward the tip and decays after launch.
        double crack = Math.exp(-state.ageTicks * 0.16) * Math.min(1.0, len * 0.18);
        double sag = state.style == BlackwhipStyle.SWING_ROPE ? len * 0.05 : len * 0.02;
        double idle = 0.012 * len;

        List<Vec3> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 p = state.start.add(axis.scale(t));
            double bend = Math.sin(Math.PI * t);
            p = p.add(lift.scale(arc * bend)).add(new Vec3(0, -sag * bend, 0));
            double envelope = t * t; // ripple grows toward the free end
            double wavePhase = t * 9.0 - state.time * 0.6;
            double crackOff = crack * envelope * Math.sin(wavePhase);
            double idleOff = idle * bend * Math.sin(t * 3.3 + state.time * 0.07);
            p = p.add(side.scale(crackOff + idleOff))
                 .add(lift.scale(0.5 * crack * envelope * Math.cos(wavePhase * 1.3)));
            pts.add(p);
        }
        return pts;
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
    // Ribbon rendering - soft-edged, layered "energy ribbon"
    // ---------------------------------------------------------------------

    /** How the strand width tapers along its length. */
    private enum TaperMode {
        /** Constant width (wrap rings). */
        NONE,
        /** Thick at the base, tapering to a fine point at the tip (ropes / lashes). */
        WHIP,
        /** Tapered at both ends, fullest in the middle (aura tendrils / bubble petals). */
        SPINDLE
    }

    /**
     * A precomputed, camera-facing skeleton for one strand: each sample carries its local-space
     * centre, the billboard normal, the along-length parameter, and the tapered width factor. Every
     * render layer reuses this so the dark core, glow and halo stay locked together.
     */
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

    private static RibbonFrame buildFrame(List<Vec3> points, BlackwhipRenderState state, float shimmer, TaperMode taper) {
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
            if (i == 0) tangent = points.get(1).subtract(p);
            else if (i == n - 1) tangent = p.subtract(points.get(i - 1));
            else tangent = points.get(i + 1).subtract(points.get(i - 1));
            if (tangent.lengthSqr() < 1e-6) tangent = new Vec3(0, 1, 0);
            tangent = tangent.normalize();

            Vec3 noise = computeNoise(tangent, t, state.time, shimmer);
            center[i] = p.add(noise).subtract(origin);

            Vec3 nrm = cf.cross(tangent);
            if (nrm.lengthSqr() < 1e-6) nrm = new Vec3(0, 1, 0);
            normal[i] = nrm.normalize();

            tArr[i] = t;
            widthFactor[i] = taperFactor(taper, t);
        }
        return new RibbonFrame(center, normal, tArr, widthFactor);
    }

    private static float taperFactor(TaperMode taper, float t) {
        return switch (taper) {
            case NONE -> 1.0f;
            // Full at the base, easing to a fine point - the classic whip silhouette.
            case WHIP -> 0.30f + 0.70f * (float) Math.pow(1.0 - t, 0.8);
            // Pointed at both ends, fattest in the belly.
            case SPINDLE -> 0.25f + 0.75f * (float) Math.pow(Math.sin(Math.PI * t), 0.65);
        };
    }

    /**
     * Emits one soft-edged ribbon layer. The cross-section is drawn as two triangle strips that fade
     * from a solid bright centre line to fully transparent edges, giving each strand a round, glowing,
     * volumetric feel instead of a flat painted strip. When {@code flow > 0} bright energy pulses crawl
     * along the strand toward the tip, and {@code tipBoost} flares the leading point.
     */
    private static void emitLayer(VertexConsumer buffer, PoseStack.Pose pose, RibbonFrame f,
                                  float halfWidth, int argb, float alphaMul,
                                  BlackwhipRenderState state, float flow, float edgeSoftness, float tipBoost) {
        int n = f.center.length;
        if (n < 2) {
            return;
        }
        int light = FULL_BRIGHT;
        float baseA = ((argb >> 24) & 0xFF) / 255f;
        int r0 = (argb >> 16) & 0xFF;
        int g0 = (argb >> 8) & 0xFF;
        int b0 = argb & 0xFF;
        Vec3 cf = state.camForward;

        Vec3[] edgeL = new Vec3[n];
        Vec3[] edgeR = new Vec3[n];
        int[][] rgb = new int[n][3];
        float[] centerA = new float[n];

        // Closed loops (wrap rings) must not fade their ends, or a gap appears at the seam.
        boolean closed = f.center[0].distanceToSqr(f.center[n - 1]) < 1.0e-4;

        for (int i = 0; i < n; i++) {
            float t = f.t[i];
            float hw = halfWidth * f.widthFactor[i];
            edgeL[i] = f.center[i].add(f.normal[i].scale(hw));
            edgeR[i] = f.center[i].add(f.normal[i].scale(-hw));

            float bright = 1.0f;
            float white = 0.0f;
            float a = baseA * alphaMul;

            if (flow > 0.0f) {
                // Two travelling octaves form sparse, bright energy bands sliding toward the tip.
                double e1 = Math.sin(t * 11.0 - state.time * 0.45 + state.seed);
                double e2 = Math.sin(t * 26.0 - state.time * 0.80 + state.seed * 1.7);
                float energy = (float) Mth.clamp(0.5 + 0.5 * (0.62 * e1 + 0.38 * e2), 0.0, 1.0);
                float hot = (float) Math.pow(energy, 2.4);
                bright = 0.72f + 0.55f * energy;
                white = 0.65f * hot * flow;
                a *= 0.80f + 0.45f * energy;
            }

            // Tip flourish: the leading 18% flares brighter and whiter (the snapping spark).
            float tipZone = Mth.clamp((t - 0.82f) / 0.18f, 0.0f, 1.0f);
            if (tipBoost > 0.0f && tipZone > 0.0f) {
                float s = smooth(tipZone) * tipBoost;
                bright += 0.8f * s;
                white = Math.min(1.0f, white + 0.7f * s);
                a += 0.5f * s * baseA;
            }

            // Fade the very ends so strands melt away instead of stopping with a hard cap.
            if (!closed) {
                a *= smooth(Mth.clamp(t / 0.06f, 0.0f, 1.0f));         // base
                a *= smooth(Mth.clamp((1.0f - t) / 0.10f, 0.0f, 1.0f)); // tip
            }

            rgb[i][0] = mixWhite(clampByte(Math.round(r0 * bright)), white);
            rgb[i][1] = mixWhite(clampByte(Math.round(g0 * bright)), white);
            rgb[i][2] = mixWhite(clampByte(Math.round(b0 * bright)), white);
            centerA[i] = Mth.clamp(a, 0.0f, 1.0f);
        }

        float edgeA = Mth.clamp(1.0f - edgeSoftness, 0.0f, 1.0f);
        for (int i = 0; i < n - 1; i++) {
            // Left edge -> centre seam.
            softQuad(buffer, pose, cf, light,
                    edgeL[i], f.center[i], edgeL[i + 1], f.center[i + 1],
                    rgb[i], rgb[i + 1], centerA[i] * edgeA, centerA[i], centerA[i + 1] * edgeA, centerA[i + 1]);
            // Centre seam -> right edge.
            softQuad(buffer, pose, cf, light,
                    f.center[i], edgeR[i], f.center[i + 1], edgeR[i + 1],
                    rgb[i], rgb[i + 1], centerA[i], centerA[i] * edgeA, centerA[i + 1], centerA[i + 1] * edgeA);
        }
    }

    /** A camera-facing quad strip segment with independent per-corner alpha, drawn double-sided. */
    private static void softQuad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 normal, int light,
                                 Vec3 a0, Vec3 b0, Vec3 a1, Vec3 b1,
                                 int[] ci, int[] cj, float aA, float aB, float aC, float aD) {
        vertex(buffer, pose, a0, ci, aA, normal, light, 0f, 0f);
        vertex(buffer, pose, b0, ci, aB, normal, light, 1f, 0f);
        vertex(buffer, pose, b1, cj, aD, normal, light, 1f, 1f);
        vertex(buffer, pose, a1, cj, aC, normal, light, 0f, 1f);
        // Reverse winding so the billboard shows from behind too.
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

    private static int mixWhite(int channel, float white) {
        return clampByte(channel + Math.round((255 - channel) * Mth.clamp(white, 0f, 1f)));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
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

        // Anchor at the arm/hand instead of the hip: shoulder height, offset out to the arm, and pushed
        // slightly forward so the whip reads as firing from the hand.
        double shoulderHeight = Math.max(0.55, Math.min(1.45, player.getBbHeight() * 0.72));
        double crouch = player.isCrouching() ? -0.22 : 0.0;
        Vec3 armBase = state.ownerPos.add(0, shoulderHeight + crouch, 0);

        float side = switch (anchor) {
            case LEFT_HAND -> -1.0f;
            case RIGHT_HAND, RIGHT_HIGH -> 1.0f;
            default -> player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        };

        // Lateral arm offset (~half shoulder width) and a forward reach to approximate the extended hand.
        Vec3 toArm = rightYaw.scale(0.34 * side);

        if (anchor == BlackwhipAnchor.RIGHT_HIGH) {
            return armBase.add(toArm).add(0, 0.18, 0).add(fwdYaw.scale(0.55));
        }

        return armBase.add(toArm).add(fwdYaw.scale(0.30));
    }
}
