package dev.sirbepy.mc.claudebridge.command;

import io.papermc.paper.command.brigadier.Commands;

/**
 * Registers the plain-string {@code /shenron} command, mirroring Names' CommandRegistrar, plus
 * the console-only {@code /claudebridge heartbeat} Brigadier command.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void register(Commands commands, ClaudeCommand claudeCommand, HeartbeatCommand heartbeatCommand) {
        commands.register("shenron", "Send a message to Claude.", claudeCommand);
        heartbeatCommand.registerInto(commands);
    }
}
