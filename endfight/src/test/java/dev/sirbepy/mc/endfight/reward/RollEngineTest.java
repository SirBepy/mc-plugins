package dev.sirbepy.mc.endfight.reward;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The roll order (armor until 4 pieces, then auras, then heads, then a duplicate armor piece) and
 * the roll-count math (Joe's reward spec, 2026-10-09). */
class RollEngineTest {

    @Test
    void aPlayerWithNoArmorRollsArmor() {
        RollEngine.Outcome outcome = RollEngine.roll(EnumSet.noneOf(ArmorPiece.class), EnumSet.noneOf(DragonAura.class),
                Set.of(), new Random(42));

        assertEquals(RollEngine.Kind.ARMOR, outcome.kind());
    }

    @Test
    void onceAllFourArmorPiecesAreOwnedTheRollMovesToAuras() {
        Set<ArmorPiece> allArmor = EnumSet.allOf(ArmorPiece.class);

        RollEngine.Outcome outcome = RollEngine.roll(allArmor, EnumSet.noneOf(DragonAura.class), Set.of(), new Random(1));

        assertEquals(RollEngine.Kind.AURA, outcome.kind());
    }

    @Test
    void onceAllArmorAndAurasAreOwnedTheRollMovesToHeads() {
        Set<ArmorPiece> allArmor = EnumSet.allOf(ArmorPiece.class);
        Set<DragonAura> allAuras = EnumSet.allOf(DragonAura.class);

        RollEngine.Outcome outcome = RollEngine.roll(allArmor, allAuras, Set.of(), new Random(1));

        assertEquals(RollEngine.Kind.HEAD, outcome.kind());
    }

    @Test
    void onceEverythingIsOwnedTheRollGivesADuplicateArmorPiece() {
        Set<ArmorPiece> allArmor = EnumSet.allOf(ArmorPiece.class);
        Set<DragonAura> allAuras = EnumSet.allOf(DragonAura.class);
        Set<String> allHeads = new LinkedHashSet<>();
        DragonTrophyHeads.ALL.forEach(h -> allHeads.add(h.id()));

        RollEngine.Outcome outcome = RollEngine.roll(allArmor, allAuras, allHeads, new Random(1));

        assertEquals(RollEngine.Kind.DUP_ARMOR, outcome.kind());
    }

    @Test
    void aSingleMissingArmorPieceIsAlwaysTheOneRolled() {
        Set<ArmorPiece> ownsThreeOfFour = EnumSet.of(ArmorPiece.HELMET, ArmorPiece.CHESTPLATE, ArmorPiece.LEGGINGS);

        RollEngine.Outcome outcome = RollEngine.roll(ownsThreeOfFour, EnumSet.noneOf(DragonAura.class), Set.of(), new Random(99));

        assertEquals(RollEngine.Kind.ARMOR, outcome.kind());
        assertEquals(ArmorPiece.BOOTS, outcome.armor());
    }

    @Test
    void rollChanceAtOrAboveOneIsAlwaysOneGuaranteedRoll() {
        assertEquals(1, RollEngine.rollCount(1.0, new Random(1)));
    }

    @Test
    void ascendedsOnePointFiveGuaranteesTheFirstAndMaySpendTheRemainderOnASecond() {
        // A Random seeded to roll < 0.5 on its very next nextDouble() call lands the second roll.
        Random landsSecond = fixedDouble(0.1);
        assertEquals(2, RollEngine.rollCount(1.5, landsSecond));

        Random missesSecond = fixedDouble(0.9);
        assertEquals(1, RollEngine.rollCount(1.5, missesSecond));
    }

    @Test
    void aFractionalChanceBelowOneIsZeroOrOneRolls() {
        assertEquals(0, RollEngine.rollCount(0.10, fixedDouble(0.5)));
        assertEquals(1, RollEngine.rollCount(0.10, fixedDouble(0.01)));
    }

    /** A {@link Random} whose {@code nextDouble()} always returns {@code value} - lets the
     * roll-count tests pin the exact branch without caring which seed produces it. */
    private static Random fixedDouble(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
