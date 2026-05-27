package com.github.bandithelps.gene;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.capabilities.dna.PlayerDNAChangedEvent;
import com.github.bandithelps.utils.gene.GeneUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class GeneResistanceHandler {

    private static final double MAX_POISON_AVOID_CHANCE = 0.90D;
    private static final float NO_DAMAGE_EPSILON = 0.0001F;
    private static final Map<UUID, ResistanceProfile> RESISTANCE_PROFILES = new ConcurrentHashMap<>();

    private GeneResistanceHandler() {
    }

    @SubscribeEvent
    public static void onDNAChanged(PlayerDNAChangedEvent event) {
        rebuildProfile(event.getEntity(), event.getCurrentDNA());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rebuildProfile(player, DNAAttachments.get(player).getDNA());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rebuildProfile(player, DNAAttachments.get(player).getDNA());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RESISTANCE_PROFILES.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResistanceProfile profile = RESISTANCE_PROFILES.get(player.getUUID());
        if (profile == null || event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        if (isFireDamageExceptLava(source)) {
            float adjusted = (float) (event.getAmount() * (1.0D - profile.fireTickDamageReduction));
            applyAdjustedDamage(event, adjusted);
            return;
        }

        if (isPoisonTickDamage(player, source)) {
            double roll = player.getRandom().nextDouble();
            if (roll < profile.poisonDamageAvoidChance) {
                // Successful resistance roll nullifies this specific poison tick.
                applyAdjustedDamage(event, 0.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResistanceProfile profile = RESISTANCE_PROFILES.get(player.getUUID());
        if (profile == null) {
            return;
        }

        DamageSource source = event.getSource();
        if (isFireDamageExceptLava(source) && event.getNewDamage() <= NO_DAMAGE_EPSILON) {
            event.setNewDamage(0.0F);
            return;
        }

        if (isPoisonTickDamage(player, source) && event.getNewDamage() <= NO_DAMAGE_EPSILON) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResistanceProfile profile = RESISTANCE_PROFILES.get(player.getUUID());
        if (profile == null || !profile.witherNullify) {
            return;
        }
        if (event.getEffectInstance().getEffect().equals(MobEffects.WITHER)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResistanceProfile profile = RESISTANCE_PROFILES.get(player.getUUID());
        if (profile == null || !profile.witherNullify) {
            return;
        }
        if (event.getEffectInstance().getEffect().equals(MobEffects.WITHER)) {
            player.removeEffect(MobEffects.WITHER);
        }
    }

    private static void rebuildProfile(ServerPlayer player, String dnaString) {
        if (player == null || dnaString == null || dnaString.isEmpty()) {
            RESISTANCE_PROFILES.remove(player.getUUID());
            return;
        }

        DNA dna = GeneUtil.parseDNA(dnaString);
        if (dna == null || dna.isEmpty()) {
            RESISTANCE_PROFILES.remove(player.getUUID());
            return;
        }

        ResistanceProfile profile = ResistanceProfile.empty();
        for (Gene gene : dna.getGenes()) {
            if (gene == null || gene.getCategory() != GeneCategory.RESISTANCE) {
                continue;
            }
            profile = profile.combine(resolveGeneProfile(gene));
        }
        if (profile.isEmpty()) {
            RESISTANCE_PROFILES.remove(player.getUUID());
        } else {
            RESISTANCE_PROFILES.put(player.getUUID(), profile);
        }
    }

    private static ResistanceProfile resolveGeneProfile(Gene gene) {
        if (gene.getType() == null || gene.getType().getResistanceEffects().isEmpty()) {
            return ResistanceProfile.empty();
        }

        double fireReduction = 0.0D;
        double poisonAvoidChance = 0.0D;
        boolean witherNullify = false;

        for (GeneType.ResistanceEffect effect : gene.getType().getResistanceEffects()) {
            double value = effect.resolveValueForQuality(
                    gene.getQuality(),
                    gene.getType().getQualityMin(),
                    gene.getType().getQualityMax()
            );
            switch (effect.getKind()) {
                case FIRE_TICK_DAMAGE -> fireReduction += clamp(value, 0.0D, 0.95D);
                case POISON_DAMAGE_AVOIDANCE -> poisonAvoidChance += clamp(value, 0.0D, MAX_POISON_AVOID_CHANCE);
                case WITHER_NULLIFY -> witherNullify = witherNullify || value > 0.0D;
            }
        }
        return new ResistanceProfile(
                clamp(fireReduction, 0.0D, 0.95D),
                clamp(poisonAvoidChance, 0.0D, MAX_POISON_AVOID_CHANCE),
                witherNullify
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isFireDamageExceptLava(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.LAVA);
    }

    private static boolean isPoisonTickDamage(ServerPlayer player, DamageSource source) {
        if (!player.hasEffect(MobEffects.POISON)) {
            return false;
        }
        return source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || (source.getEntity() == null && source.is(DamageTypeTags.WITCH_RESISTANT_TO));
    }

    private static void applyAdjustedDamage(LivingIncomingDamageEvent event, float adjustedDamage) {
        float clamped = Math.max(0.0F, adjustedDamage);
        if (clamped <= NO_DAMAGE_EPSILON) {
            event.setCanceled(true);
            return;
        }
        event.setAmount(clamped);
    }

    private record ResistanceProfile(double fireTickDamageReduction, double poisonDamageAvoidChance, boolean witherNullify) {
        private static ResistanceProfile empty() {
            return new ResistanceProfile(0.0D, 0.0D, false);
        }

        private ResistanceProfile combine(ResistanceProfile other) {
            return new ResistanceProfile(
                    clamp(this.fireTickDamageReduction + other.fireTickDamageReduction, 0.0D, 0.95D),
                    clamp(this.poisonDamageAvoidChance + other.poisonDamageAvoidChance, 0.0D, MAX_POISON_AVOID_CHANCE),
                    this.witherNullify || other.witherNullify
            );
        }

        private boolean isEmpty() {
            return this.fireTickDamageReduction <= 0.0D
                    && this.poisonDamageAvoidChance <= 0.0D
                    && !this.witherNullify;
        }
    }
}
