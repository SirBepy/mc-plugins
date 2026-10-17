package dev.sirbepy.mc.tweaks.restart;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /restartcountdown start|status|cancel} (console/RCON only: kinetic.mjs's {@code restart}
 * subcommand is the only caller) and the player-facing {@code /dont-restart}.
 */
public final class RestartCommand {

    static final String DONT_RESTART_PERMISSION = "tweaks.dontrestart";

    private final RestartCountdownManager manager;

    public RestartCommand(RestartCountdownManager manager) {
        this.manager = manager;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("restartcountdown")
                .requires(RestartCommand::isConsole)
                .then(Commands.literal("start")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(this::start))))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("cancel").executes(this::cancel))
                .build(), "Console-only: drive the restart-ASAP countdown (kinetic.mjs's restart subcommand)");

        registrar.register(Commands.literal("dont-restart")
                .requires(src -> src.getSender().hasPermission(DONT_RESTART_PERMISSION))
                .executes(this::dontRestart)
                .build(), "Push the pending server restart back up to an hour");
    }

    private static boolean isConsole(CommandSourceStack source) {
        return source.getSender() instanceof ConsoleCommandSender || source.getSender() instanceof RemoteConsoleCommandSender;
    }

    private int start(CommandContext<CommandSourceStack> c) {
        String token = StringArgumentType.getString(c, "token");
        String reason = StringArgumentType.getString(c, "reason");
        manager.start(c.getSource().getSender(), token, reason);
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> c) {
        manager.status(c.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private int cancel(CommandContext<CommandSourceStack> c) {
        manager.cancel(c.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private int dontRestart(CommandContext<CommandSourceStack> c) {
        if (c.getSource().getSender() instanceof Player player) {
            manager.dontRestart(player);
        }
        return Command.SINGLE_SUCCESS;
    }
}
