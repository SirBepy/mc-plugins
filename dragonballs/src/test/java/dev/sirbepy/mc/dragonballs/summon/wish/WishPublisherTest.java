package dev.sirbepy.mc.dragonballs.summon.wish;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link WishPublisher#publicize}, moved here from {@code ShenronCommandTest} once
 * {@code /shenron public}'s logic (validation, the grant lookup, the append) stopped living on
 * {@code ShenronCommand} itself. The existing-grant case writes a {@code "grant"} record directly
 * via {@link WishJsonWriter} instead of driving a full summon/grant session, since {@link
 * WishPublisher#publicize} only ever reads that record back as a substring match.
 */
class WishPublisherTest {

    private File wishesFile;
    private WishPublisher publisher;
    private ConsoleCommandSenderMock console;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        MockBukkit.mock();
        wishesFile = new File(tempDir.toFile(), "wishes.jsonl");
        publisher = new WishPublisher(wishesFile);
        console = new ConsoleCommandSenderMock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String nextConsoleMessage() {
        return PlainTextComponentSerializer.plainText().serialize(console.nextComponentMessage());
    }

    @Test
    void publicRefusesOnAMissingSeparatorAndWritesNothing() {
        publisher.publicize(console, "abc123", "Title without a separator");

        assertTrue(nextConsoleMessage().contains("Usage:"));
        assertFalse(wishesFile.isFile(), "no file should be created for a refused request");
    }

    @Test
    void publicRefusesASessionWithNoGrantLogged() {
        publisher.publicize(console, "nosuchsession", "Found treasure | Became rich");

        assertTrue(nextConsoleMessage().contains("no grant logged for session nosuchsession"));
    }

    @Test
    void publicRefusesATitleOverSixtyChars() {
        String longTitle = "x".repeat(61);

        publisher.publicize(console, "abc123", longTitle + " | a line");

        assertTrue(nextConsoleMessage().contains("Title is too long"));
    }

    @Test
    void publicRefusesALineOverTwoHundredChars() {
        String longLine = "x".repeat(201);

        publisher.publicize(console, "abc123", "Title | " + longLine);

        assertTrue(nextConsoleMessage().contains("Line is too long"));
    }

    @Test
    void publicAppendsARecordForASessionWithAnExistingGrant() throws IOException {
        String sessionId = "abc123";
        WishJsonWriter.append(wishesFile, WishJsonWriter.grantedLine(Instant.now(), sessionId, 1,
                "internal note never published", "overworld"));

        publisher.publicize(console, sessionId, "Found a chest | Got 64 diamonds");

        assertTrue(nextConsoleMessage().contains("Published session " + sessionId));
        List<String> lines = Files.readAllLines(wishesFile.toPath());
        boolean foundPublicRecord = lines.stream().anyMatch(line -> line.contains("\"type\":\"public\"")
                && line.contains("\"session\":\"" + sessionId + "\"")
                && line.contains("\"title\":\"Found a chest\"")
                && line.contains("\"line\":\"Got 64 diamonds\""));
        assertTrue(foundPublicRecord, "expected a public record for session " + sessionId + " in: " + lines);
        assertFalse(lines.stream().anyMatch(line -> line.contains("internal note never published") && line.contains("public")),
                "the public record must never carry the judge's internal grant note");
    }
}
