package dev.luke.tensuradungeon.client;

import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.function.Consumer;

public final class DungeonUiApi {
    public enum ActionType {
        FLOOR_SELECTED,
        OPEN_ACTIVE_RUN,
        CLOSE
    }

    public record Action(ActionType type, int floor, String value) {}

    private static Runnable openAction = () -> Minecraft.getInstance().setScreen(new DungeonScreen());
    private static Consumer<Action> actionSink = action -> {};

    private DungeonUiApi() {}

    public static void open() {
        openAction.run();
    }

    public static void setOpenAction(Runnable action) {
        openAction = Objects.requireNonNull(action);
    }

    public static void setActionSink(Consumer<Action> sink) {
        actionSink = Objects.requireNonNull(sink);
    }

    public static void emit(ActionType type, int floor, String value) {
        actionSink.accept(new Action(type, floor, value == null ? "" : value));
    }
}
