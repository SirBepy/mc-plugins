package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoardLayoutTest {

    private static Map<String, Integer> counts(int gapple, int scrap, int diamond, int shell, int emerald, int carrot,
                                               int lapis, int gold, int iron) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("gapple", gapple);
        out.put("scrap", scrap);
        out.put("diamond", diamond);
        out.put("shell", shell);
        out.put("emerald", emerald);
        out.put("carrot", carrot);
        out.put("lapis", lapis);
        out.put("gold", gold);
        out.put("iron", iron);
        return out;
    }

    private static String render(HoardLayout.Slot[] slots) {
        StringBuilder out = new StringBuilder();
        for (HoardLayout.Slot slot : slots) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(slot == null ? "-" : slot.key() + "x" + slot.count());
        }
        return out.toString();
    }

    // Expected strings come from 10ca's radial() mockup (.for_bepy/hoard-mockup/build.py), run on
    // the same four boxes Joe approved in the render.
    @Test
    void matchesTheApprovedMockupForAFledgelingBoxWithNoRoll() {
        assertEquals("ironx1 lapisx3 goldx3 carrotx3 shellx1 carrotx3 goldx3 lapisx3 ironx1 ironx2 ironx2 lapisx3 emeraldx2 "
                        + "diamondx2 emeraldx1 lapisx3 ironx2 ironx2 ironx1 lapisx2 goldx3 carrotx3 shellx1 carrotx3 goldx3 lapisx2 ironx1",
                render(HoardLayout.layout(0, counts(0, 0, 2, 2, 3, 12, 16, 12, 12))));
    }

    @Test
    void matchesTheApprovedMockupForAFerociousBoxWithOneRoll() {
        assertEquals("lapisx11 goldx12 carrotx12 shellx2 gapplex1 shellx2 carrotx12 goldx12 lapisx11 ironx24 lapisx11 emeraldx9 "
                        + "diamondx4 special:0x1 diamondx4 emeraldx9 lapisx11 ironx24 lapisx10 goldx12 carrotx12 shellx1 gapplex1 shellx1 "
                        + "carrotx12 goldx12 lapisx10",
                render(HoardLayout.layout(1, counts(2, 0, 8, 6, 18, 48, 64, 48, 48))));
    }

    @Test
    void matchesTheApprovedMockupForAPrimordialBoxWithScrap() {
        assertEquals("ironx18 lapisx24 emeraldx6 diamondx3 gapplex2 diamondx2 emeraldx6 lapisx24 ironx18 goldx36 carrotx26 shellx3 "
                        + "scrapx1 special:0x1 scrapx1 shellx3 carrotx26 goldx36 ironx18 lapisx24 emeraldx6 diamondx2 gapplex1 diamondx2 "
                        + "emeraldx6 lapisx24 ironx18",
                render(HoardLayout.layout(1, counts(3, 2, 9, 6, 24, 52, 96, 72, 72))));
    }

    @Test
    void matchesTheApprovedMockupForThreeRollsStackedInTheCentreColumn() {
        assertEquals("ironx24 lapisx32 emeraldx9 diamondx3 special:1x1 diamondx3 emeraldx9 lapisx32 ironx24 goldx48 carrotx28 shellx3 "
                        + "gapplex2 special:0x1 gapplex2 shellx3 carrotx28 goldx48 ironx24 lapisx32 emeraldx9 diamondx2 special:2x1 "
                        + "diamondx2 emeraldx9 lapisx32 ironx24",
                render(HoardLayout.layout(3, counts(4, 0, 10, 6, 36, 56, 128, 96, 96))));
    }

    // The mockup dropped an item with fewer items than any free group (one scrap once a roll took
    // the centre); nothing may ever be lost from a hoard.
    @Test
    void aSingleScrapBesideACentreRollIsNotLost() {
        HoardLayout.Slot[] slots = HoardLayout.layout(1, counts(4, 1, 10, 6, 36, 56, 128, 96, 96));

        assertEquals(1, total(slots, "scrap"));
    }

    @Test
    void everyTierAndRollCountKeepsEveryItemInStacksOfAtMost64() {
        for (Tier tier : Tier.values()) {
            TierRewardRow row = TierRewardRow.load(null).get(tier);
            for (int specials = 0; specials <= RollEngine.MAX_ROLLS + 1; specials++) {
                for (int scrap = 0; scrap <= 2; scrap++) {
                    Map<String, Integer> counts = counts(row.gapples(), scrap, row.diamonds(), row.shells(),
                            row.filler().emeralds(), row.filler().carrots(), row.filler().lapis(), row.filler().gold(),
                            row.filler().iron());
                    HoardLayout.Slot[] slots = HoardLayout.layout(specials, counts);
                    String label = tier + " specials=" + specials + " scrap=" + scrap;
                    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                        assertEquals(entry.getValue().intValue(), total(slots, entry.getKey()), label + " " + entry.getKey());
                    }
                    Map<String, Integer> specialsSeen = new HashMap<>();
                    for (HoardLayout.Slot slot : slots) {
                        if (slot != null) {
                            assertTrue(slot.count() >= 1 && slot.count() <= 64, label + " " + slot);
                            if (slot.key().startsWith("special:")) {
                                specialsSeen.merge(slot.key(), 1, Integer::sum);
                            }
                        }
                    }
                    assertEquals(specials, specialsSeen.size(), label + " specials placed");
                }
            }
        }
    }

    private static int total(HoardLayout.Slot[] slots, String key) {
        int sum = 0;
        for (HoardLayout.Slot slot : slots) {
            if (slot != null && slot.key().equals(key)) {
                sum += slot.count();
            }
        }
        return sum;
    }

    // 10ca's spec: the final-blow helm is the best item, so it takes the centre even when it and a
    // roll make an even count.
    @Test
    void theFinalBlowHelmAlwaysTakesTheCentre() {
        HoardLayout.Slot[] slots = HoardLayout.layout(2, true, counts(4, 0, 10, 6, 36, 56, 128, 96, 96));

        assertEquals("special:0", slots[13].key());
        assertEquals("special:1", slots[4].key(), "the roll goes right above it");
    }
}
