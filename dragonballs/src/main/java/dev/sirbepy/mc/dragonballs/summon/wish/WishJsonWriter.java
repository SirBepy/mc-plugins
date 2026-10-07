package dev.sirbepy.mc.dragonballs.summon.wish;

import dev.sirbepy.mc.core.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds and appends lines to {@code plugins/DragonBalls/wishes.jsonl}: one
 * {@code {"time","session","dragon","wishesLeft","player","uuid","message","set"}} line per
 * wish-session chat message, one {@code {"time","session","type":"grant","count","note","set"}}
 * line per {@code /shenron grant}, and one
 * {@code {"time","session","type":"public","title","line"}} line per {@code /shenron public}
 * (schema not specified by the rulebook; "type" distinguishes each from a wish line for anything
 * reading the file back). A "public" record is a player-safe stand-in for a grant: the grant's own
 * "note" is the judge's internal reasoning and must never be published, so the public website reads
 * "public" records instead. Running {@code /shenron public} again for the same session appends a
 * newer "public" record rather than replacing the old one, so a consumer must take the latest
 * "public" record per session as the current one. {@code set} is additive (the 3-set split
 * added it on top of the original schema): Player Claude's existing parser only reads fields it
 * already knows by name, so an older reader keeps working unchanged. Hand-escaped JSON, same
 * reasoning as ClaudeBridge's InboxWriter: a wish or a judge's note can contain quotes or
 * backslashes, so this is not the "filename never contains a quote" shortcut used elsewhere in this
 * repo.
 */
public final class WishJsonWriter {

    private WishJsonWriter() {
    }

    public static String wishLine(Instant time, String session, String dragon, int wishesLeft,
                                   String player, UUID uuid, String message, String set) {
        return "{\"time\":\"" + Json.escape(time.toString())
                + "\",\"session\":\"" + Json.escape(session)
                + "\",\"dragon\":\"" + Json.escape(dragon)
                + "\",\"wishesLeft\":" + wishesLeft
                + ",\"player\":\"" + Json.escape(player)
                + "\",\"uuid\":\"" + Json.escape(uuid.toString())
                + "\",\"message\":\"" + Json.escape(message)
                + "\",\"set\":\"" + Json.escape(set)
                + "\"}";
    }

    public static String grantedLine(Instant time, String session, int count, String note, String set) {
        return "{\"time\":\"" + Json.escape(time.toString())
                + "\",\"session\":\"" + Json.escape(session)
                + "\",\"type\":\"grant\""
                + ",\"count\":" + count
                + ",\"note\":\"" + Json.escape(note == null ? "" : note)
                + "\",\"set\":\"" + Json.escape(set)
                + "\"}";
    }

    /** A player-safe record for {@code session}, backfilling a {@link #grantedLine grant} that is
     * safe to show on the public website; see the class javadoc for why this exists instead of
     * publishing the grant's own note, and for the "latest wins" rule across repeated calls. */
    public static String publicLine(Instant time, String session, String title, String line) {
        return "{\"time\":\"" + Json.escape(time.toString())
                + "\",\"session\":\"" + Json.escape(session)
                + "\",\"type\":\"public\""
                + ",\"title\":\"" + Json.escape(title)
                + "\",\"line\":\"" + Json.escape(line)
                + "\"}";
    }

    public static void append(File file, String line) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(file.toPath(), (line + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
