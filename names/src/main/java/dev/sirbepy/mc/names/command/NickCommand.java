package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.gui.NickAnvilController;
import dev.sirbepy.mc.names.render.NickApplier;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code /nick}: opens the anvil GUI. {@code /nick <name>} and {@code /nick reset} act on the
 * caller. {@code /nick <player> <name|reset>} is the admin form, permission {@code
 * names.nick.others}; it only resolves online players, matching the {@code nick-player-not-found}
 * message.
 */
public final class NickCommand implements BasicCommand {

    private final NamesConfig config;
    private final NickApplier nickApplier;
    private final NickAnvilController anvilController;

    public NickCommand(NamesConfig config, NickApplier nickApplier, NickAnvilController anvilController) {
        this.config = config;
        this.nickApplier = nickApplier;
        this.anvilController = anvilController;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            config.messages().send(source.getSender(), "player-only");
            return;
        }
        if (!player.hasPermission("names.nick")) {
            config.messages().send(player, "no-permission");
            return;
        }
        switch (args.length) {
            case 0 -> anvilController.open(player);
            case 1 -> handleSelf(player, args[0]);
            case 2 -> handleOther(player, args[0], args[1]);
            default -> config.messages().send(player, "nick-usage");
        }
    }

    private void handleSelf(Player player, String arg) {
        if (arg.equalsIgnoreCase("reset")) {
            nickApplier.resetNick(player, player, true);
        } else {
            nickApplier.setNick(player, arg, player, true);
        }
    }

    private void handleOther(Player sender, String targetName, String nameOrReset) {
        if (!sender.hasPermission("names.nick.others")) {
            config.messages().send(sender, "no-permission");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            config.messages().send(sender, "nick-player-not-found", Placeholder.unparsed("target", targetName));
            return;
        }
        if (nameOrReset.equalsIgnoreCase("reset")) {
            nickApplier.resetNick(target, sender, false);
        } else {
            nickApplier.setNick(target, nameOrReset, sender, false);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        suggestions.add("reset");
        for (Player online : Bukkit.getOnlinePlayers()) {
            suggestions.add(online.getName());
        }
        return suggestions;
    }
}
