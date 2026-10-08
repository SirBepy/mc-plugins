package dev.sirbepy.mc.guild.data;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildStoreTest extends GuildTestBase {

    @Test
    void everythingSurvivesASaveAndReload() throws Exception {
        Member member = join(alice);
        member.setRank(Rank.C);
        member.setReputation(512);
        member.setContractsDone(33);
        member.dailies().get(0).setProgress(0, 3);
        member.dailies().get(2).markClaimed();
        member.addStat("commissions-done", 2);
        member.setCoinsToday(80);
        store.treasury().record(clock.nowMillis(), LedgerEntry.Kind.ADJUST, 1000, "test");
        store.treasury().addWithdrawal(clock.dayKey(), 250);
        guild.postCommission(gm, new Objective(Objective.Kind.MINE, Set.of("IRON_ORE", "DEEPSLATE_IRON_ORE"), 20, "Mine 20 Iron Ore"), 150, Rank.D);
        Commission commission = store.commissions().getFirst();
        commission.setProgress(alice.getUniqueId(), 7);
        store.setFeaturedId(commission.id());
        store.stores().add(new ItemStack(Material.OAK_LOG, 12));
        store.save();

        GuildStore reloaded = newStore();

        Member back = reloaded.member(alice.getUniqueId());
        assertNotNull(back);
        assertEquals(Rank.C, back.rank());
        assertEquals(512, back.reputation());
        assertEquals(33, back.contractsDone());
        assertEquals(member.dailies().get(0).defId(), back.dailies().get(0).defId());
        assertEquals(3, back.dailies().get(0).progress(0));
        assertTrue(back.dailies().get(2).claimed());
        assertEquals(member.weekly().defId(), back.weekly().defId());
        assertEquals(2, back.stat("commissions-done"));
        assertEquals(80, back.coinsToday());
        assertEquals(store.treasury().balance(), reloaded.treasury().balance());
        assertEquals(250, reloaded.treasury().withdrawnToday(clock.dayKey()));
        assertEquals(store.treasury().ledger().size(), reloaded.treasury().ledger().size());
        Commission c = reloaded.commission(commission.id());
        assertEquals(7, c.progress(alice.getUniqueId()));
        assertEquals(Set.of("IRON_ORE", "DEEPSLATE_IRON_ORE"), c.objective().targets());
        assertEquals(Rank.D, c.minRank());
        assertEquals(commission.id(), reloaded.featuredId());
        assertEquals(12, reloaded.stores().getFirst().getAmount());
        assertEquals("c2", reloaded.nextCommissionId(), "ids keep counting after a reload");
    }

    @Test
    void aPhaseOneCommissionWithoutAPosterStillLoads() throws Exception {
        File file = new File(dataDir.toFile(), "guild.yml");
        Files.writeString(file.toPath(), """
                commissions:
                - id: c1
                  kind: HUNT
                  targets: [ZOMBIE]
                  amount: 5
                  text: Hunt 5 Zombie
                  reward: 100
                  min-rank: D
                  posted: 0
                  week: '2026-10-05'
                  status: OPEN
                  progress: {}
                """);
        Commission c = newStore().commission("c1");
        assertNotNull(c, "the escrowed commission is not dropped");
        assertEquals(dev.sirbepy.mc.guild.config.GuildConfig.IMMIJO, c.poster());
    }

    @Test
    void aCorruptFileIsNeverOverwritten() throws Exception {
        File file = new File(dataDir.toFile(), "guild.yml");
        Files.writeString(file.toPath(), "members: [this is: not: valid");
        GuildStore broken = newStore();
        broken.register(alice.getUniqueId(), "Alice", 0);
        broken.save();
        assertEquals("members: [this is: not: valid", Files.readString(file.toPath()));
    }
}
