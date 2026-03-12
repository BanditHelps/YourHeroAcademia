package com.github.bandithelps.gui.gene.data;

import com.github.bandithelps.gui.gene.model.NodeKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FakeGeneCatalog {
    private FakeGeneCatalog() {
    }

    public static List<NodeTemplate> entries() {
        return List.of(
                new NodeTemplate("gene_strength", "Strength Gene", "Physical", NodeKind.GENE, 0xFFC64545, 0, 1, "gene"),
                new NodeTemplate("gene_speed", "Speed Gene", "Physical", NodeKind.GENE, 0xFF44A7E6, 0, 1, "gene"),
                new NodeTemplate("gene_regen", "Regen Gene", "Physical", NodeKind.GENE, 0xFF5ECF7D, 0, 1, "gene"),
                new NodeTemplate("gene_focus", "Focus Gene", "Mental", NodeKind.GENE, 0xFFE69D44, 0, 1, "gene"),
                new NodeTemplate("gene_stability", "Stability Gene", "Utility", NodeKind.GENE, 0xFF7A65D6, 0, 1, "gene"),
                new NodeTemplate("gene_precision", "Precision Gene", "Mental", NodeKind.GENE, 0xFF4CA3CC, 0, 1, "gene"),
                new NodeTemplate("gene_resistance", "Resistance Gene", "Utility", NodeKind.GENE, 0xFF8D59D3, 0, 1, "gene")
        );
    }

    public static Map<String, List<NodeTemplate>> groupedByCategory() {
        Map<String, List<NodeTemplate>> grouped = new LinkedHashMap<>();
        for (NodeTemplate template : entries()) {
            grouped.computeIfAbsent(template.category(), ignored -> new java.util.ArrayList<>()).add(template);
        }
        return grouped;
    }
}
