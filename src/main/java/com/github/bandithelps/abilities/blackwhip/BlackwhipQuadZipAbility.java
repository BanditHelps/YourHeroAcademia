package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import com.github.bandithelps.network.BlackwhipSwingPayload;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Quad Zip": hold to charge a four-anchor Blackwhip launch, release to rocket forward. The charge time
 * scales the launch power, and entities caught in the swept launch path take quirk-factor-scaled impact
 * damage. A short cluster of anchor tendrils sells the multi-whip lunge.
 */
public class BlackwhipQuadZipAbility extends Ability {

    private static final Map<UUID, Integer> CHARGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CHARGE_VISUAL = new ConcurrentHashMap<>();

    public static final MapCodec<BlackwhipQuadZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(10.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("max_charge_ticks", new StaticValue(30.0f)).forGetter((ab) -> ab.maxChargeTicks),
                    Value.CODEC.optionalFieldOf("base_launch_power", new StaticValue(0.9f)).forGetter((ab) -> ab.baseLaunchPower),
                    Value.CODEC.optionalFieldOf("max_launch_power", new StaticValue(2.6f)).forGetter((ab) -> ab.maxLaunchPower),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(6.0f)).forGetter((ab) -> ab.damage),
                    Value.CODEC.optionalFieldOf("hit_radius", new StaticValue(1.6f)).forGetter((ab) -> ab.hitRadius),
                    Value.CODEC.optionalFieldOf("anchor_count", new StaticValue(4.0f)).forGetter((ab) -> ab.anchorCount),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(0.9f)).forGetter((ab) -> ab.thickness),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipQuadZipAbility::new));

    public final Value range;
    public final Value maxChargeTicks;
    public final Value baseLaunchPower;
    public final Value maxLaunchPower;
    public final Value damage;
    public final Value hitRadius;
    public final Value anchorCount;
    public final Value thickness;

    public BlackwhipQuadZipAbility(Value range, Value maxChargeTicks, Value baseLaunchPower, Value maxLaunchPower, Value damage,
                                  Value hitRadius, Value anchorCount, Value thickness, AbilityProperties properties,
                                  AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.maxChargeTicks = maxChargeTicks;
        this.baseLaunchPower = baseLaunchPower;
        this.maxLaunchPower = maxLaunchPower;
        this.damage = damage;
        this.hitRadius = hitRadius;
        this.anchorCount = anchorCount;
        this.thickness = thickness;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        CHARGE.put(player.getUUID(), 0);
        BlackwhipEntity aura = BlackwhipHelper.spawnAura(player, 6, 1.1f, 0.8f, this.thickness.getAsFloat(DataContext.forEntity(entity)), 0.45f);
        CHARGE_VISUAL.put(player.getUUID(), aura.getId());
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            int max = Math.max(1, this.maxChargeTicks.getAsInt(context));
            CHARGE.merge(player.getUUID(), 1, (a, b) -> Math.min(max, a + b));
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Integer charge = CHARGE.remove(player.getUUID());
        Integer visualId = CHARGE_VISUAL.remove(player.getUUID());
        if (visualId != null && level.getEntity(visualId) instanceof BlackwhipEntity aura) {
            aura.deactivate();
        }
        if (charge == null) {
            return;
        }

        DataContext context = DataContext.forEntity(entity);
        int max = Math.max(1, this.maxChargeTicks.getAsInt(context));
        float ratio = Mth.clamp(charge / (float) max, 0.0f, 1.0f);
        double qf = QuirkFactorUtil.getQuirkFactor(player);

        double power = Mth.lerp(ratio, this.baseLaunchPower.getAsFloat(context), this.maxLaunchPower.getAsFloat(context)) * (1.0 + 0.08 * qf);
        Vec3 look = player.getLookAngle().normalize();
        player.setDeltaMovement(look.scale(power).add(0, power * 0.35, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        // Cancel any active swing visual state on the client so the launch reads clean.
        PacketDistributor.sendToPlayer(player, new BlackwhipSwingPayload(false, 0, 0, 0, 0));

        spawnLaunchTendrils(player, context, look);
        applySweptDamage(player, level, context, look, ratio, qf);

        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    private void spawnLaunchTendrils(ServerPlayer player, DataContext context, Vec3 look) {
        double range = this.range.getAsFloat(context);
        float thickness = this.thickness.getAsFloat(context);
        int count = Math.max(1, this.anchorCount.getAsInt(context));
        Vec3 eye = player.getEyePosition();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = worldUp.cross(look);
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = look.cross(right).normalize();
        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI * i) / count;
            Vec3 offset = right.scale(Math.cos(angle) * 0.9).add(up.scale(Math.sin(angle) * 0.9));
            Vec3 point = eye.add(look.scale(range)).add(offset);
            BlackwhipHelper.spawnLash(player, point, thickness, 0.25f, 5);
        }
    }

    private void applySweptDamage(ServerPlayer player, ServerLevel level, DataContext context, Vec3 look, float ratio, double qf) {
        double range = this.range.getAsFloat(context);
        double hitRadius = this.hitRadius.getAsFloat(context);
        float baseDamage = this.damage.getAsFloat(context);
        if (baseDamage <= 0.0f || range <= 0.0 || hitRadius <= 0.0) {
            return;
        }
        float damage = baseDamage * (0.5f + 0.5f * ratio) * (float) (1.0 + 0.1 * qf);

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB search = new AABB(start, end).inflate(hitRadius);
        for (Entity e : level.getEntities(player, search)) {
            if (!(e instanceof LivingEntity target) || !target.isAlive() || target == player) {
                continue;
            }
            Optional<Vec3> impact = target.getBoundingBox().inflate(hitRadius).clip(start, end);
            if (impact.isPresent()) {
                target.hurt(level.damageSources().mobAttack(player), damage);
            }
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_QUAD_ZIP.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipQuadZipAbility> {
        public MapCodec<BlackwhipQuadZipAbility> codec() {
            return BlackwhipQuadZipAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipQuadZipAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Hold to charge a four-anchor Blackwhip launch, release to rocket forward. Charge time scales launch power, and entities in the swept path take quirk-factor-scaled impact damage.")
                    .add("range", TYPE_VALUE, "Length of the swept launch path / tendril reach.")
                    .add("max_charge_ticks", TYPE_VALUE, "Ticks of holding for a full-power launch.")
                    .add("base_launch_power", TYPE_VALUE, "Launch velocity at zero charge.")
                    .add("max_launch_power", TYPE_VALUE, "Launch velocity at full charge.")
                    .add("damage", TYPE_VALUE, "Base impact damage to entities in the launch path.")
                    .add("hit_radius", TYPE_VALUE, "Radius around the launch line used for hit detection.")
                    .add("anchor_count", TYPE_VALUE, "Number of visual launch tendrils.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .addExampleObject(new BlackwhipQuadZipAbility(new StaticValue(10.0f), new StaticValue(30.0f), new StaticValue(0.9f),
                            new StaticValue(2.6f), new StaticValue(6.0f), new StaticValue(1.6f), new StaticValue(4.0f), new StaticValue(0.9f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
