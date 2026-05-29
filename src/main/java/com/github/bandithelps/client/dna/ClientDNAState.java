package com.github.bandithelps.client.dna;

public final class ClientDNAState {
    private static volatile String dna = "";
    private static volatile boolean hasDNA;
    private static volatile int intelligence;
    private static volatile boolean dnaFatigued;

    private ClientDNAState() {
    }

    public static String getDNA() {
        return dna;
    }

    public static boolean hasDNA() {
        return hasDNA;
    }

    public static int getIntelligence() {
        return intelligence;
    }

    public static boolean isDNAFatigued() {
        return dnaFatigued;
    }

    public static void set(
            String dna,
            boolean hasDNA,
            int intelligence,
            boolean dnaFatigued
    ) {
        ClientDNAState.dna = dna != null ? dna : "";
        ClientDNAState.hasDNA = hasDNA;
        ClientDNAState.intelligence = Math.max(0, Math.min(100, intelligence));
        ClientDNAState.dnaFatigued = dnaFatigued;
    }
}