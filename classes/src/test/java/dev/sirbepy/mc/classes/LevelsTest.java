package dev.sirbepy.mc.classes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelsTest {

    @Test
    void theCurveRunsFromFiftyXpToLevelTwoTo3960ForLevel25() {
        assertEquals(0, Levels.totalFor(1));
        assertEquals(50, Levels.totalFor(2));
        assertEquals(3960, Levels.MAX_XP);
        assertEquals(1, Levels.level(49));
        assertEquals(2, Levels.level(50));
        assertEquals(24, Levels.level(Levels.MAX_XP - 1));
        assertEquals(25, Levels.level(Levels.MAX_XP));
        assertEquals(25, Levels.level(Levels.MAX_XP * 2));
    }

    @Test
    void passivesStayWithinTheWishsTenPercentCap() {
        for (RpgClass c : RpgClass.values()) {
            assertEquals(2.0, c.passivePercent(1), 1e-9);
            assertEquals(10.0, c.passivePercent(25), 1e-9);
            for (int level = 1; level <= 30; level++) assertTrue(c.passivePercent(level) <= 10.0, c + " " + level);
        }
    }

    @Test
    void noCooldownIsEverUnderThirtySecondsAndLevellingShortensIt() {
        for (RpgClass c : RpgClass.values()) {
            for (int level = 1; level <= 25; level++) assertTrue(c.cooldownSeconds(level) >= 30, c + " " + level);
            assertTrue(c.cooldownSeconds(25) < c.cooldownSeconds(1), c.toString());
        }
        assertEquals(90, RpgClass.WARRIOR.cooldownSeconds(1));
        assertEquals(60, RpgClass.WARRIOR.cooldownSeconds(25));
        assertEquals(120, RpgClass.ARCANIST.cooldownSeconds(1));
    }

    @Test
    void abilitiesGrowWithLevel() {
        assertEquals(10, RpgClass.battleCryPercent(1), 1e-9);
        assertEquals(20, RpgClass.battleCryPercent(25), 1e-9);
        assertEquals(6, RpgClass.WARRIOR.abilitySeconds(1));
        assertEquals(10, RpgClass.WARRIOR.abilitySeconds(25));
        assertEquals(15, RpgClass.RANGER.abilitySeconds(25));
        assertEquals(20, RpgClass.ARTIFICER.abilitySeconds(25));
        assertEquals(2, RpgClass.mendHealth(1), 1e-9);
        assertEquals(6, RpgClass.mendHealth(25), 1e-9);
    }

    @Test
    void linesReadWell() {
        assertEquals("Weapon Master: +2.0% melee damage", RpgClass.WARRIOR.passiveLine(1));
        assertEquals("Mend: heal 1 heart and cure poison (every 120 s)", RpgClass.ARCANIST.abilityLine(1));
        assertEquals("Mend: heal 3 hearts and cure poison (every 60 s)", RpgClass.ARCANIST.abilityLine(25));
        assertEquals("a Warrior", RpgClass.WARRIOR.withArticle());
        assertEquals("an Arcanist", RpgClass.ARCANIST.withArticle());
        assertEquals("an Artificer", RpgClass.ARTIFICER.withArticle());
        assertEquals(RpgClass.RANGER, RpgClass.parse("Ranger"));
        assertEquals(RpgClass.ARTIFICER, RpgClass.parse("artificer"));
        assertEquals(null, RpgClass.parse("paladin"));
    }
}
