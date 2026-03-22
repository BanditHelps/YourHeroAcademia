package com.github.bandithelps.client;

import com.github.bandithelps.gui.screens.BodyDebugScreen;
import com.github.bandithelps.gui.screens.GeneExperimentsScreen;
import net.minecraft.client.Minecraft;

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
}
