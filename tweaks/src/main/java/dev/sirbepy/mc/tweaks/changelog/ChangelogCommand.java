package dev.sirbepy.mc.tweaks.changelog;

import com.mojang.brigadier.Command;
import dev.sirbepy.mc.core.Messages;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.inventory.Book;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * {@code /changelog}: opens the changelog book for anyone. The file is read on every use, so an edit
 * to plugins/Tweaks/changelog.yml on the server shows up without a restart.
 */
public final class ChangelogCommand {

    private final File file;
    private final Messages messages;

    public ChangelogCommand(File file, Messages messages) {
        this.file = file;
        this.messages = messages;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("changelog").executes(c -> {
            if (c.getSource().getSender() instanceof Player player) {
                player.openBook(book());
            } else {
                messages.send(c.getSource().getSender(), "changelog-players-only");
            }
            return Command.SINGLE_SUCCESS;
        }).build(), "What changed at each server restart");
    }

    Book book() {
        return Book.book(messages.render("changelog-book-title"), messages.render("changelog-book-author"),
                Changelog.pages(Changelog.load(YamlConfiguration.loadConfiguration(file)), messages));
    }
}
