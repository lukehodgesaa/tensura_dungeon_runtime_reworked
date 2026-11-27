package com.mineclawd.tensuradungeon.runtime.generation;

import java.util.Set;

public record DungeonTemplateMetadata(
    String id,
    boolean enabled,
    Set<DungeonRoomRole> roles,
    Set<String> themes,
    String motif,
    String sizeClass,
    int weight,
    int repeatLimit,
    boolean startAllowed,
    boolean terminalAllowed,
    boolean alwaysSafe,
    boolean setPieceEligible,
    int setPieceCost,
    String exclusionReason,
    String visualTheme
) {
    public DungeonTemplateMetadata(
        String id,
        boolean enabled,
        Set<DungeonRoomRole> roles,
        Set<String> themes,
        String sizeClass,
        int weight,
        int repeatLimit,
        boolean startAllowed,
        boolean terminalAllowed,
        boolean alwaysSafe,
        boolean setPieceEligible,
        int setPieceCost,
        String exclusionReason,
        String visualTheme
    ) {
        this(id, enabled, roles, themes, inferMotif(themes), sizeClass, weight, repeatLimit,
            startAllowed, terminalAllowed, alwaysSafe, setPieceEligible, setPieceCost,
            exclusionReason, visualTheme);
    }

    public DungeonTemplateMetadata(
        String id,
        boolean enabled,
        Set<DungeonRoomRole> roles,
        Set<String> themes,
        String sizeClass,
        int weight,
        int repeatLimit,
        boolean startAllowed,
        boolean terminalAllowed,
        boolean alwaysSafe,
        boolean setPieceEligible,
        int setPieceCost,
        String exclusionReason
    ) {
        this(id, enabled, roles, themes, inferMotif(themes), sizeClass, weight, repeatLimit,
            startAllowed, terminalAllowed, alwaysSafe, setPieceEligible, setPieceCost,
            exclusionReason, "legacy");
    }

    public DungeonTemplateMetadata {
        id = id == null ? "" : id.trim();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        themes = themes == null ? Set.of() : Set.copyOf(themes);
        motif = motif == null || motif.isBlank() ? inferMotif(themes)
            : motif.trim().toLowerCase(java.util.Locale.ROOT);
        sizeClass = sizeClass == null || sizeClass.isBlank() ? "MEDIUM" : sizeClass;
        weight = Math.max(1, weight);
        repeatLimit = Math.max(0, repeatLimit);
        setPieceCost = setPieceEligible ? Math.max(1, setPieceCost) : 0;
        exclusionReason = exclusionReason == null || exclusionReason.isBlank() ? "NONE" : exclusionReason;
        visualTheme = visualTheme == null || visualTheme.isBlank() ? "legacy" : visualTheme.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String inferMotif(Set<String> themes) {
        if (themes == null || themes.isEmpty()) return "unclassified";
        return themes.stream()
            .filter(theme -> theme != null && !theme.isBlank())
            .map(theme -> theme.trim().toLowerCase(java.util.Locale.ROOT))
            .sorted()
            .findFirst()
            .orElse("unclassified");
    }
}
