package com.github.bandithelps.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YhaNetwork {
    private YhaNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(AttackDamageSyncPayload.TYPE, AttackDamageSyncPayload.STREAM_CODEC, AttackDamageSyncPayload::handle);
        registrar.playToClient(OpenGeneExperimentScreenPayload.TYPE, OpenGeneExperimentScreenPayload.STREAM_CODEC, OpenGeneExperimentScreenPayload::handle);
        registrar.playToClient(OpenBodyDebugScreenPayload.TYPE, OpenBodyDebugScreenPayload.STREAM_CODEC, OpenBodyDebugScreenPayload::handle);
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC, StaminaSyncPayload::handle);
        registrar.playToClient(StaminaDebugOverlayPayload.TYPE, StaminaDebugOverlayPayload.STREAM_CODEC, StaminaDebugOverlayPayload::handle);
        registrar.playToClient(BodySyncPayload.TYPE, BodySyncPayload.STREAM_CODEC, BodySyncPayload::handle);
        registrar.playToClient(TreadmillMountStatePayload.TYPE, TreadmillMountStatePayload.STREAM_CODEC, TreadmillMountStatePayload::handle);
        registrar.playToServer(MultiJumpRequestPayload.TYPE, MultiJumpRequestPayload.STREAM_CODEC, MultiJumpRequestPayload::handle);
    }
}
