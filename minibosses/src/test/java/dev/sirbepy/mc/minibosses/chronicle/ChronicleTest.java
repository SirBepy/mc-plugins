package dev.sirbepy.mc.minibosses.chronicle;

import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import dev.sirbepy.mc.minibosses.sovereign.SovereignFight;
import dev.sirbepy.mc.minibosses.tyrant.TyrantFight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The story state machine against a stand-in story ({@code chronicles-test.yml}), the cipher, and
 * a check that the real committed {@code chronicles.enc} still decrypts and matches the real
 * Guardians, which only runs where the key is present.
 */
class ChronicleTest {

    private static final Logger LOG = Logger.getLogger("chronicle-test");
    private static final UUID WISHER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private GuardianKills kills;
    private ChronicleProgress progress;
    private Chronicle chronicle;
    private Path dir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        dir = tempDir;
        kills = new GuardianKills(tempDir.toFile(), LOG);
        progress = new ChronicleProgress(tempDir.toFile(), LOG);
        chronicle = new Chronicle(ChronicleText.parse(resource("/chronicles-test.yml")), progress, kills, new Random(0));
    }

    private String resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void kill(UUID player, String guardianId) {
        kills.record(guardianId, Tier.NORMAL, Set.of(player));
    }

    @Test
    void theWholeStoryWalksInOrder() {
        assertEquals(List.of("prologue 1", "prologue 2"), chronicle.talk(OTHER).lines());
        assertEquals(1, chronicle.stage(OTHER));
        assertEquals(List.of("waiting one"), chronicle.talk(OTHER).lines(), "no kill yet, so the chapter waits");

        kill(OTHER, SentinelFight.GUARDIAN_ID);
        assertEquals(List.of("told one"), chronicle.talk(OTHER).lines());
        assertTrue(chronicle.seeking(OTHER));
        assertEquals(List.of("reminder one"), chronicle.talk(OTHER).lines());
        assertEquals(List.of(), chronicle.discover(OTHER, "minecraft:plains"), "the wrong biome finds nothing");
        assertEquals(List.of("echo one"), chronicle.discover(OTHER, "minecraft:lush_caves"));
        assertFalse(chronicle.seeking(OTHER));

        kill(OTHER, TyrantFight.GUARDIAN_ID);
        assertEquals(List.of("told two"), chronicle.talk(OTHER).lines());
        assertEquals(List.of("echo two"), chronicle.discover(OTHER, "minecraft:basalt_deltas"));
        kill(OTHER, SovereignFight.GUARDIAN_ID);
        assertEquals(List.of("told three"), chronicle.talk(OTHER).lines());
        assertEquals(List.of("echo three"), chronicle.discover(OTHER, "minecraft:end_highlands"));
        assertEquals(chronicle.finaleStage(), chronicle.stage(OTHER));

        Chronicle.Talk finale = chronicle.talk(OTHER);
        assertTrue(finale.finale());
        assertEquals(List.of("it was them"), finale.lines());
        assertEquals(List.of("idle line"), chronicle.talk(OTHER).lines(), "after the finale she only idles");
        assertFalse(chronicle.talk(OTHER).finale(), "the finale plays once");
    }

    @Test
    void aGuardianBeatenBeforeItsChapterCountsWhenTheChapterComesUp() {
        kill(OTHER, TyrantFight.GUARDIAN_ID);
        chronicle.talk(OTHER);
        assertEquals(List.of("waiting one"), chronicle.talk(OTHER).lines(), "the Tyrant kill must not skip chapter one");

        kill(OTHER, SentinelFight.GUARDIAN_ID);
        chronicle.talk(OTHER);
        chronicle.discover(OTHER, "minecraft:lush_caves");
        assertEquals(List.of("told two"), chronicle.talk(OTHER).lines(), "the earlier Tyrant kill counts now");
    }

    @Test
    void aDiscoveryOnlyCountsWhileSeekingIt() {
        chronicle.talk(OTHER);
        assertEquals(List.of(), chronicle.discover(OTHER, "minecraft:lush_caves"),
                "standing in the biome before the chapter is told finds nothing");
        assertEquals(1, chronicle.stage(OTHER));
    }

    @Test
    void theJournalGrowsAndEndsWithWhereToGoNext() {
        assertEquals(List.of(), chronicle.journal(OTHER), "a stranger has no journal");
        chronicle.talk(OTHER);
        assertEquals(List.of("prologue page", "hunt one"), chronicle.journal(OTHER));

        kill(OTHER, SentinelFight.GUARDIAN_ID);
        chronicle.talk(OTHER);
        assertEquals(List.of("prologue page", "page one", "clue one"), chronicle.journal(OTHER));
        chronicle.discover(OTHER, "minecraft:lush_caves");
        assertEquals(List.of("prologue page", "page one", "discovery one page", "hunt two"), chronicle.journal(OTHER));
    }

    @Test
    void theFinaleNamesTheWisherToThemselvesAndToEveryoneElse() {
        progress.setStage(WISHER, chronicle.finaleStage());
        progress.setStage(OTHER, chronicle.finaleStage());

        assertEquals(List.of("it was you"), chronicle.talk(WISHER).lines());
        assertEquals(List.of("it was them"), chronicle.talk(OTHER).lines());
        assertTrue(chronicle.unlockedPages(WISHER).contains("finale page you"));
        assertTrue(chronicle.unlockedPages(OTHER).contains("finale page them"));
        assertEquals("", chronicle.objective(OTHER), "a finished story has nowhere left to go");
    }

    @Test
    void theFinishedBookIsTheWholeStoryAndOpensWithADedicationForTheWisher() {
        progress.setStage(WISHER, chronicle.doneStage());
        progress.setStage(OTHER, chronicle.doneStage());
        List<String> whole = List.of("prologue page", "page one", "discovery one page", "page two",
                "discovery two page", "page three", "discovery three page");

        assertEquals(concat(whole, "finale page them"), chronicle.bookPages(OTHER));
        assertEquals(concat(List.of("for you"), concat(whole, "finale page you")), chronicle.bookPages(WISHER));
    }

    private static List<String> concat(List<String> head, String tail) {
        return concat(head, List.of(tail));
    }

    private static List<String> concat(List<String> head, List<String> tail) {
        return java.util.stream.Stream.concat(head.stream(), tail.stream()).toList();
    }

    @Test
    void progressSurvivesAReload() {
        chronicle.talk(OTHER);
        progress.markFragment(OTHER, SentinelFight.GUARDIAN_ID);

        ChronicleProgress reloaded = new ChronicleProgress(dir.toFile(), LOG);
        assertEquals(1, reloaded.stage(OTHER));
        assertTrue(reloaded.hasFragment(OTHER, SentinelFight.GUARDIAN_ID));
        assertFalse(reloaded.hasFragment(OTHER, TyrantFight.GUARDIAN_ID));
    }

    @Test
    void theCipherRoundTripsAndRejectsAWrongKey() throws Exception {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] plain = "secret ending".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = ChronicleCipher.encrypt(plain, key);

        assertFalse(new String(sealed, StandardCharsets.ISO_8859_1).contains("secret"));
        assertArrayEquals(plain, ChronicleCipher.decrypt(sealed, key));
        byte[] other = new byte[32];
        other[0] = 1;
        assertThrows(GeneralSecurityException.class,
                () -> ChronicleCipher.decrypt(sealed, Base64.getEncoder().encodeToString(other)));
        assertThrows(GeneralSecurityException.class,
                () -> ChronicleCipher.encrypt(plain, Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void theCommittedStoryDecryptsAndMatchesTheRealGuardians() throws Exception {
        Path secrets = Path.of("..", "server", "secrets.properties");
        assumeTrue(Files.exists(secrets), "no server/secrets.properties on this machine");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(secrets)) {
            props.load(in);
        }
        String key = props.getProperty("minibosses.chronicles-key");
        assumeTrue(key != null, "no Chronicles key on this machine");
        byte[] sealed;
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/chronicles.enc"))) {
            sealed = in.readAllBytes();
        }

        ChronicleText text = ChronicleText.parse(new String(ChronicleCipher.decrypt(sealed, key), StandardCharsets.UTF_8));

        assertEquals(List.of(SentinelFight.GUARDIAN_ID, TyrantFight.GUARDIAN_ID, SovereignFight.GUARDIAN_ID),
                text.chapters().stream().map(ChronicleText.Chapter::guardian).toList());
        for (ChronicleText.Chapter chapter : text.chapters()) {
            assertTrue(chapter.discovery().biome().startsWith("minecraft:"), chapter.title());
            assertFalse(chapter.told().isEmpty(), chapter.title());
        }
        assertFalse(text.finale().lines().isEmpty());
    }
}
