package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonCosmeticActionPayload;
import com.mineclawd.tensuradungeon.network.DungeonCosmeticSnapshotPayload;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonCosmeticClient {
    private static final long HUD_PREVIEW_DURATION_MS = 30_000L;
    private static volatile DungeonCosmeticSnapshotPayload latest;
    private static volatile HudPreview hudPreview;
    private static volatile boolean previewBridgeDirty;

    private DungeonCosmeticClient() {}

    public static void handle(DungeonCosmeticSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> accept(payload));
    }

    static synchronized boolean accept(DungeonCosmeticSnapshotPayload payload) {
        if (payload == null || !payload.supportedSchema()) {
            return false;
        }
        if (latest != null && payload.revision() < latest.revision()) {
            return false;
        }
        latest = payload;
        return true;
    }

    public static void requestEquip(CosmeticKey cosmetic) {
        DungeonCosmeticSnapshotPayload snapshot = latest;
        long revision = snapshot == null ? 0L : snapshot.revision();
        PacketDistributor.sendToServer(new DungeonCosmeticActionPayload(
            DungeonCosmeticActionPayload.Action.EQUIP,
            cosmetic,
            revision
        ));
    }

    public static boolean startHudPreview(String offerId) {
        return startHudPreviewAt(offerId, System.currentTimeMillis());
    }

    public static boolean isHudPreviewActive() {
        return activePreview(System.currentTimeMillis()) != null;
    }

    public static int hudPreviewRemainingSeconds() {
        long now = System.currentTimeMillis();
        HudPreview preview = activePreview(now);
        if (preview == null) {
            return 0;
        }
        long remaining = preview.endsAt() - now;
        return (int) Math.max(1L, (remaining + 999L) / 1_000L);
    }

    public static void cancelHudPreview() {
        hudPreview = null;
        previewBridgeDirty = true;
    }

    public static Map<String, Object> snapshot() {
        return snapshotAt(System.currentTimeMillis());
    }

    private static Map<String, Object> snapshotAt(long now) {
        DungeonCosmeticSnapshotPayload snapshot = latest;
        HudPreview preview = activePreview(now);
        if (snapshot == null) {
            if (preview == null && !previewBridgeDirty) {
                return Map.of();
            }
            if (preview == null) {
                previewBridgeDirty = false;
            }
            return Map.of(
                "revision", 0L,
                "hudTheme", "",
                "questTheme", CosmeticKey.QUEST_INHERIT.id(),
                "owned", List.of(),
                "hudPreviewTheme", preview == null ? "" : preview.key().id()
            );
        }
        previewBridgeDirty = preview != null;
        List<String> owned = snapshot.owned().stream().map(CosmeticKey::canonicalId).toList();
        return Map.of(
            "revision", snapshot.revision(),
            "hudTheme", snapshot.equippedHud().id(),
            "questTheme", snapshot.equippedQuest().id(),
            "owned", owned,
            "hudPreviewTheme", preview == null ? "" : preview.key().id()
        );
    }

    static synchronized void resetForTests() {
        latest = null;
        hudPreview = null;
        previewBridgeDirty = false;
    }

    static boolean startHudPreviewAt(String offerId, long now) {
        Optional<CosmeticKey> key = resolveHudPreviewKey(offerId);
        if (key.isEmpty()) {
            return false;
        }
        hudPreview = new HudPreview(key.get(), now + HUD_PREVIEW_DURATION_MS);
        previewBridgeDirty = true;
        return true;
    }

    static boolean isHudPreviewActiveAt(long now) {
        return activePreview(now) != null;
    }

    static int hudPreviewRemainingSecondsAt(long now) {
        HudPreview preview = activePreview(now);
        if (preview == null) {
            return 0;
        }
        long remaining = preview.endsAt() - now;
        return (int) Math.max(1L, (remaining + 999L) / 1_000L);
    }

    static Map<String, Object> snapshotAtForTests(long now) {
        return snapshotAt(now);
    }

    private static synchronized HudPreview activePreview(long now) {
        HudPreview preview = hudPreview;
        if (preview != null && now >= preview.endsAt()) {
            hudPreview = null;
            return null;
        }
        return preview;
    }

    private static Optional<CosmeticKey> resolveHudPreviewKey(String offerId) {
        Optional<CosmeticKey> canonical = CosmeticKey.fromCanonicalId(offerId)
            .filter(key -> key.type() == CosmeticKey.Type.HUD_THEME && key.cataloged());
        if (canonical.isPresent()) {
            return canonical;
        }
        String normalized = offerId == null ? "" : offerId.trim().toLowerCase(Locale.ROOT);
        String prefix = "tensura_dungeon:";
        String suffix = "_hud_theme";
        if (!normalized.startsWith(prefix) || !normalized.endsWith(suffix)) {
            return Optional.empty();
        }
        String id = normalized.substring(prefix.length(), normalized.length() - suffix.length());
        try {
            CosmeticKey key = new CosmeticKey(CosmeticKey.Type.HUD_THEME, id);
            return key.cataloged() ? Optional.of(key) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private record HudPreview(CosmeticKey key, long endsAt) {
    }
}
