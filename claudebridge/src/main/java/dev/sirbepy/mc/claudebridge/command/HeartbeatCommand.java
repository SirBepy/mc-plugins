package dev.sirbepy.mc.claudebridge.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.claudebridge.heartbeat.HeartbeatTracker;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

/**
 * {@code /claudebridge heartbeat} (console/RCON only: server/deploy/player-claude.mjs's daemon is
 * the only caller, pushed roughly every 15s while it's alive on Joe's PC). Player-invisible by
 * design, so a player can never spoof or observe Shenron's online state directly through chat.
 */
public final class HeartbeatCommand {

    private final HeartbeatTracker tracker;

    public HeartbeatCommand(HeartbeatTracker tracker) {
        this.tracker = tracker;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("claudebridge")
                        .requires(HeartbeatCommand::isConsole)
                        .then(Commands.literal("heartbeat").executes(this::heartbeat))
                        .build(),
                "Console-only: player-claude.mjs's heartbeat push (dev.sirbepy.mc.claudebridge)");
    }

    private static boolean isConsole(CommandSourceStack source) {
        return source.getSender() instanceof ConsoleCommandSender || source.getSender() instanceof RemoteConsoleCommandSender;
    }

    private int heartbeat(CommandContext<CommandSourceStack> c) {
        tracker.recordHeartbeat();
        return Command.SINGLE_SUCCESS;
    }
}
