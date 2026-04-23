package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class DNAAnalyzerTickHandler {
    private DNAAnalyzerTickHandler() {
    }
}