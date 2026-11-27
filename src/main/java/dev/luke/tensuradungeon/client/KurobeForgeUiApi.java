package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.function.Consumer;

public final class KurobeForgeUiApi {
    private static Consumer<KurobeForgeView> openAction = view -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof KurobeForgeScreen screen) {
            screen.applyView(view);
        } else {
            minecraft.setScreen(new KurobeForgeScreen(view, minecraft.screen));
        }
    };

    private KurobeForgeUiApi() {}

    public static void open(KurobeForgeView view) {
        openAction.accept(Objects.requireNonNull(view, "Kurobe Forge view"));
    }

    public static void openVisualPreview() {
        DungeonClientSoundEffects.playMenuOpen();
        open(KurobeForgeInventoryProjection.visualPreview());
    }

    public static void setOpenAction(Consumer<KurobeForgeView> action) {
        openAction = Objects.requireNonNull(action, "Kurobe Forge open action");
    }
}
