package dev.sirbepy.mc.claudebridge.inbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Appends one {@code {"time","player","uuid","message"}} JSON line per accepted {@code /claude}
 * to inbox.jsonl, for an out-of-process reader to pick up. Hand-rolled escaping, not a JSON
 * library dependency: a player's message can contain quotes, backslashes or (pre-validator)
 * newlines, unlike the filenames elsewhere in this repo that get the "never contains a quote"
 * shortcut (see server/build.gradle.kts), so this escapes for real rather than assuming.
 */
public final class InboxWriter {

    private InboxWriter() {
    }

    public static void append(File inboxFile, String player, UUID uuid, String message, Instant time) throws IOException {
        String line = "{\"time\":\"" + jsonEscape(time.toString())
                + "\",\"player\":\"" + jsonEscape(player)
                + "\",\"uuid\":\"" + jsonEscape(uuid.toString())
                + "\",\"message\":\"" + jsonEscape(message)
                + "\"}\n";
        File parent = inboxFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(inboxFile.toPath(), line.getBytes(StandardCharsets.UTF_8),
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
