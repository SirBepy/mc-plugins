package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstDragonTest extends GuildTestBase {

    private FirstDragon firstDragon;
    private PlayerMock bob;

    @BeforeEach
    void setUpDragon() {
        firstDragon = new FirstDragon(guild, Logger.getLogger("guild-test"));
        bob = server.addPlayer("Bob");
    }

    @Test
    void everyMemberWhoFoughtIsPaidTitledAndRecordedOutsideTheCaps() {
        Member a = join(alice);
        Member g = join(gm);
        a.setContractsDone(4);
        a.setCoinsToday(config.get().dailyCoinCap());
        long treasury = store.treasury().balance();

        List<String> party = firstDragon.dragonKilled(List.of(alice.getUniqueId(), bob.getUniqueId(), gm.getUniqueId()));

        assertEquals(List.of("Alice", "ImMijo"), party);
        assertEquals(FirstDragon.COINS, bank.balance(alice), "paid even with today's coin cap used up");
        assertEquals(FirstDragon.COINS, bank.balance(gm), "the Guildmaster qualifies like anyone else");
        assertEquals(0, bank.balance(bob), "not a member: nothing from the Guild");
        assertEquals(FirstDragon.REPUTATION, a.reputation());
        assertEquals(4, a.contractsDone(), "the dragon is no contract");
        assertEquals(config.get().dailyCoinCap(), a.coinsToday(), "outside the daily caps");
        assertTrue(a.titles().contains(FirstDragon.TITLE));
        assertTrue(g.titles().contains(FirstDragon.TITLE));
        assertEquals(treasury + 2 * Math.round(FirstDragon.COINS * config.get().commissionRate()), store.treasury().balance(),
                "the commission is minted on top, as on a Grand Quest share");
        assertEquals(List.of("Alice", "ImMijo"), List.copyOf(FirstDragon.party(store).values()));
    }

    @Test
    void onlyTheFirstDragonEverPays() {
        Member a = join(alice);
        firstDragon.dragonKilled(List.of(alice.getUniqueId()));
        join(gm);

        assertEquals(List.of(), firstDragon.dragonKilled(List.of(alice.getUniqueId(), gm.getUniqueId())));

        assertEquals(FirstDragon.COINS, bank.balance(alice));
        assertEquals(FirstDragon.REPUTATION, a.reputation());
        assertEquals(0, bank.balance(gm));
        assertEquals(List.of("Alice"), List.copyOf(FirstDragon.party(store).values()));
    }

    @Test
    void aFirstDragonWithNoMemberInTheFightStillSpendsTheEvent() {
        join(alice);
        firstDragon.dragonKilled(List.of(bob.getUniqueId()));

        firstDragon.dragonKilled(List.of(alice.getUniqueId()));

        assertTrue(firstDragon.done());
        assertEquals(0, bank.balance(alice));
    }

    @Test
    void anEndThatAlreadySawADragonDieSpendsTheEvent() {
        join(alice);
        firstDragon.endLoaded("world_the_end", false);
        assertFalse(firstDragon.done(), "a fresh End leaves the event armed");

        firstDragon.endLoaded("world_the_end", true);
        firstDragon.dragonKilled(List.of(alice.getUniqueId()));

        assertTrue(FirstDragon.earlier(store));
        assertEquals(0, bank.balance(alice));
    }

    @Test
    void theGuildsOwnKillIsNotUndoneByTheEndsFlagAtTheNextBoot() {
        join(alice);
        firstDragon.dragonKilled(List.of(alice.getUniqueId()));

        firstDragon.endLoaded("world_the_end", true);

        assertFalse(FirstDragon.earlier(store));
        assertEquals(List.of("Alice"), List.copyOf(FirstDragon.party(store).values()));
    }

    @Test
    void anOfflineFighterIsPaidAtOnceAndToldOnJoin() {
        Member a = join(alice);
        alice.disconnect();

        firstDragon.dragonKilled(List.of(alice.getUniqueId()));

        assertEquals(FirstDragon.COINS, bank.balance(alice));
        assertEquals(FirstDragon.REPUTATION, a.reputation());
        alice.reconnect();
        while (alice.nextComponentMessage() != null) {
            // drop anything said before the join
        }
        firstDragon.joined(alice);
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(alice.nextComponentMessage()).contains("While you were away"));
        firstDragon.joined(alice);
        assertEquals(null, alice.nextComponentMessage(), "told once");
    }

    @Test
    void coinsTheBankRefusedArePaidOnTheNextJoin() {
        join(alice);
        bank.up = false;

        firstDragon.dragonKilled(List.of(alice.getUniqueId()));
        assertTrue(firstDragon.unpaid(alice.getUniqueId()));
        assertEquals(0, bank.balance(alice));

        bank.up = true;
        firstDragon.joined(alice);

        assertEquals(FirstDragon.COINS, bank.balance(alice));
        assertFalse(firstDragon.unpaid(alice.getUniqueId()));
    }

    @Test
    void thePartySurvivesARestart() {
        join(alice);
        firstDragon.dragonKilled(List.of(alice.getUniqueId()));

        GuildStore reloaded = newStore();

        assertTrue(FirstDragon.done(reloaded));
        assertEquals(List.of("Alice"), List.copyOf(FirstDragon.party(reloaded).values()));
    }

    @Test
    void adminStatusAndResetForTestingAndRepair() {
        join(alice);
        assertTrue(FirstDragon.describe(store, config.get().zone()).contains("armed"));
        bank.up = false;
        firstDragon.dragonKilled(List.of(alice.getUniqueId()));
        String status = FirstDragon.describe(store, config.get().zone());
        assertTrue(status.contains("party Alice") && status.contains("coins still owed to Alice"), status);

        FirstDragon.reset(store);

        assertFalse(FirstDragon.done(store));
        assertTrue(FirstDragon.describe(store, config.get().zone()).contains("armed"));
    }

    @Test
    void namesReadAsAList() {
        assertEquals("A", FirstDragon.list(List.of("A")));
        assertEquals("A and B", FirstDragon.list(List.of("A", "B")));
        assertEquals("A, B and C", FirstDragon.list(List.of("A", "B", "C")));
    }
}
