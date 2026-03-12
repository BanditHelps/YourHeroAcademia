package com.github.bandithelps.gui.gene.data;

import com.github.bandithelps.gui.gene.model.NodeKind;

public record NodeTemplate(
        String templateId,
        String title,
        String category,
        NodeKind kind,
        int color,
        int inputCount,
        int outputCount,
        String dataType
) {
}
