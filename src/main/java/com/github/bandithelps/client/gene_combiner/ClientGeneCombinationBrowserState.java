package com.github.bandithelps.client.gene_combiner;

import java.util.ArrayList;
import java.util.List;

public final class ClientGeneCombinationBrowserState {
    private static volatile List<String> lines = List.of();

    private ClientGeneCombinationBrowserState() {
    }

    public static void setLines(List<String> newLines) {
        if (newLines == null) {
            lines = List.of();
            return;
        }
        lines = List.copyOf(new ArrayList<>(newLines));
    }

    public static List<String> getLines() {
        return lines;
    }
}
