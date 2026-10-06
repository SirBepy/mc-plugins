package dev.sirbepy.mc.dragonballs.summon.wish;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds and appends lines to {@code plugins/DragonBalls/wishes.jsonl}: one
 * {@code {"time","session","dragon","wishesLeft","player","uuid","message"}} line per wish-session
 * chat message, and one {@code {"time","session","type":"grant","count","note"}} line per
 * {@code /shenron grant} (schema not specified by the rulebook; "type" distinguishes it from a wish
 * line for anything reading the file back). Hand-escaped JSON, same reasoning as ClaudeBridge's
 * InboxWriter: a wish or a judge's note can contain quotes or backslashes, so this is not the
 * "filename never contains a quote" shortcut used elsewhere in this repo.
 */
public final class WishJsonWriter {

    private WishJsonWriter() {
    }

    public static String wishLine(Instant time, String session, String dragon, int wishesLeft,
                                   String player, UUID uuid, String message) {
        return "{\"time\":\"" + jsonEscape(time.toString())
                + "\",\"session\":\"" + jsonEscape(session)
                + "\",\"dragon\":\"" + jsonEscape(dragon)
                + "\",\"wishesLeft\":" + wishesLeft
                + ",\"player\":\"" + jsonEscape(player)
                + "\",\"uuid\":\"" + jsonEscape(uuid.toString())
                + "\",\"message\":\"" + jsonEscape(message)
                + "\"}";
    }

    public static String grantedLine(Instant time, String session, int count, String note) {
        return "{\"time\":\"" + jsonEscape(time.toString())
                + "\",\"session\":\"" + jsonEscape(session)
                + "\",\"type\":\"grant\""
                + ",\"count\":" + count
                + ",\"note\":\"" + jsonEscape(note == null ? "" : note)
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

    static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
