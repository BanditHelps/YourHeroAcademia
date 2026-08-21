package com.github.bandithelps.client.renderers.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot for {@code BlackwhipChainEntityRenderer}: joint polyline + visual params.
 */
public class BlackwhipChainRenderState extends EntityRenderState {
    public final List<Vec3> joints = new ArrayList<>();
    public Vec3 renderOrigin = Vec3.ZERO;
    public Vec3 camForward = new Vec3(0, 0, 1);

    public int coreColor = 0xFF101A1A;
    public int outerColor = 0xE025BE9C;
    public int glowColor = 0xB325BE9C;
    public float thickness = 1.0f;
    public boolean active = true;
    public boolean dissolving = false;
    public float retractProgress = 0.0f;
    public float extendProgress = 1.0f;
    public float ageTicks = 0.0f;
    public int seed = 0;
    public int hurtTick = 0;
    public double time = 0.0;

    /** Optional client-side tip polish (player waist bone). */
    public boolean hasBoneTip = false;
    public Vec3 boneTip = Vec3.ZERO;

    /** True when a dense wrap coil was appended to {@link #joints} for this frame. */
    public boolean coilAppended = false;

    /**
     * Joint count for the wrist→tip rope only (excludes appended wrap coil samples). Used so idle
     * sway scales from the rope chord instead of the dense helix polyline.
     */
    public int ropeJointCount = 0;

    /**
     * When true, soft-fade the wrist end of the ribbon (first-person local owner only). Third person
     * and other viewers keep full opacity at the root so the chain reads solid.
     */
    public boolean fadeRoot = false;

    /** Cargo stuck at the whip tip during reel-in (magnet / disarm). */
    public ItemStack tipItem = ItemStack.EMPTY;
    public final ItemStackRenderState tipItemModel = new ItemStackRenderState();
}
