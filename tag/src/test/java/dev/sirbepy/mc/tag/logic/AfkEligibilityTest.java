package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkEligibilityTest {

    private static AfkEligibility.Candidate candidate(UUID uuid, boolean afk, boolean optedOut, boolean playable) {
        return new AfkEligibility.Candidate(uuid, afk, optedOut, playable);
    }

    @Test
    void excludesAfkOptedOutAndNonPlayableGameModes() {
        UUID afk = UUID.randomUUID();
        UUID optedOut = UUID.randomUUID();
        UUID spectator = UUID.randomUUID();
        UUID eligible = UUID.randomUUID();

        List<AfkEligibility.Candidate> candidates = List.of(
                candidate(afk, true, false, true),
                candidate(optedOut, false, true, true),
                candidate(spectator, false, false, false),
                candidate(eligible, false, false, true));

        assertEquals(List.of(eligible), AfkEligibility.eligible(candidates, null));
    }

    @Test
    void avoidsThePreviousItWhenSomeoneElseIsEligible() {
        UUID previous = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<AfkEligibility.Candidate> candidates = List.of(
                candidate(previous, false, false, true),
                candidate(other, false, false, true));

        assertEquals(List.of(other), AfkEligibility.eligible(candidates, previous));
    }

    @Test
    void fallsBackToThePreviousItWhenTheyAreTheOnlyEligiblePlayer() {
        UUID previous = UUID.randomUUID();
        List<AfkEligibility.Candidate> candidates = List.of(candidate(previous, false, false, true));

        assertEquals(List.of(previous), AfkEligibility.eligible(candidates, previous));
    }

    @Test
    void emptyCandidateListGivesEmptyResult() {
        assertTrue(AfkEligibility.eligible(List.of(), UUID.randomUUID()).isEmpty());
    }
}
