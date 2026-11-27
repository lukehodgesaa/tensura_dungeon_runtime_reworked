package com.mineclawd.tensurahudnative.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class ClientHudCommands {
    private ClientHudCommands() {
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> theme = Commands.literal("theme")
                .then(themeChoice(HudThemes.GOTHIC_ID))
                .then(themeChoice(HudThemes.FREY_ID))
                .then(themeChoice(HudThemes.RIMURU_ID))
                .then(themeChoice(HudThemes.BENIMARU_ID))
                .then(themeChoice(HudThemes.SOUEI_ID))
                .then(themeChoice(HudThemes.VANILLA_ID))
                .then(Commands.literal("automatic").executes(context -> useAutomaticTheme()));

        event.getDispatcher().register(
                Commands.literal("tensurahud")
                        .then(theme)
                        .then(Commands.literal("tracker")
                                .then(Commands.literal("show").executes(context -> selectTracker(true)))
                                .then(Commands.literal("hide").executes(context -> selectTracker(false))))
                        .then(Commands.literal("dialogue")
                                .then(Commands.literal("vanilla").executes(context -> selectDialogue(false)))
                                .then(Commands.literal("themed").executes(context -> selectDialogue(true))))
                        .executes(context -> showUsage())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> themeChoice(String themeId) {
        return Commands.literal(themeId).executes(context -> selectTheme(themeId));
    }

    private static int selectTheme(String themeId) {
        HudSettings settings = HudSettings.get();
        HudTheme theme = HudThemes.resolve(themeId);
        settings.hudTheme = theme.id();
        settings.questTheme = QuestThemes.INHERIT_ID;
        settings.testLocalThemeOverride = true;
        settings.save();
        message("Tensura HUD: " + theme.displayName() + " selected.");
        return 1;
    }

    private static int useAutomaticTheme() {
        HudSettings settings = HudSettings.get();
        settings.testLocalThemeOverride = false;
        settings.save();
        message("Tensura HUD: automatic. Tensura's own HUD outside runs, the equipped dungeon HUD during runs.");
        return 1;
    }

    private static int selectTracker(boolean show) {
        HudSettings settings = HudSettings.get();
        settings.showQuestTracker = show;
        settings.save();
        message(show ? "Dungeon quest tracker: shown." : "Dungeon quest tracker: hidden.");
        return 1;
    }

    private static int selectDialogue(boolean themed) {
        HudSettings settings = HudSettings.get();
        settings.themedDialogue = themed;
        settings.save();
        message(themed ? "NPC dialogue: themed dungeon panel." : "NPC dialogue: vanilla box.");
        return 1;
    }

    private static int showUsage() {
        message("Use /tensurahud theme <gothic|frey|rimuru|benimaru|souei|vanilla|automatic>, /tensurahud tracker <show|hide> or /tensurahud dialogue <vanilla|themed>");
        return 1;
    }

    private static void message(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(text), false);
        }
    }
}
