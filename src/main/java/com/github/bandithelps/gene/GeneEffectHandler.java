package com.github.bandithelps.gene;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.capabilities.dna.PlayerDNAChangedEvent;
import com.github.bandithelps.attributes.QuirkAttributes;
import com.github.bandithelps.utils.gene.GeneUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class GeneEffectHandler {

    private static final AttributeModifier.Operation ATTRIBUTE_GENE_OPERATION = AttributeModifier.Operation.ADD_VALUE;
    private static final Map<UUID, Map<Identifier, String>> APPLIED_GENE_MODIFIERS = new ConcurrentHashMap<>();

    private GeneEffectHandler() {
    }

    @SubscribeEvent
    public static void onDNAChanged(PlayerDNAChangedEvent event) {
        applyGeneEffects(event.getEntity(), event.getCurrentDNA());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String dna = DNAAttachments.get(player).getDNA();
            applyGeneEffects(player, dna);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String dna = DNAAttachments.get(player).getDNA();
            applyGeneEffects(player, dna);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            APPLIED_GENE_MODIFIERS.remove(player.getUUID());
        }
    }

    public static void applyGeneEffects(ServerPlayer player, String dnaString) {
        if (player == null) {
            return;
        }

        clearGeneEffects(player);

        if (dnaString == null || dnaString.isEmpty()) {
            return;
        }

        DNA dna = GeneUtil.parseDNA(dnaString);
        if (dna == null || dna.isEmpty()) {
            return;
        }

        Map<Identifier, String> playerModifiers = new HashMap<>();
        for (Gene gene : dna.getGenes()) {
            applyGeneEffect(player, gene, playerModifiers);
        }

        APPLIED_GENE_MODIFIERS.put(player.getUUID(), playerModifiers);
    }

    private static void applyGeneEffect(ServerPlayer player, Gene gene, Map<Identifier, String> playerModifiers) {
        if (player == null || gene == null) {
            return;
        }

        switch (gene.getCategory()) {
            case ATTRIBUTE -> applyAttributeEffect(player, gene, playerModifiers);
            default -> {
                // Placeholder for future gene categories.
            }
        }
    }

    private static void applyAttributeEffect(ServerPlayer player, Gene gene, Map<Identifier, String> playerModifiers) {
        if (gene.getType().getAttributeEffects().isEmpty()) {
            YourHeroAcademia.LOGGER.warn("Attribute gene {} has no attribute effect definition", gene.getType().getId());
            return;
        }

        for (int i = 0; i < gene.getType().getAttributeEffects().size(); i++) {
            GeneType.AttributeEffect attributeEffect = gene.getType().getAttributeEffects().get(i);
            String attributeId = attributeEffect.getAttributeId();
            Holder<Attribute> attributeHolder = resolveAttribute(attributeId);
            if (attributeHolder == null) {
                YourHeroAcademia.LOGGER.warn("Unknown attribute {} for gene {}", attributeId, gene.getType().getId());
                continue;
            }

            AttributeInstance instance = player.getAttribute(attributeHolder);
            if (instance == null) {
                continue;
            }

            double modifier = attributeEffect.resolveModifierForQuality(
                    gene.getQuality(),
                    gene.getType().getQualityMin(),
                    gene.getType().getQualityMax()
            );
            Identifier modifierId = createGeneModifierId(gene.getId(), i);
            instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, modifier, ATTRIBUTE_GENE_OPERATION));
            playerModifiers.put(modifierId, attributeId);
        }
    }

    private static Holder<Attribute> resolveAttribute(String attributeId) {
        try {
            Identifier id = Identifier.parse(attributeId);
            return BuiltInRegistries.ATTRIBUTE.get(id)
                    .map(holder -> (Holder<Attribute>) holder)
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Identifier createGeneModifierId(UUID geneId, int effectIndex) {
        return Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene/attribute/" + geneId + "/" + effectIndex);
    }

    public static void clearGeneEffects(ServerPlayer player) {
        Map<Identifier, String> applied = APPLIED_GENE_MODIFIERS.remove(player.getUUID());
        if (applied == null || applied.isEmpty()) {
            return;
        }

        for (Map.Entry<Identifier, String> modifierEntry : applied.entrySet()) {
            Holder<Attribute> attributeHolder = resolveAttribute(modifierEntry.getValue());
            if (attributeHolder == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attributeHolder);
            if (instance != null) {
                instance.removeModifier(modifierEntry.getKey());
            }
        }
    }
}