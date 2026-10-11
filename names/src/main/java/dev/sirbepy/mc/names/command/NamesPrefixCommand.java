package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.core.Players;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.render.CustomPrefix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /names prefix set <player> <MiniMessage text>} and {@code /names prefix clear <player>}, for
 * ops and the console. Works on offline players the server has seen, so a prefix can be given before
 * its owner next joins. Takes the {@code /names} arguments as typed, {@code args[0]} being "prefix".
 */
public final class NamesPrefixCommand {

    private final NamesConfig config;
    private final CustomPrefix customPrefix;
    private final Consumer<Player> rerender;

    public NamesPrefixCommand(NamesConfig config, CustomPrefix customPrefix, Consumer<Player> rerender) {
        this.config = config;
        this.customPrefix = customPrefix;
        this.rerender = rerender;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("names.admin")) {
            config.messages().send(sender, "no-permission");
            return;
        }
        boolean set = args.length >= 4 && args[1].equalsIgnoreCase("set");
        boolean clear = args.length == 3 && args[1].equalsIgnoreCase("clear");
        if (!set && !clear) {
            config.messages().send(sender, "prefix-usage");
            return;
        }
        OfflinePlayer target = find(args[2]);
        if (target == null) {
            config.messages().send(sender, "nick-player-not-found", Placeholder.unparsed("target", args[2]));
            return;
        }
        UUID id = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[2];
        if (clear) {
            customPrefix.clear(id);
            config.messages().send(sender, "prefix-cleared", Placeholder.unparsed("target", name));
        } else {
            String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            Component rendered = CustomPrefix.render(text);
            if (PlainTextComponentSerializer.plainText().serialize(rendered).isBlank()) {
                config.messages().send(sender, "prefix-blank");
                return;
            }
            customPrefix.set(id, text);
            config.messages().send(sender, "prefix-set", Placeholder.unparsed("target", name),
                    Placeholder.component("prefix", rendered));
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            rerender.accept(online);
        }
    }

    /** Online by exact name, else a player this server has seen, else a raw UUID. */
    private static OfflinePlayer find(String nameOrId) {
        Optional<OfflinePlayer> known = Players.known(nameOrId);
        if (known.isPresent()) {
            return known.get();
        }
        UUID id = Players.idOf(nameOrId);
        return id == null ? null : Bukkit.getOfflinePlayer(id);
    }
}
