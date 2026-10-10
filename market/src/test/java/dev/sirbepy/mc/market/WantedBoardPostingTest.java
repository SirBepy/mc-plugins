package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Target picking, the posting/rest rhythm and the survivor reward (split out of WantedBoardTest). */
class WantedBoardPostingTest extends WantedBoardTestBase {

    @Test
    void theOnlineCoinPicksAnOnlinePlayer() {
        PlayerMock steve = server.addPlayer("Steve");
        online.add(steve);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        assertEquals(steve.getUniqueId(), board.state().targetId());
        assertEquals("Steve", board.state().targetName());
    }

    @Test
    void otherHalfOrWithNobodyOnlineTheTargetComesFromEveryoneWhoEverJoined() {
        PlayerMock steve = server.addPlayer("Steve");
        online.add(steve);
        // hasPlayedBefore() only flips true once MockBukkit sees them disconnect (matches real
        // Bukkit: it reflects playerdata on disk, not "currently connected").
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();
        everJoined.add(alex);
        // The coin says "offline" even though Steve is online right now.
        WantedBoard board = board(NEVER_ONLINE, flat());
        board.rotate();
        assertEquals(alex.getUniqueId(), board.state().targetId());

        // Nobody online at all: always the offline pool, whatever the coin says.
        online.clear();
        WantedBoard noOneOnline = board(ALWAYS_ONLINE, flat());
        noOneOnline.rotate();
        assertEquals(alex.getUniqueId(), noOneOnline.state().targetId());
    }

    @Test
    void noTargetWhenNobodyIsOnlineAndNobodyHasEverJoined() {
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        assertFalse(board.state().hasTarget());
        assertNull(board.state().targetId());
    }

    @Test
    void bannedPlayersAreExcludedFromBothPools() {
        PlayerMock steve = server.addPlayer("Steve");
        steve.ban("test", (Date) null, null);
        online.add(steve);
        // Has genuinely played before (disconnected once), banned regardless: still excluded.
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();
        alex.ban("test", (Date) null, null);
        everJoined.add(alex);

        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        assertFalse(board.state().hasTarget(), "the only candidates in either pool are banned");
    }

    @Test
    void onlineChanceIsEightyPercent() {
        PlayerMock steve = server.addPlayer("Steve");
        online.add(steve);
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();
        everJoined.add(alex);

        WantedBoard under = board(rolling(0.79), flat(), new File(dir, "under.yml"));
        under.rotate();
        assertEquals(steve.getUniqueId(), under.state().targetId(), "a roll under 0.8 picks from the online players");

        WantedBoard over = board(rolling(0.81), flat(), new File(dir, "over.yml"));
        over.rotate();
        assertEquals(alex.getUniqueId(), over.state().targetId(), "a roll over 0.8 picks from everyone who ever joined");
    }

    @Test
    void factorStaysWithinTheSpread() {
        assertEquals(0.9, Market.factor(rolling(0), 0.1), 1e-9);
        assertEquals(1.0, Market.factor(rolling(0.5), 0.1), 1e-9);
        assertEquals(1.1, Market.factor(rolling(1), 0.1), 1e-9);
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            double f = Market.factor(random, 0.1);
            assertTrue(f >= 0.9 && f <= 1.1, "factor " + f);
        }
    }

    @Test
    void theFactorIsRolledOnceAtPostingAndKeptAcrossARestart() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        File stateFile = new File(dir, "wanted.yml");
        // rolling(0.7): online (under 0.8), and a factor of 1 + 0.1 * (2 * 0.7 - 1) = 1.04.
        WantedBoard board = board(rolling(0.7), config(50, 25, 500, 0.1), stateFile);
        board.rotate();
        assertEquals(1.04, board.state().factor(), 1e-9);

        WantedBoard restarted = board(rolling(0), config(50, 25, 500, 0.1), stateFile);
        assertEquals(1.04, restarted.state().factor(), 1e-9, "the saved factor, not a new roll");
    }

    @Test
    void aTargetWhoPlayedAndDidNotDieGetsHalfTheValueWhenTheBoardMovesOn() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        alive.put(target.getUniqueId(), 2 * HOUR);
        WantedBoard board = board(ALWAYS_ONLINE, growing());
        board.rotate();
        alive.put(target.getUniqueId(), 6 * HOUR);

        period.set("day-2");
        assertTrue(board.refresh());
        assertEquals(List.of(100.0), paid, "half of the $200 bounty at the change");
        assertEquals(List.of(target.getUniqueId()), paidTo);
        assertEquals(List.of("Target survived the bounty and earned $100!"), broadcasts);
    }

    @Test
    void noSurvivorRewardAfterADeathASaleOrAnAdminRotate() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        alive.put(target.getUniqueId(), 2 * HOUR);

        WantedBoard died = board(ALWAYS_ONLINE, growing(), new File(dir, "died.yml"));
        died.rotate();
        died.onPlayerDeath(new PlayerDeathEvent(target, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>(), 0, null));
        period.set("day-2");
        died.refresh();
        assertTrue(paid.isEmpty(), "died since posting, even with nobody turning the head in");

        period.set("day-1");
        WantedBoard sold = board(ALWAYS_ONLINE, growing(), new File(dir, "sold.yml"));
        sold.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), "Target"));
        sold.sell(seller);
        paid.clear();
        period.set("day-2");
        sold.refresh();
        assertTrue(paid.isEmpty(), "a claimed bounty pays no survivor");

        WantedBoard rotated = board(ALWAYS_ONLINE, growing(), new File(dir, "rotated.yml"));
        rotated.rotate();
        rotated.rotate();
        assertTrue(paid.isEmpty(), "the admin rotate never pays");
    }

    @Test
    void anOfflineTargetGetsTheSurvivorRewardOnlyIfTheyJoinedSincePosting() {
        PlayerMock target = server.addPlayer("Target");
        target.disconnect();
        everJoined.add(target);
        alive.put(target.getUniqueId(), 2 * HOUR);

        WantedBoard idle = board(NEVER_ONLINE, growing(), new File(dir, "idle.yml"));
        idle.rotate();
        assertFalse(idle.state().played());
        period.set("day-2");
        idle.refresh();
        assertTrue(paid.isEmpty(), "never logged in: no reward for not playing");

        period.set("day-1");
        WantedBoard joined = board(NEVER_ONLINE, growing(), new File(dir, "joined.yml"));
        joined.rotate();
        joined.onJoin(new PlayerJoinEvent(target, Component.empty()));
        assertTrue(joined.state().played());
        period.set("day-2");
        joined.refresh();
        assertEquals(List.of(50.0), paid, "joined once since posting: half of $100");
    }

    @Test
    void stateSurvivesARestartAndANewPeriodPicksAgain() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        File stateFile = new File(dir, "wanted.yml");
        WantedBoard first = board(ALWAYS_ONLINE, flat(), stateFile);
        assertTrue(first.refresh(), "first start has no state yet");
        assertEquals(target.getUniqueId(), first.state().targetId());

        WantedBoard restarted = board(NEVER_ONLINE, flat(), stateFile);
        assertFalse(restarted.refresh(), "same period: keeps the saved target, does not re-roll");
        assertEquals(target.getUniqueId(), restarted.state().targetId());
        assertEquals(first.state().factor(), restarted.state().factor());
        assertEquals(first.state().postedAt(), restarted.state().postedAt());
        assertEquals(first.state().played(), restarted.state().played());

        // restarted was built with NEVER_ONLINE, so the new pick still reads the offline pool;
        // "Other" needs to have genuinely played before (disconnected once) to qualify for it.
        PlayerMock other = server.addPlayer("Other");
        other.disconnect();
        online.clear();
        everJoined.add(other);
        period.set("day-2");
        assertTrue(restarted.refresh());
        assertTrue(restarted.state().cooldown(), "the day after a bounty rests");
        period.set("day-3");
        assertTrue(restarted.refresh(), "the day after the rest picks again");
        assertEquals(other.getUniqueId(), restarted.state().targetId());
    }

    @Test
    void cooldownFollowsEveryBountyClaimedOrNot() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        assertFalse(board.state().claimed());

        period.set("day-2");
        assertTrue(board.refresh());
        assertTrue(board.state().cooldown(), "nobody claimed it, and the next period still rests");
        assertFalse(board.state().hasTarget());

        period.set("day-3");
        assertTrue(board.refresh());
        assertTrue(board.state().hasTarget(), "the period after the rest picks");

        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);
        assertTrue(board.state().claimed());

        period.set("day-4");
        assertTrue(board.refresh());
        assertTrue(board.state().cooldown(), "the period right after a claimed sale rests");
        assertFalse(board.state().hasTarget());

        period.set("day-5");
        assertTrue(board.refresh());
        assertFalse(board.state().cooldown(), "the period after the rest picks normally again");
        assertTrue(board.state().hasTarget());
    }

    @Test
    void tooFewOnlineMeansNoBountyAndTheNextPeriodTriesAgain() {
        // Joe's 2026-10-10 rule: 3 online is not enough, 4 is.
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(WantedBoardPostingTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        MarketConfig shipped = new MarketConfig(yaml);
        assertEquals(4, shipped.wantedMinOnline());
        for (String name : List.of("A", "B", "C")) {
            online.add(server.addPlayer(name));
        }
        WantedBoard board = board(ALWAYS_ONLINE, shipped);
        board.rotate();
        assertFalse(board.state().hasTarget(), "3 online: no bounty");
        assertFalse(board.state().cooldown(), "not a rest either");
        board.apply(world);
        assertEquals("No bounty", plainLine((Sign) world.getBlockAt(20, 70, 0).getState(), 1));

        online.add(server.addPlayer("D"));
        period.set("day-2");
        assertTrue(board.refresh());
        assertTrue(board.state().hasTarget(), "4 online the next period: the skipped day doesn't count as a bounty, so it picks");

        // A bounty already up stays up when players leave; the next period rests as usual.
        online.clear();
        period.set("day-3");
        board.refresh();
        assertTrue(board.state().cooldown());
        period.set("day-4");
        board.refresh();
        assertFalse(board.state().hasTarget(), "nobody online after the rest: no bounty");
        assertFalse(board.state().cooldown());
    }

    @Test
    void cooldownSurvivesARestart() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        File stateFile = new File(dir, "wanted.yml");
        WantedBoard board = board(ALWAYS_ONLINE, flat(), stateFile);
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);

        period.set("day-2");
        board.refresh();
        assertTrue(board.state().cooldown());

        WantedBoard restarted = board(ALWAYS_ONLINE, flat(), stateFile);
        assertFalse(restarted.refresh(), "same period: keeps the saved cooldown, does not re-roll");
        assertTrue(restarted.state().cooldown());
        assertFalse(restarted.state().hasTarget());

        period.set("day-3");
        assertTrue(restarted.refresh());
        assertFalse(restarted.state().cooldown(), "the period after the rest picks normally");
        assertTrue(restarted.state().hasTarget());
    }

    @Test
    void adminRotateForcesANormalPickRightAfterASaleInsteadOfQueuingACooldown() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);
        assertTrue(board.state().claimed(), "a plain refresh() from here would queue a cooldown");

        // The admin command, same period: forces a normal pick right away instead.
        board.rotate();
        assertFalse(board.state().cooldown());
        assertTrue(board.state().hasTarget());
        assertFalse(board.state().claimed(), "a freshly picked target starts unclaimed");
    }

    @Test
    void adminRotateDuringAnActualCooldownPeriodAlsoPicksNormally() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, flat());
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);

        period.set("day-2");
        board.refresh();
        assertTrue(board.state().cooldown());

        board.rotate();
        assertFalse(board.state().cooldown(), "the admin command forces a normal pick even during the rest period");
        assertTrue(board.state().hasTarget());
    }
}
