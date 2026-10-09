package dev.sirbepy.mc.endfight.reset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreRunTest {

    private static List<ChangedBlock> blocks(int count) {
        List<ChangedBlock> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(new ChangedBlock(i, 0, 0, "minecraft:obsidian"));
        }
        return out;
    }

    @Test
    void spendsExactlyTheBudgetPerCallUntilExhausted() {
        RestoreRun run = new RestoreRun(blocks(100));

        assertEquals(40, run.takeNext(40).size());
        assertFalse(run.isDone());
        assertEquals(40, run.takeNext(40).size());
        assertFalse(run.isDone());
        assertEquals(20, run.takeNext(40).size(), "the last batch is short - only 20 blocks remained");
        assertTrue(run.isDone());
    }

    @Test
    void takeNextAfterDoneReturnsNothing() {
        RestoreRun run = new RestoreRun(blocks(10));
        run.takeNext(10);

        assertTrue(run.isDone());
        assertEquals(0, run.takeNext(40).size());
    }

    @Test
    void anEmptyPlanIsDoneImmediately() {
        RestoreRun run = new RestoreRun(List.of());

        assertTrue(run.isDone());
        assertEquals(0, run.total());
    }

    @Test
    void appliedAndRemainingTrackTheCursor() {
        RestoreRun run = new RestoreRun(blocks(50));

        run.takeNext(30);

        assertEquals(30, run.applied());
        assertEquals(20, run.remaining());
    }
}
