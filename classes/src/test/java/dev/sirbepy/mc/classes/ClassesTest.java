package dev.sirbepy.mc.classes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassesTest {

    static final long DAY = Duration.ofDays(1).toMillis();
    // 2026-10-10 12:00 in Zagreb (10:00 UTC).
    static final long NOON = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli();

    @TempDir
    File dir;
    long now = NOON;
    Classes classes;
    ClassStore store;
    final UUID gut = UUID.randomUUID();
    final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test"));
        classes = new Classes(store, () -> now, ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        classes.setFeedback(new Classes.Feedback() {
            @Override
            public void levelUp(UUID player, RpgClass c, int level) {
                events.add("level " + c.id() + " " + level);
            }

            @Override
            public void dailyCapReached(UUID player, int cap) {
                events.add("cap " + cap);
            }

            @Override
            public void changed(UUID player) {
            }
        });
    }

    @Test
    void withNoClassNothingIsEarned() {
        assertEquals(0, classes.addXp(gut, 50));
        assertNull(classes.active(gut));
        assertEquals(0, classes.level(gut));
    }

    @Test
    void theFirstPickIsFreeThenOtherClassesWaitSevenDays() {
        assertEquals(Classes.Choice.CHOSEN, classes.choose(gut, RpgClass.WARRIOR));
        assertEquals(Classes.Choice.ALREADY, classes.choose(gut, RpgClass.WARRIOR));
        assertEquals(Classes.Choice.LOCKED, classes.choose(gut, RpgClass.RANGER));
        assertEquals(NOON + 7 * DAY, classes.lockedUntil(gut, RpgClass.RANGER));
        now += 7 * DAY - 1;
        assertEquals(Classes.Choice.LOCKED, classes.choose(gut, RpgClass.RANGER));
        now += 1;
        assertEquals(Classes.Choice.CHOSEN, classes.choose(gut, RpgClass.RANGER));
        assertEquals(RpgClass.RANGER, classes.active(gut));
        assertEquals(Classes.Choice.LOCKED, classes.choose(gut, RpgClass.WARRIOR));
    }

    @Test
    void leavingIsFreeAndSoIsGoingBackToTheSameClass() {
        classes.choose(gut, RpgClass.ARCANIST);
        assertTrue(classes.leave(gut));
        assertNull(classes.active(gut));
        assertEquals(Classes.Choice.LOCKED, classes.choose(gut, RpgClass.WARRIOR));
        now += DAY;
        assertEquals(Classes.Choice.CHOSEN, classes.choose(gut, RpgClass.ARCANIST));
        // Going back didn't restart the lock: it still ends 7 days after the original pick.
        assertEquals(NOON + 7 * DAY, classes.lockedUntil(gut, RpgClass.WARRIOR));
    }

    @Test
    void onlyTheActiveClassEarnsAndEveryClassKeepsItsProgress() {
        classes.choose(gut, RpgClass.WARRIOR);
        classes.addXp(gut, 120);
        now += 7 * DAY;
        classes.choose(gut, RpgClass.ARTIFICER);
        classes.addXp(gut, 30);
        ClassProfile p = classes.profile(gut);
        assertEquals(120, p.xp(RpgClass.WARRIOR));
        assertEquals(30, p.xp(RpgClass.ARTIFICER));
        assertEquals(0, p.xp(RpgClass.RANGER));
        now += 7 * DAY;
        classes.choose(gut, RpgClass.WARRIOR);
        assertEquals(Levels.level(120), classes.level(gut));
    }

    @Test
    void theDailyCapStopsEarningUntilMidnightInZagreb() {
        classes.choose(gut, RpgClass.WARRIOR);
        assertEquals(250, classes.addXp(gut, 250));
        assertEquals(50, classes.addXp(gut, 100));
        assertEquals(0, classes.addXp(gut, 5));
        assertEquals(List.of("level warrior 2", "level warrior 3", "level warrior 4", "level warrior 5", "cap 300"), events);
        // 23:59 in Zagreb is still the same day...
        now = Instant.parse("2026-10-10T21:59:00Z").toEpochMilli();
        assertEquals(0, classes.addXp(gut, 5));
        // ...and 00:01 is the next one.
        now = Instant.parse("2026-10-10T22:01:00Z").toEpochMilli();
        assertEquals(5, classes.addXp(gut, 5));
        assertEquals(5, classes.profile(gut).earnedToday());
    }

    @Test
    void levelTwentyFiveIsTheTop() {
        classes.choose(gut, RpgClass.RANGER);
        classes.setXp(gut, RpgClass.RANGER, Levels.MAX_XP - 10);
        assertEquals(10, classes.addXp(gut, 100));
        assertEquals(25, classes.level(gut));
        assertEquals(0, classes.addXp(gut, 100));
    }

    @Test
    void cooldownsRunFromTheLevelsCooldown() {
        classes.choose(gut, RpgClass.WARRIOR);
        classes.startCooldown(gut);
        assertEquals(90_000, classes.cooldownLeftMillis(gut));
        now += 89_000;
        assertEquals(1000, classes.cooldownLeftMillis(gut));
        now += 1000;
        assertEquals(0, classes.cooldownLeftMillis(gut));
    }

    @Test
    void battleCryEndsAndOnlyCountsForAWarrior() {
        classes.choose(gut, RpgClass.WARRIOR);
        classes.startBattleCry(gut, 6);
        assertTrue(classes.battleCryActive(gut));
        now += 6000;
        assertEquals(false, classes.battleCryActive(gut));
    }

    @Test
    void profilesSurviveASaveAndLoad() {
        classes.choose(gut, RpgClass.ARCANIST);
        classes.addXp(gut, 77);
        store.save();
        ClassStore reloaded = new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test"));
        reloaded.load();
        Classes again = new Classes(reloaded, () -> now, ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        assertEquals(RpgClass.ARCANIST, again.active(gut));
        assertEquals(77, again.profile(gut).xp(RpgClass.ARCANIST));
        assertEquals(77, again.profile(gut).earnedToday());
        assertEquals(Classes.Choice.LOCKED, again.choose(gut, RpgClass.WARRIOR));
    }

    @Test
    void anAdminUnlockMakesTheNextPickFree() {
        classes.choose(gut, RpgClass.WARRIOR);
        classes.unlock(gut);
        assertEquals(Classes.Choice.CHOSEN, classes.choose(gut, RpgClass.RANGER));
        assertEquals(Classes.Choice.LOCKED, classes.choose(gut, RpgClass.WARRIOR));
    }

    @Test
    void secondaryClassNeedsTheWishGrant() {
        classes.choose(gut, RpgClass.WARRIOR);
        assertEquals(Classes.DualChoice.NOT_GRANTED, classes.chooseSecondary(gut, RpgClass.ARTIFICER));
        assertNull(classes.secondary(gut));
    }

    @Test
    void secondaryClassIsPickedOnceAndCantMatchTheMainClass() {
        classes.setDualClassGrants(Set.of(gut));
        classes.choose(gut, RpgClass.WARRIOR);
        assertEquals(Classes.DualChoice.SAME_AS_MAIN, classes.chooseSecondary(gut, RpgClass.WARRIOR));
        assertEquals(Classes.DualChoice.CHOSEN, classes.chooseSecondary(gut, RpgClass.ARTIFICER));
        assertEquals(RpgClass.ARTIFICER, classes.secondary(gut));
        assertEquals(Classes.DualChoice.ALREADY_SET, classes.chooseSecondary(gut, RpgClass.RANGER));
        assertEquals(RpgClass.ARTIFICER, classes.secondary(gut));
    }

    @Test
    void noExistingAdminCommandCanTouchThePermanentSecondaryPick() {
        classes.setDualClassGrants(Set.of(gut));
        classes.choose(gut, RpgClass.WARRIOR);
        classes.chooseSecondary(gut, RpgClass.ARTIFICER);
        classes.unlock(gut);
        classes.choose(gut, RpgClass.RANGER);
        classes.setXp(gut, RpgClass.ARTIFICER, 500);
        classes.resetToday(gut);
        classes.leave(gut);
        assertEquals(RpgClass.ARTIFICER, classes.secondary(gut));
    }

    @Test
    void revokingTheGrantHidesTheSecondaryWithoutLosingTheStoredPick() {
        classes.setDualClassGrants(Set.of(gut));
        classes.choose(gut, RpgClass.WARRIOR);
        classes.chooseSecondary(gut, RpgClass.ARTIFICER);
        classes.setDualClassGrants(Set.of());
        assertNull(classes.secondary(gut));
        assertEquals(RpgClass.ARTIFICER, classes.profile(gut).secondary());
        classes.setDualClassGrants(Set.of(gut));
        assertEquals(RpgClass.ARTIFICER, classes.secondary(gut));
    }

    @Test
    void secondaryPickSurvivesASaveAndLoad() {
        classes.setDualClassGrants(Set.of(gut));
        classes.choose(gut, RpgClass.WARRIOR);
        classes.chooseSecondary(gut, RpgClass.ARTIFICER);
        store.save();
        ClassStore reloaded = new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test"));
        reloaded.load();
        Classes again = new Classes(reloaded, () -> now, ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        again.setDualClassGrants(Set.of(gut));
        assertEquals(RpgClass.ARTIFICER, again.secondary(gut));
        assertEquals(Classes.DualChoice.ALREADY_SET, again.chooseSecondary(gut, RpgClass.RANGER));
    }
}
