package com.github.bandithelps.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YhaNetwork {
    private YhaNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("9");
        registrar.playToClient(AttackDamageSyncPayload.TYPE, AttackDamageSyncPayload.STREAM_CODEC, AttackDamageSyncPayload::handle);
        registrar.playToClient(OpenBodyDebugScreenPayload.TYPE, OpenBodyDebugScreenPayload.STREAM_CODEC, OpenBodyDebugScreenPayload::handle);
        registrar.playToClient(OpenTreeEditorScreenPayload.TYPE, OpenTreeEditorScreenPayload.STREAM_CODEC, OpenTreeEditorScreenPayload::handle);
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC, StaminaSyncPayload::handle);
        registrar.playToClient(StaminaDebugOverlayPayload.TYPE, StaminaDebugOverlayPayload.STREAM_CODEC, StaminaDebugOverlayPayload::handle);
        registrar.playToClient(BodySyncPayload.TYPE, BodySyncPayload.STREAM_CODEC, BodySyncPayload::handle);
        registrar.playToClient(TreadmillMountStatePayload.TYPE, TreadmillMountStatePayload.STREAM_CODEC, TreadmillMountStatePayload::handle);
        registrar.playToClient(TreadmillMinigameStatePayload.TYPE, TreadmillMinigameStatePayload.STREAM_CODEC, TreadmillMinigameStatePayload::handle);
        registrar.playToServer(TreadmillMinigameInputPayload.TYPE, TreadmillMinigameInputPayload.STREAM_CODEC, TreadmillMinigameInputPayload::handle);
        registrar.playToClient(DNASyncPayload.TYPE, DNASyncPayload.STREAM_CODEC, DNASyncPayload::handle);
        registrar.playToClient(GeneAliasSyncPayload.TYPE, GeneAliasSyncPayload.STREAM_CODEC, GeneAliasSyncPayload::handle);
        registrar.playToClient(DNAAnalyzerSyncPayload.TYPE, DNAAnalyzerSyncPayload.STREAM_CODEC, DNAAnalyzerSyncPayload::handle);
        registrar.playToServer(DNAAnalyzerExtractPayload.TYPE, DNAAnalyzerExtractPayload.STREAM_CODEC, DNAAnalyzerExtractPayload::handle);
        registrar.playToServer(DNAAnalyzerRenamePayload.TYPE, DNAAnalyzerRenamePayload.STREAM_CODEC, DNAAnalyzerRenamePayload::handle);
        registrar.playToClient(GeneCombinerSyncPayload.TYPE, GeneCombinerSyncPayload.STREAM_CODEC, GeneCombinerSyncPayload::handle);
        registrar.playToServer(GeneCombinerStartPayload.TYPE, GeneCombinerStartPayload.STREAM_CODEC, GeneCombinerStartPayload::handle);
        registrar.playToServer(GeneCombinerTransferPayload.TYPE, GeneCombinerTransferPayload.STREAM_CODEC, GeneCombinerTransferPayload::handle);
        registrar.playToClient(BioPrinterSyncPayload.TYPE, BioPrinterSyncPayload.STREAM_CODEC, BioPrinterSyncPayload::handle);
        registrar.playToServer(BioPrinterImportPayload.TYPE, BioPrinterImportPayload.STREAM_CODEC, BioPrinterImportPayload::handle);
        registrar.playToServer(BioPrinterTransferPayload.TYPE, BioPrinterTransferPayload.STREAM_CODEC, BioPrinterTransferPayload::handle);
        registrar.playToServer(BioPrinterPrintPayload.TYPE, BioPrinterPrintPayload.STREAM_CODEC, BioPrinterPrintPayload::handle);
        registrar.playToClient(GeneCombinationBrowserDataPayload.TYPE, GeneCombinationBrowserDataPayload.STREAM_CODEC, GeneCombinationBrowserDataPayload::handle);
        registrar.playToClient(BlackwhipChainSwingPayload.TYPE, BlackwhipChainSwingPayload.STREAM_CODEC, BlackwhipChainSwingPayload::handle);
        registrar.playToClient(BlackwhipWebSwingPayload.TYPE, BlackwhipWebSwingPayload.STREAM_CODEC, BlackwhipWebSwingPayload::handle);
        registrar.playToServer(BlackwhipWebSwingVelocityPayload.TYPE, BlackwhipWebSwingVelocityPayload.STREAM_CODEC, BlackwhipWebSwingVelocityPayload::handle);
        registrar.playToServer(BlackwhipWebSwingBreakPayload.TYPE, BlackwhipWebSwingBreakPayload.STREAM_CODEC, BlackwhipWebSwingBreakPayload::handle);
        registrar.playToClient(BlackwhipChainZipAnimPayload.TYPE, BlackwhipChainZipAnimPayload.STREAM_CODEC, BlackwhipChainZipAnimPayload::handle);
        registrar.playToClient(BlackwhipChainChargeZipPayload.TYPE, BlackwhipChainChargeZipPayload.STREAM_CODEC, BlackwhipChainChargeZipPayload::handle);
        registrar.playToClient(BlackwhipChainLeadPayload.TYPE, BlackwhipChainLeadPayload.STREAM_CODEC, BlackwhipChainLeadPayload::handle);
        registrar.playToClient(BlackwhipChainReelSessionPayload.TYPE, BlackwhipChainReelSessionPayload.STREAM_CODEC, BlackwhipChainReelSessionPayload::handle);
        registrar.playToServer(BlackwhipChainReelScrollPayload.TYPE, BlackwhipChainReelScrollPayload.STREAM_CODEC, BlackwhipChainReelScrollPayload::handle);
        registrar.playToClient(AbilityLoadoutSyncPayload.TYPE, AbilityLoadoutSyncPayload.STREAM_CODEC, AbilityLoadoutSyncPayload::handle);
        registrar.playToServer(AbilityLoadoutAssignPayload.TYPE, AbilityLoadoutAssignPayload.STREAM_CODEC, AbilityLoadoutAssignPayload::handle);
        registrar.playToServer(AbilityLoadoutModeSelectPayload.TYPE, AbilityLoadoutModeSelectPayload.STREAM_CODEC, AbilityLoadoutModeSelectPayload::handle);
        registrar.playToClient(CreationSyncPayload.TYPE, CreationSyncPayload.STREAM_CODEC, CreationSyncPayload::handle);
        registrar.playToServer(CreationResearchPayload.TYPE, CreationResearchPayload.STREAM_CODEC, CreationResearchPayload::handle);
        registrar.playToServer(CreationCreatePayload.TYPE, CreationCreatePayload.STREAM_CODEC, CreationCreatePayload::handle);
        registrar.playToServer(CreationAssignSlotPayload.TYPE, CreationAssignSlotPayload.STREAM_CODEC, CreationAssignSlotPayload::handle);
    }
}
