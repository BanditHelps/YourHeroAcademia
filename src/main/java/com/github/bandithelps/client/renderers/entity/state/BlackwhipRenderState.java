package com.github.bandithelps.client.renderers.entity.state;

import com.github.bandithelps.entities.BlackwhipStyle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Snapshot of everything the {@code BlackwhipEntityRenderer} needs to draw a whip for a single frame.
 * Populated in {@code extractRenderState} so the heavy world lookups happen once per frame.
 */
public class BlackwhipRenderState extends EntityRenderState {
    public BlackwhipStyle style = BlackwhipStyle.TETHER;

    // Resolved endpoints in world space.
    public Vec3 start = Vec3.ZERO;
    public Vec3 end = Vec3.ZERO;
    public boolean hasEnd = false;

    // Wrap rings (TETHER / WRAP) around a target.
    public boolean drawWrap = false;
    public Vec3 wrapCenter = Vec3.ZERO;
    public double wrapRadius = 0.0;
    public double wrapMinY = 0.0;
    public double wrapMaxY = 0.0;

    // Owner basis for procedural styles (AURA / BUBBLE).
    public Vec3 ownerPos = Vec3.ZERO;
    public Vec3 ownerEye = Vec3.ZERO;
    public Vec3 fwdYaw = new Vec3(0, 0, 1);
    public Vec3 rightYaw = new Vec3(1, 0, 0);
    public double ownerHeight = 1.8;

    // Visual params.
    public int coreColor = 0xFF101A1A;
    public int glowColor = 0xB325BE9C;
    public float thickness = 1.0f;
    public float curve = 0.6f;
    public float jaggedness = 0.3f;
    public float length = 2.0f;
    public int strands = 1;
    public long seed = 0L;
    public float forwardOffset = 1.2f;

    // Animation.
    public float extendProgress = 1.0f; // 0..1 grow-in
    public boolean active = true;
    public float retractProgress = 0.0f; // 0..1 shrink-out
    public float ageTicks = 0.0f; // ticks (with partial) since spawn, drives the whip-crack decay

    // Frame helpers.
    public Vec3 renderOrigin = Vec3.ZERO; // interpolated entity pos (local-space origin)
    public Vec3 camPos = Vec3.ZERO;
    public Vec3 camForward = new Vec3(0, 0, 1);
    public double time = 0.0;
}
