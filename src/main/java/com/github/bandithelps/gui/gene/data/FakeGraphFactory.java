package com.github.bandithelps.gui.gene.data;

import com.github.bandithelps.gui.gene.model.GeneGraphDocument;

public final class FakeGraphFactory {
    private FakeGraphFactory() {
    }

    public static GeneGraphDocument createEmpty() {
        return new GeneGraphDocument();
    }
}
