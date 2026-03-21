package com.github.bandithelps.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YhaNetwork {
    private YhaNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenGeneExperimentScreenPayload.TYPE, OpenGeneExperimentScreenPayload.STREAM_CODEC, OpenGeneExperimentScreenPayload::handle);
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC, StaminaSyncPayload::handle);
    }
}
