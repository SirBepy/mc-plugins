package dev.sirbepy.mc.dragonballs.summon.wish;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

/**
 * Backs {@code /shenron public <session> <title> | <line>}: appends a player-safe {@code "public"}
 * record to {@code wishes.jsonl} for a session that already has a {@code "grant"} record, so the
 * public website can show the wish without ever reading a judge's internal grant note. The
 * session need not be the active one - backfilling an old, already-ended session is the main
 * first use. Running this again for the same session appends a newer {@code "public"} record;
 * see {@link WishJsonWriter} for the "latest wins" rule.
 */
public final class WishPublisher {

    private static final int MAX_PUBLIC_TITLE_CHARS = 60;
    private static final int MAX_PUBLIC_LINE_CHARS = 200;

    private final File wishesFile;

    public WishPublisher(File wishesFile) {
        this.wishesFile = wishesFile;
    }

    public int publicize(CommandSender sender, String session, String titleAndLine) {
        int sep = titleAndLine.indexOf(" | ");
        if (sep < 0) {
            sender.sendMessage(Component.text("Usage: /shenron public <session> <title> | <line>"));
            return Command.SINGLE_SUCCESS;
        }
        String title = titleAndLine.substring(0, sep).trim();
        String line = titleAndLine.substring(sep + 3).trim();
        if (title.isEmpty() || line.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /shenron public <session> <title> | <line>"));
            return Command.SINGLE_SUCCESS;
        }
        if (title.length() > MAX_PUBLIC_TITLE_CHARS) {
            sender.sendMessage(Component.text("Title is too long (max " + MAX_PUBLIC_TITLE_CHARS + " chars)."));
            return Command.SINGLE_SUCCESS;
        }
        if (line.length() > MAX_PUBLIC_LINE_CHARS) {
            sender.sendMessage(Component.text("Line is too long (max " + MAX_PUBLIC_LINE_CHARS + " chars)."));
            return Command.SINGLE_SUCCESS;
        }
        if (!hasGrant(session)) {
            sender.sendMessage(Component.text("no grant logged for session " + session));
            return Command.SINGLE_SUCCESS;
        }
        String json = WishJsonWriter.publicLine(Instant.now(), session, title, line);
        try {
            WishJsonWriter.append(wishesFile, json);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Failed to write public record: " + e.getMessage()));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("Published session " + session + ": \"" + title + "\" | \"" + line + "\""));
        return Command.SINGLE_SUCCESS;
    }

    /** Whether {@code wishes.jsonl} already holds a {@code "type":"grant"} record for {@code
     * session}; field order is fixed by {@link WishJsonWriter#grantedLine}, so a plain substring
     * check is enough without a JSON parser dependency. */
    private boolean hasGrant(String session) {
        if (!wishesFile.isFile()) {
            return false;
        }
        try {
            for (String recordedLine : Files.readAllLines(wishesFile.toPath(), StandardCharsets.UTF_8)) {
                if (recordedLine.contains("\"type\":\"grant\"") && recordedLine.contains("\"session\":\"" + session + "\"")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
