package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.render.NameChangeService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** {@code /names reload}: re-reads config.yml and re-renders every online player. */
public final class NamesReloadCommand implements BasicCommand {

    private final NamesConfig config;
    private final NameChangeService changeService;

    public NamesReloadCommand(NamesConfig config, NameChangeService changeService) {
        this.config = config;
        this.changeService = changeService;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            source.getSender().sendMessage(Component.text("Usage: /names reload"));
            return;
        }
        if (!source.getSender().hasPermission("names.admin")) {
            config.messages().send(source.getSender(), "no-permission");
            return;
        }
        config.reload();
        for (Player player : Bukkit.getOnlinePlayers()) {
            changeService.apply(player);
        }
        config.messages().send(source.getSender(), "reload-done");
    }
}
