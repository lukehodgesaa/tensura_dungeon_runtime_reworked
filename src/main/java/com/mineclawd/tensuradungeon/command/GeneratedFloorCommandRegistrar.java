package com.mineclawd.tensuradungeon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

final class GeneratedFloorCommandRegistrar {
    private GeneratedFloorCommandRegistrar() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return GeneratedLinearFloorDebugCommands.register();
    }
}
