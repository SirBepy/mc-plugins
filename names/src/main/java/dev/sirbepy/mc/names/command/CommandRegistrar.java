package dev.sirbepy.mc.names.command;

import io.papermc.paper.command.brigadier.Commands;

/** Registers the plain-string {@code /color}, {@code /nick} and {@code /names} commands. */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void register(Commands commands, ColorCommand colorCommand, NickCommand nickCommand,
                                  NamesReloadCommand reloadCommand) {
        commands.register("color", "Open the name color picker.", colorCommand);
        commands.register("nick", "Set or edit your nickname.", nickCommand);
        commands.register("names", "Names plugin admin command.", reloadCommand);
    }
}
