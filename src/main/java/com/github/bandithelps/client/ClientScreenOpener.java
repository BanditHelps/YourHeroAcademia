package com.github.bandithelps.client;

import com.github.bandithelps.gui.screens.BodyDebugScreen;
import com.github.bandithelps.gui.screens.DNAAnalyzerScreen;
import com.github.bandithelps.gui.screens.GeneCombinationBrowserScreen;
import com.github.bandithelps.gui.screens.GeneExperimentsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientScreenOpener {
    private ClientScreenOpener() {
    }

    public static void openGeneScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new GeneExperimentsScreen());
    }

    public static void openBodyDebugScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new BodyDebugScreen());
    }

    public static void openDNAAnalyzerScreen(BlockPos blockPos) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DNAAnalyzerScreen(blockPos));
    }

    public static void openGeneCombinationBrowser() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new GeneCombinationBrowserScreen());
    }
}
