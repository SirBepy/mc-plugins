package dev.sirbepy.mc.claudebridge.command;

import io.papermc.paper.command.brigadier.Commands;

/** Registers the plain-string {@code /claude} command, mirroring Names' CommandRegistrar. */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void register(Commands commands, ClaudeCommand claudeCommand) {
        commands.register("claude", "Send a message to Claude.", claudeCommand);
    }
}
