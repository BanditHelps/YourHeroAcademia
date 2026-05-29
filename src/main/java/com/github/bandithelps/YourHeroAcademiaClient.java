package com.github.bandithelps;

import com.github.bandithelps.gui.ui.components.YhaUiComponentSerializers;
import com.github.bandithelps.gui.ui.layouts.YhaUiLayoutSerializers;
import com.github.bandithelps.particles.ModParticles;
import com.github.bandithelps.particles.SmokescreenParticle;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = YourHeroAcademia.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public class YourHeroAcademiaClient {

    public YourHeroAcademiaClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        YhaUiComponentSerializers.init();
        YhaUiLayoutSerializers.init();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        YourHeroAcademia.LOGGER.info("HELLO FROM CLIENT SETUP");
        YourHeroAcademia.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SMOKESCREEN.get(), SmokescreenParticle.Provider::new);
    }
}