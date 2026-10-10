package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.item.KillStamp;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selling the target's head, fresh vs. old value, and the death kill-stamp (split out of WantedBoardTest). */
class WantedBoardSaleTest extends WantedBoardTestBase {

    @Test
    void aFreshKillPaysTheLifeItEndedEvenAfterTheStatReset() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        alive.put(target.getUniqueId(), 4 * HOUR);
        WantedBoard board = board(ALWAYS_ONLINE, growing());
        board.rotate();

        clock.set(board.state().postedAt().plusSeconds(5));
        List<ItemStack> drops = new ArrayList<>(List.of(headOf(target.getUniqueId(), "Target")));
        PlayerDeathEvent death = new PlayerDeathEvent(target, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), drops, 0, null);
        board.onPlayerDeath(death);
        // The server resets time_since_death right after the event.
        alive.put(target.getUniqueId(), 0L);

        seller.getInventory().setItemInMainHand(death.getDrops().getFirst());
        board.sell(seller);
        assertEquals(List.of(150.0), paid, "4 h alive when killed: $50 + 4 x $25, not today's $50");
    }

    @Test
    void aFreshHeadWithNoLifeStampPaysTheTargetsLatestDeathSincePosting() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        alive.put(target.getUniqueId(), 2 * HOUR);
        WantedBoard board = board(ALWAYS_ONLINE, growing());
        board.rotate();

        // The death drops no head (a cartel hit: its trophy is made later, with only a kill time).
        clock.set(board.state().postedAt().plusSeconds(5));
        board.onPlayerDeath(new PlayerDeathEvent(target, DamageSource.builder(DamageType.PLAYER_ATTACK).build(),
                new ArrayList<>(), 0, null));
        alive.put(target.getUniqueId(), 0L);

        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), "Target", clock.get()));
        board.sell(seller);
        assertEquals(List.of(100.0), paid, "the 2 h life that death ended");
    }

    @Test
    void anOldHeadPaysTheOldShareOfTodaysLiveValue() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        alive.put(target.getUniqueId(), 0L);
        WantedBoard board = board(ALWAYS_ONLINE, growing());
        board.rotate();
        alive.put(target.getUniqueId(), 6 * HOUR);

        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), "Target"));
        board.sell(seller);
        assertEquals(List.of(100.0), paid, "50% of today's $200");
    }

    @Test
    void onlyOneSaleGoesThroughPerPeriodServerWide() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock firstSeller = server.addPlayer("First");
        PlayerMock secondSeller = server.addPlayer("Second");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();

        firstSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(firstSeller);
        assertEquals(List.of(100.0), paid);

        secondSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(secondSeller);
        assertEquals(List.of(100.0), paid, "the second attempt is not paid");
        assertEquals(Material.PLAYER_HEAD, secondSeller.getInventory().getItemInMainHand().getType(), "kept: nothing to sell anymore");
        assertTrue(secondSeller.nextMessage().contains("already brought in"));
    }

    @Test
    void theTargetCannotSellTheirOwnHead() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();

        target.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(target);

        assertTrue(paid.isEmpty());
        assertEquals(Material.PLAYER_HEAD, target.getInventory().getItemInMainHand().getType());
        assertTrue(target.nextMessage().contains("can't turn in your own head"));
    }

    @Test
    void aHeadOfSomeoneElseOrNoHeadAtAllIsRefused() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();

        seller.getInventory().setItemInMainHand(headOf(UUID.randomUUID(), "SomeoneElse"));
        board.sell(seller);
        assertTrue(seller.nextMessage().contains("The board wants Target's head today"));
        assertTrue(paid.isEmpty());

        seller.getInventory().setItemInMainHand(ItemStack.of(Material.DIRT));
        board.sell(seller);
        assertTrue(seller.nextMessage().contains("The board wants Target's head today"));

        // A head whose profile has no id at all falls back to matching by name; unstamped, so it's
        // an old head and pays the old-head share, not the full price.
        ItemStack nameOnly = ItemStack.of(Material.PLAYER_HEAD);
        nameOnly.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile("Target")));
        seller.getInventory().setItemInMainHand(nameOnly);
        board.sell(seller);
        assertEquals(List.of(50.0), paid, "name-only profile still matches when there is no id to compare");
    }

    @Test
    void whenTheEconomyCannotPayTheHeadAndClaimStayOpen() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();

        bankOpen = false;
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(seller);

        assertTrue(paid.isEmpty());
        assertEquals(Material.PLAYER_HEAD, seller.getInventory().getItemInMainHand().getType(), "the head is kept");
        assertFalse(board.state().claimed(), "still open for a real sale");
        assertTrue(seller.nextMessage().contains("can't pay"));
    }

    @Test
    void freshKillPaysFullAnOldOrUnstampedHeadPaysTheOldShareRounded() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);

        WantedBoard freshBoard = board(ALWAYS_ONLINE, flat(), new File(dir, "fresh.yml"));
        freshBoard.rotate();
        PlayerMock freshSeller = server.addPlayer("FreshSeller");
        freshSeller.getInventory().setItemInMainHand(
                headOf(target.getUniqueId(), target.getName(), freshBoard.state().postedAt().plusSeconds(1)));
        freshBoard.sell(freshSeller);
        assertEquals(List.of(100.0), paid, "a kill timestamped after the bounty posted pays full");

        paid.clear();
        WantedBoard oldBoard = board(ALWAYS_ONLINE, flat(), new File(dir, "old.yml"));
        oldBoard.rotate();
        PlayerMock oldSeller = server.addPlayer("OldSeller");
        oldSeller.getInventory().setItemInMainHand(
                headOf(target.getUniqueId(), target.getName(), oldBoard.state().postedAt().minusSeconds(1)));
        oldBoard.sell(oldSeller);
        assertEquals(List.of(50.0), paid, "a kill timestamped before the bounty posted pays the old-head share, rounded");

        paid.clear();
        WantedBoard unstampedBoard = board(ALWAYS_ONLINE, flat(), new File(dir, "unstamped.yml"));
        unstampedBoard.rotate();
        PlayerMock unstampedSeller = server.addPlayer("UnstampedSeller");
        unstampedSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        unstampedBoard.sell(unstampedSeller);
        assertEquals(List.of(50.0), paid, "a head with no stamp at all pays the old-head share too");
    }

    @Test
    void oldHeadShareRoundsToTheNearestDollar() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        // No growth, no spread: the value is $101 exactly. 101 * 0.5 = 50.5, rounds up to 51.
        WantedBoard board = board(ALWAYS_ONLINE, config(101, 0, 1000, 0));
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(seller);
        assertEquals(List.of(51.0), paid);
    }

    @Test
    void saleBroadcastNamesWhetherTheHeadWasFreshOrOld() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);

        WantedBoard freshBoard = board(ALWAYS_ONLINE, flat(), new File(dir, "fresh.yml"));
        freshBoard.rotate();
        PlayerMock freshSeller = server.addPlayer("FreshSeller");
        freshSeller.getInventory().setItemInMainHand(
                headOf(target.getUniqueId(), target.getName(), freshBoard.state().postedAt().plusSeconds(1)));
        freshBoard.sell(freshSeller);
        assertEquals(List.of("FreshSeller brought in Target's head (fresh kill) for $100!"), broadcasts);

        broadcasts.clear();
        WantedBoard oldBoard = board(ALWAYS_ONLINE, flat(), new File(dir, "old.yml"));
        oldBoard.rotate();
        PlayerMock oldSeller = server.addPlayer("OldSeller");
        oldSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        oldBoard.sell(oldSeller);
        assertEquals(List.of("OldSeller brought in Target's head (old head) for $50!"), broadcasts);
    }

    @Test
    void playerDeathStampsTheirOwnUnstampedHeadDropButNeverAnotherPlayersOrAnAlreadyStampedOne() {
        PlayerMock dead = server.addPlayer("Dead");
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        clock.set(Instant.parse("2026-10-09T12:00:00Z"));
        alive.put(dead.getUniqueId(), 3 * HOUR);

        ItemStack ownHead = headOf(dead.getUniqueId(), "Dead");
        ItemStack someoneElsesHead = headOf(UUID.randomUUID(), "Other");
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        ItemStack alreadyStamped = headOf(dead.getUniqueId(), "Dead", earlier);
        ItemStack dirt = ItemStack.of(Material.DIRT);
        List<ItemStack> drops = new ArrayList<>(List.of(ownHead, someoneElsesHead, alreadyStamped, dirt));

        PlayerDeathEvent event = new PlayerDeathEvent(dead, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), drops, 0, null);
        board.onPlayerDeath(event);

        assertEquals(clock.get(), KillStamp.killedAt(event.getDrops().get(0)).orElseThrow(), "the dead player's own unstamped head is stamped now");
        assertEquals(3 * HOUR, event.getDrops().get(0).getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "alive_ticks"), PersistentDataType.LONG), "with the life that death ended");
        assertTrue(KillStamp.killedAt(event.getDrops().get(1)).isEmpty(), "a head of someone else is left alone");
        assertEquals(earlier, KillStamp.killedAt(event.getDrops().get(2)).orElseThrow(), "an already-stamped head keeps its original time, never re-stamped");
        assertNotNull(event.getDrops().get(3), "a plain non-head drop is untouched");
    }
}
