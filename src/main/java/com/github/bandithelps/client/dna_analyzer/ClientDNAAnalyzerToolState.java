package com.github.bandithelps.client.dna_analyzer;

public final class ClientDNAAnalyzerToolState {
    public static final String TOOL_RENAME = "rename";
    public static final String TOOL_ISOLATE = "isolate";
    private static volatile String activeToolId = "";

    private ClientDNAAnalyzerToolState() {
    }

    public static String getActiveToolId() {
        return activeToolId;
    }

    public static boolean isActive(String toolId) {
        return toolId != null && !toolId.isBlank() && toolId.equals(activeToolId);
    }

    public static boolean isRenameEnabled() {
        return isActive(TOOL_RENAME);
    }

    public static void setActiveTool(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            activeToolId = "";
            return;
        }
        activeToolId = toolId;
    }

    public static void toggleTool(String toolId) {
        if (isActive(toolId)) {
            activeToolId = "";
            return;
        }
        setActiveTool(toolId);
    }

    public static void clear() {
        activeToolId = "";
    }
}
