package com.github.bandithelps.client;

import com.github.bandithelps.gui.screens.ExampleScreen;
import com.github.bandithelps.gui.screens.GeneExperimentsScreen;
import net.minecraft.client.Minecraft;

public final class ClientScreenOpener {
    private ClientScreenOpener() {
    }

    public static void openGeneScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new GeneExperimentsScreen());
    }
}
