package com.github.bandithelps.gui.gene.data;

import com.github.bandithelps.gui.gene.model.NodeKind;

import java.util.List;

public final class FakeMachineCatalog {
    private FakeMachineCatalog() {
    }

    public static List<NodeTemplate> entries() {
        return List.of(
                new NodeTemplate("combiner", "Combiner", "Machines", NodeKind.MACHINE, 0xFF4C7DFF, 2, 1, "gene"),
                new NodeTemplate("sequencer", "Sequencer", "Machines", NodeKind.MACHINE, 0xFF8866FF, 1, 1, "gene"),
                new NodeTemplate("stabilizer", "Stabilizer", "Machines", NodeKind.MACHINE, 0xFF4FB78D, 1, 1, "gene"),
                new NodeTemplate("extractor", "Extractor", "Machines", NodeKind.MACHINE, 0xFFB77F36, 1, 2, "gene")
        );
    }
}
