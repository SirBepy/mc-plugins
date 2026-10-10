package dev.sirbepy.mc.endfight.opening;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongholdWarpTest {

    @Test
    void onlyWarpEndIsTakenFromHuskHomes() {
        assertTrue(StrongholdWarp.isWarpEnd("/warp end"));
        assertTrue(StrongholdWarp.isWarpEnd("/WARP End "));
        assertTrue(StrongholdWarp.isWarpEnd("/huskhomes:warp end"));
        assertFalse(StrongholdWarp.isWarpEnd("/warp guild"));
        assertFalse(StrongholdWarp.isWarpEnd("/warp endzone"));
        assertFalse(StrongholdWarp.isWarpEnd("/warp"));
        assertFalse(StrongholdWarp.isWarpEnd("/setwarp end"));
    }

    @Test
    void thePortalRoomIsTheElevenByEightBySixteenPieceEitherWayRound() {
        assertTrue(StrongholdWarp.isPortalRoomSize(new BoundingBox(0, 0, 0, 10, 7, 15)));
        assertTrue(StrongholdWarp.isPortalRoomSize(new BoundingBox(0, 0, 0, 16, 8, 11)));
        assertFalse(StrongholdWarp.isPortalRoomSize(new BoundingBox(0, 0, 0, 5, 5, 7)));
        assertFalse(StrongholdWarp.isPortalRoomSize(new BoundingBox(0, 0, 0, 11, 2, 16)));
    }

    @Test
    void piecesNarrowToThePortalRoomOrFallBackToAll() {
        BoundingBox corridor = new BoundingBox(0, 0, 0, 4, 4, 6);
        BoundingBox room = new BoundingBox(10, 0, 10, 20, 7, 25);

        assertEquals(List.of(room), StrongholdWarp.portalRoomCandidates(List.of(() -> corridor, () -> room)));
        assertEquals(List.of(corridor), StrongholdWarp.portalRoomCandidates(List.of(() -> corridor)));
    }
}
