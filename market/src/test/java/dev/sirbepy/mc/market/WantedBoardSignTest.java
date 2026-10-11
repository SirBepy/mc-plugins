package dev.sirbepy.mc.market;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The WANTED head/sign rendering and /market's today line (split out of WantedBoardTest). */
class WantedBoardSignTest extends WantedBoardTestBase {

    @Test
    void theSignAndMarketLineShowTheLiveValueAndTheMinuteTickRedrawsIt() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        alive.put(target.getUniqueId(), 2 * HOUR);
        WantedBoard board = board(ALWAYS_ONLINE, growing());
        board.rotate();
        board.apply(world);
        Sign sign = (Sign) world.getBlockAt(20, 70, 0).getState();
        assertEquals("Fresh: $100", plainLine(sign, 2));
        assertEquals("Old: $50", plainLine(sign, 3));

        alive.put(target.getUniqueId(), 6 * HOUR);
        // The tick leaves an unloaded sign chunk alone; someone standing at the board loads it.
        world.loadChunk(20 >> 4, 0);
        board.tick();
        Sign later = (Sign) world.getBlockAt(20, 70, 0).getState();
        assertEquals("Fresh: $200", plainLine(later, 2), "6 h alive: $50 + 6 x $25");
        assertEquals("Old: $100", plainLine(later, 3));
        String line = PlainTextComponentSerializer.plainText().serialize(board.todayLines().getFirst());
        assertTrue(line.contains("200") && line.contains("100"), line);
    }

    @Test
    void signShowsFreshAndOldPricesWhileOpenThenClaimedThenCooldown() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        board.apply(world);

        var headBlock = world.getBlockAt(20, 71, 0);
        assertEquals(Material.PLAYER_WALL_HEAD, headBlock.getType());
        assertEquals(BlockFace.WEST, ((Directional) headBlock.getBlockData()).getFacing());
        Skull skull = (Skull) headBlock.getState();
        assertEquals(target.getUniqueId(), skull.getPlayerProfile().getId());

        var signBlock = world.getBlockAt(20, 70, 0);
        Sign sign = (Sign) signBlock.getState();
        assertEquals("WANTED", plainLine(sign, 0));
        assertEquals("Target", plainLine(sign, 1));
        assertEquals("Fresh: $100", plainLine(sign, 2));
        assertEquals("Old: $50", plainLine(sign, 3));

        // A kill timestamped after the bounty posted: a fresh head, full price.
        clock.set(board.state().postedAt().plusSeconds(10));
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), clock.get()));
        board.sell(seller);

        assertEquals(List.of(100.0), paid);
        assertEquals(Material.AIR, seller.getInventory().getItemInMainHand().getType());
        Sign claimed = (Sign) signBlock.getState();
        assertEquals("Target", plainLine(claimed, 1));
        assertEquals("Claimed by", plainLine(claimed, 2));
        assertEquals("Seller", plainLine(claimed, 3));
        assertEquals(List.of("Seller brought in Target's head (fresh kill) for $100!"), broadcasts);

        // The period right after a claimed sale rests: no target, a different sign, head cleared.
        period.set("day-2");
        assertTrue(board.refresh(), "a new period after the sale");
        assertTrue(board.state().cooldown(), "the period right after a sale rests");
        assertFalse(board.state().hasTarget());
        board.apply(world);
        Sign resting = (Sign) signBlock.getState();
        assertEquals("WANTED", plainLine(resting, 0));
        assertEquals("Next bounty", plainLine(resting, 1));
        assertEquals("tomorrow", plainLine(resting, 2));
        assertEquals("", plainLine(resting, 3));
        assertEquals(Material.AIR, world.getBlockAt(20, 71, 0).getType(), "the head spot is cleared during the rest");
    }

    @Test
    void marketTodayLineShowsBothFreshAndOldPrices() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();

        String line = PlainTextComponentSerializer.plainText().serialize(board.todayLines().getFirst());
        assertTrue(line.contains("100") && line.contains("50"), "shows both the fresh and the old-head price: " + line);
    }

    @Test
    void disablingTheBoardStopsPicksPayoutsAndTheSignButKeepsTheSavedStateUntouched() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        AtomicReference<MarketConfig> cfg = new AtomicReference<>(flat());
        WantedBoard board = board(ALWAYS_ONLINE, cfg::get, new File(dir, "wanted.yml"));
        board.rotate();
        board.apply(world);
        assertTrue(board.state().hasTarget(), "posted while enabled");
        assertEquals("Target", plainLine((Sign) world.getBlockAt(20, 70, 0).getState(), 1));

        // /market reload flipping wanted.enabled to false, no restart.
        cfg.set(config(100, 0, 1000, 0, false));

        period.set("day-2");
        assertFalse(board.refresh(), "disabled: no pick even on a new period");
        board.rotate();
        assertTrue(board.state().hasTarget(), "disabled rotate is a no-op; the state on disk from before is untouched");

        board.apply(world);
        Sign sign = (Sign) world.getBlockAt(20, 70, 0).getState();
        assertEquals("No bounty", plainLine(sign, 1), "sign shows no bounty while disabled, even with a target still saved");
        assertEquals(Material.AIR, world.getBlockAt(20, 71, 0).getType(), "head cleared too");

        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);
        assertTrue(paid.isEmpty(), "no payout while disabled");
        assertFalse(board.state().claimed(), "the saved state is untouched, not claimed");

        String line = PlainTextComponentSerializer.plainText().serialize(board.todayLines().getFirst());
        assertTrue(line.contains("No bounty"), line);

        // Flip back on: the period moved on to day-2 while disabled, so the still-open (unclaimed)
        // day-1 bounty is treated the same as any bounty whose period passed unclaimed: it rests.
        cfg.set(flat());
        assertTrue(board.refresh(), "the period changed while disabled; back on, it catches up");
        assertTrue(board.state().cooldown());
        assertFalse(board.state().hasTarget());
    }
}
