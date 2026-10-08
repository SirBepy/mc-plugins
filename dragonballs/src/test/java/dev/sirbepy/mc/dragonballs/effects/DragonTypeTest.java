package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only a Nether Shenron is renamed; the set-independent name the console parsers match stays fixed. */
class DragonTypeTest {

    @Test
    void netherShenronIsNuovaShenron() {
        assertEquals("Nuova Shenron", DragonType.SHENRON.displayName(BallSet.NETHER));
    }

    @Test
    void shenronKeepsItsNameOutsideTheNether() {
        assertEquals("Shenron", DragonType.SHENRON.displayName(BallSet.OVERWORLD));
        assertEquals("Shenron", DragonType.SHENRON.displayName(BallSet.END));
    }

    @Test
    void porungaIsPorungaInEverySet() {
        for (BallSet set : BallSet.values()) {
            assertEquals("Porunga", DragonType.PORUNGA.displayName(set), set.name());
        }
    }

    @Test
    void setIndependentNameNeverChanges() {
        assertEquals("Shenron", DragonType.SHENRON.displayName());
        assertEquals("Porunga", DragonType.PORUNGA.displayName());
    }

    @Test
    void onlyANetherShenronIsNuova() {
        assertTrue(DragonType.SHENRON.isNuova(BallSet.NETHER));
        assertFalse(DragonType.SHENRON.isNuova(BallSet.OVERWORLD));
        assertFalse(DragonType.SHENRON.isNuova(BallSet.END));
        assertFalse(DragonType.PORUNGA.isNuova(BallSet.NETHER));
    }

    @Test
    void onlyANetherPorungaIsPurple() {
        assertTrue(DragonType.PORUNGA.isPurplePorunga(BallSet.NETHER));
        assertFalse(DragonType.PORUNGA.isPurplePorunga(BallSet.OVERWORLD));
        assertFalse(DragonType.PORUNGA.isPurplePorunga(BallSet.END));
        assertFalse(DragonType.SHENRON.isPurplePorunga(BallSet.NETHER));
    }
}
