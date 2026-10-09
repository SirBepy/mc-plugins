package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PillarScannerTest {

    private FakeBlockLookup seeded(Pillar p) {
        FakeBlockLookup lookup = new FakeBlockLookup();
        int r = p.radius();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                if (dx * dx + dz * dz <= r * r + 1) {
                    lookup.set(p.cx() + dx, p.topY() - 1, p.cz() + dz, Material.OBSIDIAN);
                }
            }
        }
        lookup.set(p.cx() + r + 3, p.surfaceY(), p.cz(), Material.END_STONE);
        return lookup;
    }

    @Test
    void recoversCentreRadiusTopYAndSurfaceYFromAKnownPillar() {
        Pillar expected = new Pillar(42, 0, 4, 94, 64);
        FakeBlockLookup lookup = seeded(expected);

        Optional<Pillar> found = PillarScanner.scan(lookup, expected.cx(), expected.cz());

        assertTrue(found.isPresent());
        assertEquals(expected, found.get());
    }

    @Test
    void worksForEveryEntryInTheSampleSet() {
        for (Pillar expected : EndFightTestSupport.samplePillars()) {
            FakeBlockLookup lookup = seeded(expected);
            Optional<Pillar> found = PillarScanner.scan(lookup, expected.cx(), expected.cz());
            assertTrue(found.isPresent(), "expected a pillar at " + expected.cx() + "," + expected.cz());
            assertEquals(expected, found.get());
        }
    }

    @Test
    void aColumnWithNoObsidianIsSkippedNotGuessed() {
        FakeBlockLookup lookup = new FakeBlockLookup(); // completely empty: no chunk generated yet

        Optional<Pillar> found = PillarScanner.scan(lookup, 42, 0);

        assertTrue(found.isEmpty());
    }

    @Test
    void scanAllSkipsOnlyTheMissingColumnAndKeepsTheRest() {
        List<Pillar> sample = EndFightTestSupport.samplePillars();
        FakeBlockLookup lookup = new FakeBlockLookup();
        for (int i = 1; i < sample.size(); i++) { // deliberately skip index 0
            Pillar p = sample.get(i);
            int r = p.radius();
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (dx * dx + dz * dz <= r * r + 1) {
                        lookup.set(p.cx() + dx, p.topY() - 1, p.cz() + dz, Material.OBSIDIAN);
                    }
                }
            }
            lookup.set(p.cx() + r + 3, p.surfaceY(), p.cz(), Material.END_STONE);
        }

        List<Pillar> found = PillarScanner.scanAll(lookup);

        assertEquals(sample.size() - 1, found.size());
        assertFalse(found.contains(sample.get(0)));
    }
}
