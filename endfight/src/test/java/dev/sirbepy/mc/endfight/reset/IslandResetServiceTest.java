package dev.sirbepy.mc.endfight.reset;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandResetServiceTest {

    private static final class CountingTaker implements SnapshotCapture {
        final AtomicInteger captures = new AtomicInteger();

        @Override
        public SnapshotStats capture(World end) {
            captures.incrementAndGet();
            return new SnapshotStats(0, 0, 0);
        }
    }

    @Test
    void onFirstBuildCapturesOnlyWhenNoSnapshotExistsYet() {
        CountingTaker taker = new CountingTaker();
        BooleanSupplier exists = () -> false;
        IslandResetService service = new IslandResetService(taker, fakeStore(exists), Logger.getLogger("test"));

        service.onFirstBuild(null);

        assertEquals(1, taker.captures.get());
    }

    @Test
    void onFirstBuildSkipsWhenASnapshotAlreadyExists() {
        CountingTaker taker = new CountingTaker();
        IslandResetService service = new IslandResetService(taker, fakeStore(() -> true), Logger.getLogger("test"));

        service.onFirstBuild(null);

        assertEquals(0, taker.captures.get(), "a restart must never silently overwrite an existing snapshot");
    }

    @Test
    void captureNowAlwaysRetakesEvenIfOneAlreadyExists() {
        CountingTaker taker = new CountingTaker();
        IslandResetService service = new IslandResetService(taker, fakeStore(() -> true), Logger.getLogger("test"));

        service.captureNow(null);
        service.captureNow(null);

        assertEquals(2, taker.captures.get(), "/endfight snapshot always retakes");
    }

    /** A minimal store whose only behaviour this test needs is {@code exists()}. */
    private static IslandSnapshotStore fakeStore(BooleanSupplier exists) {
        return new IslandSnapshotStore(new java.io.File("unused.bin"), Logger.getLogger("test")) {
            @Override
            public boolean exists() {
                return exists.getAsBoolean();
            }
        };
    }
}
