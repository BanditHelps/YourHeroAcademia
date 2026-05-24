package com.github.bandithelps.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YhaNetwork {
    private YhaNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(AttackDamageSyncPayload.TYPE, AttackDamageSyncPayload.STREAM_CODEC, AttackDamageSyncPayload::handle);
        registrar.playToClient(OpenGeneExperimentScreenPayload.TYPE, OpenGeneExperimentScreenPayload.STREAM_CODEC, OpenGeneExperimentScreenPayload::handle);
        registrar.playToClient(OpenBodyDebugScreenPayload.TYPE, OpenBodyDebugScreenPayload.STREAM_CODEC, OpenBodyDebugScreenPayload::handle);
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC, StaminaSyncPayload::handle);
        registrar.playToClient(StaminaDebugOverlayPayload.TYPE, StaminaDebugOverlayPayload.STREAM_CODEC, StaminaDebugOverlayPayload::handle);
        registrar.playToClient(BodySyncPayload.TYPE, BodySyncPayload.STREAM_CODEC, BodySyncPayload::handle);
        registrar.playToClient(TreadmillMountStatePayload.TYPE, TreadmillMountStatePayload.STREAM_CODEC, TreadmillMountStatePayload::handle);
        registrar.playToClient(TreadmillMinigameStatePayload.TYPE, TreadmillMinigameStatePayload.STREAM_CODEC, TreadmillMinigameStatePayload::handle);
        registrar.playToServer(TreadmillMinigameInputPayload.TYPE, TreadmillMinigameInputPayload.STREAM_CODEC, TreadmillMinigameInputPayload::handle);
        registrar.playToClient(DNASyncPayload.TYPE, DNASyncPayload.STREAM_CODEC, DNASyncPayload::handle);
        registrar.playToClient(GeneAliasSyncPayload.TYPE, GeneAliasSyncPayload.STREAM_CODEC, GeneAliasSyncPayload::handle);
        registrar.playToClient(DNAAnalyzerSyncPayload.TYPE, DNAAnalyzerSyncPayload.STREAM_CODEC, DNAAnalyzerSyncPayload::handle);
        registrar.playToClient(OpenDNAAnalyzerScreenPayload.TYPE, OpenDNAAnalyzerScreenPayload.STREAM_CODEC, OpenDNAAnalyzerScreenPayload::handle);
        registrar.playToServer(DNAAnalyzerExtractPayload.TYPE, DNAAnalyzerExtractPayload.STREAM_CODEC, DNAAnalyzerExtractPayload::handle);
        registrar.playToServer(DNAAnalyzerRenamePayload.TYPE, DNAAnalyzerRenamePayload.STREAM_CODEC, DNAAnalyzerRenamePayload::handle);
        registrar.playToClient(DNASplicerSyncPayload.TYPE, DNASplicerSyncPayload.STREAM_CODEC, DNASplicerSyncPayload::handle);
        registrar.playToServer(DNASplicerCreateInjectorPayload.TYPE, DNASplicerCreateInjectorPayload.STREAM_CODEC, DNASplicerCreateInjectorPayload::handle);
        registrar.playToClient(GeneCombinerSyncPayload.TYPE, GeneCombinerSyncPayload.STREAM_CODEC, GeneCombinerSyncPayload::handle);
        registrar.playToServer(GeneCombinerStartPayload.TYPE, GeneCombinerStartPayload.STREAM_CODEC, GeneCombinerStartPayload::handle);
        registrar.playToServer(GeneCombinerTransferPayload.TYPE, GeneCombinerTransferPayload.STREAM_CODEC, GeneCombinerTransferPayload::handle);
        registrar.playToClient(BioPrinterSyncPayload.TYPE, BioPrinterSyncPayload.STREAM_CODEC, BioPrinterSyncPayload::handle);
        registrar.playToServer(BioPrinterImportPayload.TYPE, BioPrinterImportPayload.STREAM_CODEC, BioPrinterImportPayload::handle);
        registrar.playToServer(BioPrinterTransferPayload.TYPE, BioPrinterTransferPayload.STREAM_CODEC, BioPrinterTransferPayload::handle);
        registrar.playToServer(BioPrinterPrintPayload.TYPE, BioPrinterPrintPayload.STREAM_CODEC, BioPrinterPrintPayload::handle);
        registrar.playToClient(GeneCombinationBrowserDataPayload.TYPE, GeneCombinationBrowserDataPayload.STREAM_CODEC, GeneCombinationBrowserDataPayload::handle);
        registrar.playToClient(OpenGeneCombinationBrowserPayload.TYPE, OpenGeneCombinationBrowserPayload.STREAM_CODEC, OpenGeneCombinationBrowserPayload::handle);
    }
}
