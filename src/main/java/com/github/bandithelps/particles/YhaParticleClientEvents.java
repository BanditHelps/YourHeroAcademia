package com.github.bandithelps.particles;

import com.github.bandithelps.YourHeroAcademia;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class YhaParticleClientEvents {
    private YhaParticleClientEvents() {
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(YhaParticles.STAGNANT_SMOKE.get(), StagnantSmokeParticle.Provider::new);
        event.registerSpriteSet(YhaParticles.MANAGED_SMOKE.get(), ManagedSmokeParticle.Provider::new);
    }
}
