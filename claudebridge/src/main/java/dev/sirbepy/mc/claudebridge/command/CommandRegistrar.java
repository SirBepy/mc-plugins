package dev.sirbepy.mc.claudebridge.command;

import io.papermc.paper.command.brigadier.Commands;

/** Registers the plain-string {@code /shenron} command, mirroring Names' CommandRegistrar. */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void register(Commands commands, ClaudeCommand claudeCommand) {
        commands.register("shenron", "Send a message to Claude.", claudeCommand);
    }
}
