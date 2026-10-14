package dev.sirbepy.mc.haru.curio;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.rep.Tier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysteryCuriosTest extends HaruTestBase {

    @Test
    void curioCostsPetalsAndStopsAtTheDailyLimit() {
        OwnerState state = state();
        state.setPetals(1000);
        MysteryCurios.Curio curio = ctx.curios().today(state.tier(), clock.today);
        for (int i = 0; i < MysteryCurios.DAILY_LIMIT; i++) {
            List<String> given = new ArrayList<>();
            assertEquals(MysteryCurios.Result.OPENED, ctx.curios().open(owner, state, clock.today, new Random(i), given));
            assertFalse(given.isEmpty());
        }
        assertEquals(1000 - 3 * curio.petals(), state.petals());
        assertEquals(MysteryCurios.Result.SOLD_OUT, ctx.curios().open(owner, state, clock.today, new Random(9), new ArrayList<>()));
        assertEquals(MysteryCurios.Result.OPENED,
                ctx.curios().open(owner, state, clock.today.plusDays(1), new Random(9), new ArrayList<>()));
    }

    @Test
    void noCurioEverHoldsNetherite() {
        for (MysteryCurios.Curio curio : ctx.curios().all()) {
            OwnerState state = new OwnerState();
            for (var reward : curio.contents().values()) {
                for (String given : ctx.rewards().grant(owner, state, reward, false)) {
                    assertFalse(given.toLowerCase().contains("netherite"), curio.id() + ": " + given);
                }
            }
        }
    }

    @Test
    void curioWithoutPetalsTakesNothing() {
        OwnerState state = state();
        assertEquals(MysteryCurios.Result.NO_PETALS, ctx.curios().open(owner, state, clock.today, new Random(1), new ArrayList<>()));
        assertEquals(0, ctx.curios().boughtToday(state, clock.today));
    }

    @Test
    void theDragonCasketWaitsForTrust() {
        Set<String> strangerBoxes = new HashSet<>();
        Set<String> trustedBoxes = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            strangerBoxes.add(ctx.curios().today(Tier.STRANGER, clock.today.plusDays(day)).id());
            trustedBoxes.add(ctx.curios().today(Tier.TRUSTED, clock.today.plusDays(day)).id());
        }
        assertFalse(strangerBoxes.contains("dragon"));
        assertTrue(trustedBoxes.contains("dragon"));
    }

    @Test
    void rewardsUnboundOverridesTheHardcodedCurioBind() {
        OwnerState state = state();
        state.setPetals(1000);
        state.setRewardsUnbound(true);
        List<String> given = new ArrayList<>();

        assertEquals(MysteryCurios.Result.OPENED, ctx.curios().open(owner, state, clock.today, new Random(1), given));

        assertFalse(given.isEmpty());
        for (org.bukkit.inventory.ItemStack item : owner.getInventory().getContents()) {
            if (item != null) {
                assertNull(ctx.bound().ownerOf(item), "curio rewards stay unbound when rewardsUnbound is set");
            }
        }
    }

    @Test
    void everyRewardInEveryBoxCanBeGranted() {
        OwnerState state = state();
        for (MysteryCurios.Curio curio : ctx.curios().all()) {
            for (var reward : curio.contents().values()) {
                ctx.rewards().grant(owner, state, reward, true);
            }
        }
    }
}
