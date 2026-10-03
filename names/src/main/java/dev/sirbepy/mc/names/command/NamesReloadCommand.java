package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.render.NameChangeService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
            sendUsage(source.getSender(), config.messages());
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

    /** Package-private so tests can prove a configured override of reload-usage is what gets sent. */
    static void sendUsage(CommandSender sender, Messages messages) {
        messages.send(sender, "reload-usage");
    }
}
