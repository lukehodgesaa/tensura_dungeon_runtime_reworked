package com.mineclawd.tensuradungeon.compat.tensura;

import java.util.List;

public record TensuraCompatStatus(
    IntegrationState integrationState,
    boolean tensuraModPresent,
    List<String> detectedModIds,
    String primaryModId,
    String tensuraVersion,
    String manascoreVersion,
    boolean compatInitialized,
    boolean skillGriefHookRegistered,
    boolean epRewardHookAvailable,
    boolean magiculeRewardHookAvailable,
    boolean auraRewardHookAvailable,
    boolean battlewillRewardHookAvailable,
    boolean skillRewardHookAvailable,
    boolean playerDataApiAvailable,
    String lastProbeError,
    boolean safeFallbackMode
) {
    public enum IntegrationState {
        ABSENT,
        AVAILABLE,
        BROKEN,
        UNSUPPORTED_VERSION
    }

    public static TensuraCompatStatus notInitialized() {
        return absent("NOT_INITIALIZED", false);
    }

    public static TensuraCompatStatus absent(String detail, boolean initialized) {
        return create(IntegrationState.ABSENT, false, List.of(), "NONE", "UNKNOWN", "UNKNOWN", initialized,
            false, normalize(detail, "NONE"));
    }

    public static TensuraCompatStatus unsupported(List<String> modules, String tensuraVersion, String manascoreVersion, String detail) {
        return create(IntegrationState.UNSUPPORTED_VERSION, true, modules, "tensura", tensuraVersion, manascoreVersion,
            true, false, normalize(detail, "UNSUPPORTED_VERSION"));
    }

    public static TensuraCompatStatus broken(List<String> modules, String tensuraVersion, String manascoreVersion, String detail) {
        return create(IntegrationState.BROKEN, true, modules, "tensura", tensuraVersion, manascoreVersion,
            true, false, normalize(detail, "BROKEN_ADAPTER"));
    }

    public static TensuraCompatStatus available(List<String> modules, String tensuraVersion, String manascoreVersion) {
        return create(IntegrationState.AVAILABLE, true, modules, "tensura", tensuraVersion, manascoreVersion,
            true, false, "NONE");
    }

    private static TensuraCompatStatus create(
        IntegrationState state,
        boolean present,
        List<String> detected,
        String primary,
        String tensuraVersion,
        String manascoreVersion,
        boolean initialized,
        boolean skillHook,
        String error
    ) {
        boolean available = state == IntegrationState.AVAILABLE;
        return new TensuraCompatStatus(
            state,
            present,
            List.copyOf(detected),
            primary,
            tensuraVersion,
            manascoreVersion,
            initialized,
            skillHook,
            available,
            available,
            available,
            false,
            false,
            available,
            error,
            !available
        );
    }

    public TensuraCompatStatus withSkillGriefHook(boolean registered) {
        return new TensuraCompatStatus(integrationState, tensuraModPresent, detectedModIds, primaryModId,
            tensuraVersion, manascoreVersion, compatInitialized, registered, epRewardHookAvailable,
            magiculeRewardHookAvailable, auraRewardHookAvailable, battlewillRewardHookAvailable,
            skillRewardHookAvailable, playerDataApiAvailable, lastProbeError,
            safeFallbackMode);
    }

    public String detectedModIdsSummary() {
        return summarize(detectedModIds);
    }

    public boolean anyMutationSafeEvidence() {
        return integrationState == IntegrationState.AVAILABLE;
    }

    public String hookEvidenceSummary() {
        return integrationState == IntegrationState.AVAILABLE ? "SUPPORTED_VERSION_CONTRACT" : integrationState.name();
    }

    public String lastProbeErrorSummary() {
        return normalize(lastProbeError, "NONE");
    }

    private static String summarize(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
