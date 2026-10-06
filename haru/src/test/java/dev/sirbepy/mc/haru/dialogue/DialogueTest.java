package dev.sirbepy.mc.haru.dialogue;

import dev.sirbepy.mc.haru.rep.Tier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueTest {

    private static DialogueContext ctx(boolean first, Tier tier, long time, boolean rain, String held, double health) {
        return new DialogueContext(first, tier, time, 2, rain, false, held, health, 20, 5, 10, 0, false);
    }

    private static Set<String> lines(DialogueContext ctx) {
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 300; seed++) {
            seen.add(Dialogue.greet(ctx, "ImMijo", new Random(seed)));
        }
        return seen;
    }

    @Test
    void firstMeetingIsTheIntroductionWithTheOwnersName() {
        String line = Dialogue.greet(ctx(true, Tier.STRANGER, 6000, false, null, 20), "ImMijo", new Random(1));
        assertEquals(Dialogue.intro("ImMijo"), line);
        assertTrue(line.contains("ImMijo"));
        assertFalse(line.contains("{name}"));
    }

    @Test
    void woundedOwnerIsToldToRest() {
        assertEquals(Set.of("You are hurt, ImMijo. Sit beneath the blossoms a while before you go back out there."),
                lines(ctx(false, Tier.FRIEND, 6000, false, "katana", 4)));
    }

    @Test
    void heldKatanaUsuallyGetsRemarkedOn() {
        assertTrue(lines(ctx(false, Tier.STRANGER, 6000, false, "katana", 20)).stream()
                .anyMatch(l -> l.contains("blade") || l.contains("katana")));
    }

    @Test
    void rainAndTimeOfDayShowUp() {
        assertTrue(lines(ctx(false, Tier.STRANGER, 6000, true, null, 20)).stream().anyMatch(l -> l.contains("Rain")
                || l.contains("walk home")));
        assertTrue(lines(ctx(false, Tier.STRANGER, 18000, false, null, 20)).stream().anyMatch(l -> l.contains("late")
                || l.contains("Night")));
    }

    @Test
    void backstoryLinesOnlyAfterTheirTier() {
        String treeLine = "Sometimes I still feel the hill under me. Old roots do not forget.";
        assertFalse(lines(ctx(false, Tier.ACQUAINTANCE, 6000, false, null, 20)).contains(treeLine));
        assertTrue(lines(ctx(false, Tier.FRIEND, 6000, false, null, 20)).contains(treeLine));
    }

    @Test
    void everyTierAboveStrangerHasAnUnlockLine() {
        for (Tier tier : Tier.values()) {
            if (tier != Tier.STRANGER) {
                assertFalse(Dialogue.tierUnlock(tier, "ImMijo").contains("{name}"));
            }
        }
    }

    @Test
    void strangersHearWhoHaruServes() {
        assertTrue(Dialogue.stranger("ImMijo", new Random(3)).contains("ImMijo")
                || Dialogue.stranger("ImMijo", new Random(3)).contains("seven stars"));
    }
}
