package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.blackwhip.BlackwhipChainClientAnchors;
import com.github.bandithelps.client.blackwhip.BlackwhipWaistBoneHelper;
import com.github.bandithelps.client.renderers.entity.state.BlackwhipChainRenderState;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.BlackwhipSegmentEntity;
import com.github.bandithelps.particles.BlackwhipDissolveParticle;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
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
    private static final Map<Integer, DissolveSnap> LAST_VISUAL = new HashMap<>();
    private static final Map<Integer, DissolveSnap> DISSOLVE_SNAPS = new HashMap<>();
    private static final float TIP_ITEM_SCALE = 0.55f;

    private record DissolveSnap(List<Vec3> joints, int ropeJointCount, boolean coilAppended) {
    }

    private final ItemModelResolver itemModelResolver;

    public BlackwhipChainEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
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

        state.extendProgress = entity.getExtendProgress(partial);
        state.tipItem = entity.getHeldTipItem();
        if (!state.tipItem.isEmpty()) {
            this.itemModelResolver.updateForNonLiving(
                    state.tipItemModel, state.tipItem, ItemDisplayContext.GROUND, entity);
        } else {
            state.tipItemModel.clear();
        }

        boolean dissolving = entity.getPhase() != BlackwhipChainEntity.PHASE_REELING
                && (!state.active || entity.getPhase() == BlackwhipChainEntity.PHASE_RETRACTING);
        state.dissolving = dissolving;
        if (dissolving) {
            int since = INACTIVE_SINCE.computeIfAbsent(entity.getId(), k -> entity.tickCount);
            float rt = Math.max(1, entity.getRetractTicks());
            state.retractProgress = Mth.clamp((entity.tickCount - since + partial) / rt, 0f, 1f);
        } else {
            INACTIVE_SINCE.remove(entity.getId());
            DISSOLVE_SNAPS.remove(entity.getId());
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
        // the whole ribbon (including the latch-height wrap) down relative to the target.
        double ex = Mth.lerp(partial, entity.xOld, entity.getX());
        double ey = Mth.lerp(partial, entity.yOld, entity.getY());
        double ez = Mth.lerp(partial, entity.zOld, entity.getZ());
        state.renderOrigin = new Vec3(ex, ey, ez);

        Entity owner = entity.getOwnerId() >= 0 && entity.level() != null
                ? entity.level().getEntity(entity.getOwnerId()) : null;
        state.hasBoneTip = false;

        if (dissolving) {
            applyDissolveSnapshot(entity, state, owner, partial);
            return;
        }

        buildVisualPose(entity, state, owner, partial, false);
        rememberVisualPose(entity.getId(), state);
    }

    private static void rememberVisualPose(int entityId, BlackwhipChainRenderState state) {
        if (state.joints.size() < 2) {
            LAST_VISUAL.remove(entityId);
            return;
        }
        LAST_VISUAL.put(entityId, new DissolveSnap(
                List.copyOf(state.joints), state.ropeJointCount, state.coilAppended));
    }

    /**
     * Freeze the last on-screen ribbon. Do not rebuild from raw segments — those are not wrist-glued
     * or coil-pinned, so using them teleports the whip before it dissolves.
     */
    private static void applyDissolveSnapshot(BlackwhipChainEntity entity, BlackwhipChainRenderState state,
                                             Entity owner, float partial) {
        state.fadeRoot = false;
        DissolveSnap snap = DISSOLVE_SNAPS.get(entity.getId());
        if (snap == null) {
            DissolveSnap last = LAST_VISUAL.remove(entity.getId());
            if (last != null && last.joints.size() >= 2) {
                snap = last;
            } else {
                buildVisualPose(entity, state, owner, partial, true);
                if (state.joints.size() >= 2) {
                    snap = new DissolveSnap(List.copyOf(state.joints), state.ropeJointCount, state.coilAppended);
                }
            }
            if (snap != null) {
                DISSOLVE_SNAPS.put(entity.getId(), snap);
            }
        }
        if (snap == null) {
            state.ropeJointCount = state.joints.size();
            state.coilAppended = false;
            return;
        }
        state.joints.clear();
        state.joints.addAll(driftDissolveJoints(snap.joints, state));
        state.ropeJointCount = snap.ropeJointCount;
        state.coilAppended = snap.coilAppended;
    }

    /**
     * Wrist + tip authorship used while the chain is alive. {@code dissolvingFallback} is only for
     * the rare case we never rendered an active pose (uses leftover target/anchor data).
     */
    private static void buildVisualPose(BlackwhipChainEntity entity, BlackwhipChainRenderState state,
                                        Entity owner, float partial, boolean dissolvingFallback) {
        state.fadeRoot = !dissolvingFallback && isLocalFirstPersonOwner(owner);
        Vec3 wrist = null;
        if (owner != null && state.joints.size() >= 2) {
            wrist = resolveVisualRoot(owner, partial);
            Vec3 seg0 = state.joints.getFirst();
            Vec3 offset = wrist.subtract(seg0);
            for (int i = 0; i < state.joints.size(); i++) {
                state.joints.set(i, state.joints.get(i).add(offset));
            }
        }

        state.coilAppended = false;
        state.ropeJointCount = state.joints.size();

        boolean pinAnchor = dissolvingFallback ? isAnchorDissolveFallback(entity) : entity.isAnchored();
        if (pinAnchor && state.joints.size() >= 2 && wrist != null) {
            Vec3 tip = entity.getAnchorPoint();
            state.joints.set(state.joints.size() - 1, tip);
            BlackwhipChainAnchors.redistributeJoints(state.joints, wrist, tip);
            state.ropeJointCount = state.joints.size();
            return;
        }

        LivingEntity target = entity.getTargetLiving();
        boolean wrap = dissolvingFallback
                ? target != null && target.isAlive() && entity.getWrapTurns() > 0.0f
                : entity.isLatched() && target != null && state.active;
        if (wrap && target != null && state.joints.size() >= 2 && wrist != null) {
            float wrapHeight = isLocalFirstPersonTarget(target)
                    ? BlackwhipChainAnchors.firstPersonTargetWrapHeight(target)
                    : entity.getWrapHeight();
            int ropeCount = BlackwhipWaistBoneHelper.attachTipAndCoil(
                    state.joints, target, wrist, entity.getWrapTurns(),
                    dissolvingFallback ? 1.0f : state.extendProgress,
                    wrapHeight, partial);
            if (ropeCount > 0) {
                state.ropeJointCount = ropeCount;
                state.coilAppended = state.joints.size() > ropeCount;
            }
            var bone = BlackwhipWaistBoneHelper.resolveWaistTipClient(entity.getTargetId(), partial);
            if (bone.isPresent()) {
                state.hasBoneTip = true;
                state.boneTip = bone.get();
            }
        }
    }

    private static boolean isAnchorDissolveFallback(BlackwhipChainEntity entity) {
        if (entity.getTargetId() >= 0) {
            return false;
        }
        int purpose = entity.getPurpose();
        return purpose == BlackwhipChainEntity.PURPOSE_SWING
                || purpose == BlackwhipChainEntity.PURPOSE_WEB_SWING
                || purpose == BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE
                || purpose == BlackwhipChainEntity.PURPOSE_ZIP_CHARGE;
    }

    private static List<Vec3> driftDissolveJoints(List<Vec3> frozen, BlackwhipChainRenderState state) {
        float p = state.retractProgress;
        float eased = p * p;
        List<Vec3> out = new ArrayList<>(frozen.size());
        for (int i = 0; i < frozen.size(); i++) {
            float hx = hash01(state.seed, i, 1) - 0.5f;
            float hy = hash01(state.seed, i, 2) - 0.3f;
            float hz = hash01(state.seed, i, 3) - 0.5f;
            double sag = 0.42 * eased * p;
            out.add(frozen.get(i).add(hx * 0.55 * eased, -sag + hy * 0.18 * eased, hz * 0.55 * eased));
        }
        return out;
    }

    static float fragmentAlive(int seed, int index, float progress) {
        float threshold = 0.16f + hash01(seed, index, 7) * 0.72f;
        return smooth(Mth.clamp((threshold - progress) / 0.14f, 0.0f, 1.0f));
    }

    private static float hash01(int seed, int index, int salt) {
        int h = seed * 374761393 + index * 668265263 + salt * 1274126177;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h >>> 1) & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    /**
     * Animated main-hand attach via Palladium {@code ModelUtil} (same as energy-beam / webswing).
     * Falls back to approximate wrist math for non-player owners.
     */
    private static Vec3 resolveVisualRoot(Entity owner, float partial) {
        return BlackwhipChainClientAnchors.resolveVisualRoot(owner, partial);
    }

    /** True when the camera player owns this whip and is in first person. */
    private static boolean isLocalFirstPersonOwner(Entity owner) {
        Minecraft mc = Minecraft.getInstance();
        return owner != null
                && mc.player != null
                && owner.getId() == mc.player.getId()
                && mc.options.getCameraType().isFirstPerson();
    }

    /** True when the camera player is the latched target viewing themselves in first person. */
    private static boolean isLocalFirstPersonTarget(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        return target != null
                && mc.player != null
                && target.getId() == mc.player.getId()
                && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public void submit(BlackwhipChainRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.joints.size() < 2) {
            return;
        }

        Vector3f camLook = camera.orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        state.camForward = new Vec3(camLook.x(), camLook.y(), camLook.z()).normalize();

        List<Vec3> points = new ArrayList<>(state.joints);

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

        float alphaScale = state.dissolving
                ? smooth(1.0f - state.retractProgress * 0.55f)
                : 1.0f;
        float spawnFlash = (float) Math.exp(-state.ageTicks * 0.18f);

        // Mild shimmer under the slow idle pulse — keep amplitude low so layers stay sharp.
        // With a wrap coil, damp shimmer further so dense helix samples don't self-intersect.
        float shimmerCap = state.coilAppended ? 0.018f : 0.035f;
        if (state.dissolving) {
            shimmerCap = Math.max(shimmerCap, 0.04f) * (1.0f + 3.2f * state.retractProgress);
        }
        RibbonFrame frame = buildFrame(points, state, Math.min(base * 0.55f, shimmerCap));
        if (frame == null) {
            return;
        }

        int glow = state.glowColor;
        int outer = state.outerColor;
        int core = state.coreColor;
        float finalBase = base * 1.12f; // applying thickness with the extra 1.12
        float finalFlash = spawnFlash ;

        // Small camera-depth bias separates coplanar glow/outer/core and kills ribbon z-fight.
        collector.submitCustomGeometry(poseStack, GLOW_TYPE, (pose, buffer) -> {
            emitLayer(buffer, pose, frame, finalBase * 2.6f, glow,
                    0.16f * alphaScale * (1.0f + 0.6f * finalFlash), state, 0.0f, 0.88f, finalFlash, 0.0f);
            emitLayer(buffer, pose, frame, finalBase * 1.15f, outer,
                    0.95f * alphaScale, state, 1.0f, 1.0f, finalFlash, 0.012f);
        });
        collector.submitCustomGeometry(poseStack, CORE_TYPE, (pose, buffer) ->
                emitLayer(buffer, pose, frame, finalBase * 0.5f, core,
                        alphaScale, state, 0.0f, 1.2f, 0.0f, 0.024f));

        submitTipItem(state, points, poseStack, collector);
        spawnDissolveParticles(state, points);
    }

    private static void spawnDissolveParticles(BlackwhipChainRenderState state, List<Vec3> points) {
        if (!state.dissolving || points.isEmpty() || state.retractProgress >= 0.97f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)) {
            return;
        }
        var random = level.getRandom();
        int n = points.size();
        float remaining = 1.0f - state.retractProgress;
        int bursts = state.retractProgress < 0.08f
                ? Math.max(4, n / 2)
                : Math.max(1, Math.round(n * 0.18f * remaining));
        for (int k = 0; k < bursts; k++) {
            int i = random.nextInt(n);
            if (fragmentAlive(state.seed, i, state.retractProgress) < 0.08f) {
                continue;
            }
            Vec3 p = points.get(i);
            double jx = (random.nextDouble() - 0.5) * 0.12;
            double jy = (random.nextDouble() - 0.5) * 0.12;
            double jz = (random.nextDouble() - 0.5) * 0.12;
            Vec3 vel = new Vec3(
                    (random.nextDouble() - 0.5) * 0.07,
                    0.015 + random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.07);
            int color = random.nextBoolean() ? state.glowColor : state.coreColor;
            BlackwhipDissolveParticle.spawn(level, p.add(jx, jy, jz), vel, color);
        }
    }

    /** Draws cargo at the whip tip. During reel-in it rides back to the wrist; dissolve still fades in place. */
    private static void submitTipItem(BlackwhipChainRenderState state, List<Vec3> points,
                                      PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.tipItem.isEmpty() || state.tipItemModel.isEmpty() || points.size() < 2) {
            return;
        }
        float fade = state.dissolving ? smooth(1.0f - state.retractProgress) : 1.0f;
        if (fade < 0.08f) {
            return;
        }
        Vec3 tip = points.getLast();
        Vec3 prev = points.get(points.size() - 2);
        Vec3 dir = tip.subtract(prev);
        if (dir.lengthSqr() < 1.0e-6) {
            dir = state.camForward;
        } else {
            dir = dir.normalize();
        }
        Vec3 local = tip.subtract(state.renderOrigin);

        poseStack.pushPose();
        poseStack.translate(local.x, local.y, local.z);
        // Sit slightly ahead of the tip so the ribbon doesn't swallow the model.
        poseStack.translate(dir.x * 0.08, dir.y * 0.08, dir.z * 0.08);
        float scale = TIP_ITEM_SCALE * (0.85f + 0.15f * fade);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotation(state.ageTicks * 0.35f));
        state.tipItemModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
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

        // Chord from rope only (exclude wrap helix) — dense coil polyline inflated idle sway / z-fight.
        int ropeEnd = state.ropeJointCount > 1
                ? Math.min(state.ropeJointCount, n)
                : n;
        Vec3 chord = points.get(ropeEnd - 1).subtract(points.get(0));
        double chordLen = Math.sqrt(chord.lengthSqr());
        Vec3 chordDir = chordLen > 1.0e-6 ? chord.scale(1.0 / chordLen) : new Vec3(0, 0, 1);
        Vec3 side = new Vec3(0, 1, 0).cross(chordDir);
        if (side.lengthSqr() < 1.0e-6) {
            side = new Vec3(1, 0, 0).cross(chordDir);
        }
        side = side.normalize();

        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            // Rope-normalized t for idle sway so the helix doesn't get mid-chord bend amplitude.
            float ropeT = ropeEnd > 1
                    ? Mth.clamp(i / (float) (ropeEnd - 1), 0.0f, 1.0f)
                    : t;
            boolean onCoil = i >= ropeEnd;
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

            // Visual-only: slow breathe/sway on the ribbon; does not move segment hitboxes.
            // Coil samples stay still — sway on a dense helix is the main z-fight source.
            Vec3 pulse = onCoil ? Vec3.ZERO : computeIdlePulse(side, ropeT, state.time, state.seed, chordLen);
            Vec3 noise = onCoil ? Vec3.ZERO : computeNoise(tangent, ropeT, state.time, shimmer);
            center[i] = p.add(pulse).add(noise).subtract(origin);

            Vec3 nrm = cf.cross(tangent);
            if (nrm.lengthSqr() < 1e-6) {
                nrm = new Vec3(0, 1, 0);
            }
            normal[i] = nrm.normalize();
            tArr[i] = t;
            float width = 0.30f + 0.70f * (float) Math.pow(1.0 - t, 0.8);
            if (state.dissolving) {
                width *= 1.0f - 0.65f * state.retractProgress;
            }
            widthFactor[i] = width;
        }
        return new RibbonFrame(center, normal, tArr, widthFactor);
    }

    /**
     * Soft port of the old render-only rope idle sway + aura breathe. Applied only to ribbon
     * centers so kinematics and attack/destroy hit proxies stay authoritative.
     * Amplitudes stay small relative to ribbon half-width to avoid layer self-intersection / z-fight.
     */
    private static Vec3 computeIdlePulse(Vec3 side, double t, double time, int seed, double chordLen) {
        double bend = Math.sin(Math.PI * t);
        if (bend < 1.0e-4) {
            return Vec3.ZERO;
        }
        // Match old rope scale, but clamp — dense tip coils must not inflate motion.
        double idle = Mth.clamp(0.008 * Math.max(0.5, chordLen), 0.004, 0.04);
        double idleOff = idle * bend * Math.sin(t * 3.3 + time * 0.07);
        double breathe = 0.012 * bend * Math.sin(time * 0.03 + seed * 0.01);
        return side.scale(idleOff).add(0, breathe, 0);
    }

    private static void emitLayer(VertexConsumer buffer, PoseStack.Pose pose, RibbonFrame f,
                                  float halfWidth, int argb, float alphaMul,
                                  BlackwhipChainRenderState state, float flow, float edgeSoftness,
                                  float tipBoost, float depthBias) {
        int n = f.center.length;
        if (n < 2) {
            return;
        }
        float baseA = ((argb >> 24) & 0xFF) / 255f;
        int r0 = (argb >> 16) & 0xFF;
        int g0 = (argb >> 8) & 0xFF;
        int b0 = argb & 0xFF;
        Vec3 cf = state.camForward;
        // Pull layer slightly toward the camera so glow/outer/core aren't coplanar.
        Vec3 towardCam = depthBias > 0.0f ? cf.scale(-depthBias) : Vec3.ZERO;

        Vec3[] edgeL = new Vec3[n];
        Vec3[] edgeR = new Vec3[n];
        Vec3[] mid = new Vec3[n];
        int[][] rgb = new int[n][3];
        float[] centerA = new float[n];

        for (int i = 0; i < n; i++) {
            float t = f.t[i];
            float hw = halfWidth * f.widthFactor[i];
            mid[i] = f.center[i].add(towardCam);
            edgeL[i] = mid[i].add(f.normal[i].scale(hw));
            edgeR[i] = mid[i].add(f.normal[i].scale(-hw));

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

            // Root fade is first-person only — hides the hard wrist attach under the camera hand.
            if (state.fadeRoot) {
                a *= smooth(Mth.clamp(t / 0.06f, 0.0f, 1.0f));
            }
            if (!state.dissolving) {
                a *= smooth(Mth.clamp((1.0f - t) / 0.10f, 0.0f, 1.0f));
            } else {
                a *= fragmentAlive(state.seed, i, state.retractProgress);
            }

            rgb[i][0] = mixWhite(clampByte(Math.round(r0 * bright)), white);
            rgb[i][1] = mixWhite(clampByte(Math.round(g0 * bright)), white);
            rgb[i][2] = mixWhite(clampByte(Math.round(b0 * bright)), white);
            centerA[i] = Mth.clamp(a, 0.0f, 1.0f);
        }

        float edgeA = Mth.clamp(1.0f - edgeSoftness, 0.0f, 1.0f);
        for (int i = 0; i < n - 1; i++) {
            if (state.dissolving && centerA[i] < 0.04f && centerA[i + 1] < 0.04f) {
                continue;
            }
            softQuad(buffer, pose, cf, FULL_BRIGHT,
                    edgeL[i], mid[i], edgeL[i + 1], mid[i + 1],
                    rgb[i], rgb[i + 1], centerA[i] * edgeA, centerA[i], centerA[i + 1] * edgeA, centerA[i + 1]);
            softQuad(buffer, pose, cf, FULL_BRIGHT,
                    mid[i], edgeR[i], mid[i + 1], edgeR[i + 1],
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
        // Slower / quieter than the old shimmer so it doesn't fight the idle pulse.
        double a1 = Math.sin(time * 0.35 + t * 8.0) * amplitude;
        double a2 = Math.cos(time * 0.48 + t * 13.0) * (amplitude * 0.55);
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
