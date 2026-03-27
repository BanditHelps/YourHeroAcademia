package com.github.bandithelps.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YhaNetwork {
    private YhaNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenGeneExperimentScreenPayload.TYPE, OpenGeneExperimentScreenPayload.STREAM_CODEC, OpenGeneExperimentScreenPayload::handle);
        registrar.playToClient(OpenBodyDebugScreenPayload.TYPE, OpenBodyDebugScreenPayload.STREAM_CODEC, OpenBodyDebugScreenPayload::handle);
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC, StaminaSyncPayload::handle);
        registrar.playToClient(StaminaDebugOverlayPayload.TYPE, StaminaDebugOverlayPayload.STREAM_CODEC, StaminaDebugOverlayPayload::handle);
        registrar.playToClient(BodySyncPayload.TYPE, BodySyncPayload.STREAM_CODEC, BodySyncPayload::handle);
        registrar.playToClient(CloudVolumeSpawnPayload.TYPE, CloudVolumeSpawnPayload.STREAM_CODEC, CloudVolumeSpawnPayload::handle);
        registrar.playToClient(CloudVolumeDeltaPayload.TYPE, CloudVolumeDeltaPayload.STREAM_CODEC, CloudVolumeDeltaPayload::handle);
        registrar.playToClient(CloudVolumeRemovePayload.TYPE, CloudVolumeRemovePayload.STREAM_CODEC, CloudVolumeRemovePayload::handle);
    }
}
