package dev.sirbepy.mc.claudebridge.inbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxWriterTest {

    @Test
    void appendsOneJsonLineWithAllFourFields(@TempDir Path tempDir) throws Exception {
        File inbox = new File(tempDir.toFile(), "inbox.jsonl");
        UUID uuid = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-04T12:00:00Z");

        InboxWriter.append(inbox, "Steve", uuid, "hello there", time);

        List<String> lines = Files.readAllLines(inbox.toPath(), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertEquals("{\"time\":\"2026-10-04T12:00:00Z\",\"player\":\"Steve\",\"uuid\":\"" + uuid
                + "\",\"message\":\"hello there\"}", lines.get(0));
    }

    @Test
    void secondCallAppendsRatherThanOverwrites(@TempDir Path tempDir) throws Exception {
        File inbox = new File(tempDir.toFile(), "inbox.jsonl");
        Instant time = Instant.parse("2026-10-04T12:00:00Z");

        InboxWriter.append(inbox, "Steve", UUID.randomUUID(), "first", time);
        InboxWriter.append(inbox, "Alex", UUID.randomUUID(), "second", time);

        List<String> lines = Files.readAllLines(inbox.toPath(), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"message\":\"first\""));
        assertTrue(lines.get(1).contains("\"message\":\"second\""));
    }

    @Test
    void quotesAndBackslashesInTheMessageAreEscapedValidJson(@TempDir Path tempDir) throws Exception {
        File inbox = new File(tempDir.toFile(), "inbox.jsonl");

        InboxWriter.append(inbox, "Steve", UUID.randomUUID(), "she said \"hi\" \\ bye", Instant.now());

        String line = Files.readString(inbox.toPath(), StandardCharsets.UTF_8).trim();
        assertTrue(line.contains("\"message\":\"she said \\\"hi\\\" \\\\ bye\""), line);
    }

    @Test
    void newlinesInTheMessageAreEscapedNotLiteral(@TempDir Path tempDir) throws Exception {
        File inbox = new File(tempDir.toFile(), "inbox.jsonl");

        InboxWriter.append(inbox, "Steve", UUID.randomUUID(), "line one\nline two", Instant.now());

        List<String> lines = Files.readAllLines(inbox.toPath(), StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), "an escaped \\n must not become a real line break in the file");
        assertTrue(lines.get(0).contains("\"message\":\"line one\\nline two\""));
    }

    @Test
    void parentDirectoryIsCreatedWhenMissing(@TempDir Path tempDir) throws Exception {
        File inbox = new File(tempDir.toFile(), "nested/plugins/ClaudeBridge/inbox.jsonl");

        InboxWriter.append(inbox, "Steve", UUID.randomUUID(), "hi", Instant.now());

        assertTrue(inbox.exists());
    }
}
