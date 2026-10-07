package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Trials;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialTest extends GuildTestBase {

    private Member qualified() {
        Member member = join(alice);
        member.setReputation(Rank.D.reputation());
        member.setContractsDone(Rank.D.contracts());
        return member;
    }

    @Test
    void aTrialCannotStartBeforeQualifying() {
        join(alice);
        assertEquals(Guild.TrialStart.NOT_ELIGIBLE, guild.startTrial(alice));
    }

    @Test
    void passingTheTrialPromotes() {
        Member member = qualified();
        assertEquals(Guild.TrialStart.STARTED, guild.startTrial(alice));
        assertEquals(Guild.TrialStart.ALREADY_ACTIVE, guild.startTrial(alice));
        assertEquals("trial_d", member.trial().defId());

        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE", "#HOSTILE"), 25);
        assertEquals(Guild.ClaimResult.MISSING_ITEMS, guild.claim(alice, member.trial()).result());
        alice.getInventory().addItem(new ItemStack(Material.IRON_SWORD));

        Guild.Claim claim = guild.claim(alice, member.trial());

        assertEquals(Guild.ClaimResult.PROMOTED, claim.result());
        assertEquals(Rank.D, member.rank());
        assertNull(member.trial());
        assertTrue(claim.items().size() > 0, "a trial pays a supply bundle");
        assertEquals(Rank.D.contracts(), member.contractsDone(), "a trial isn't counted as a contract");
    }

    @Test
    void dyingDuringANoDeathTrialWipesItsProgress() {
        Member member = qualified();
        guild.startTrial(alice);
        guild.progress(alice, Objective.Kind.HUNT, Set.of("#HOSTILE"), 20);
        guild.onDeath(alice);
        assertEquals(0, member.trial().progress(0));
        assertNotNull(member.trial(), "the trial itself stands");
    }

    @Test
    void anUnfinishedTrialLapsesAndWaitsADay() {
        Member member = qualified();
        guild.startTrial(alice);
        clock.plusDays(Trials.DAYS);
        clock.plusHours(1);
        guild.member(alice);
        assertNull(member.trial());
        assertEquals(Guild.TrialStart.COOLDOWN, guild.startTrial(alice));
        clock.plusHours(Trials.RETRY_HOURS);
        assertEquals(Guild.TrialStart.STARTED, guild.startTrial(alice));
    }

    @Test
    void aBRankPromotionIsAnnouncedToEveryone() {
        Member member = join(alice);
        member.setRank(Rank.C);
        member.setReputation(Rank.B.reputation());
        member.setContractsDone(Rank.B.contracts());
        guild.startTrial(alice);
        assertEquals("trial_b", member.trial().defId());
        guild.progress(alice, Objective.Kind.HUNT, Set.of("BLAZE"), 8);
        guild.progress(alice, Objective.Kind.HUNT, Set.of("WITHER_SKELETON"), 3);
        guild.progress(alice, Objective.Kind.STRUCTURE, Set.of("incendium:sanctum"), 1);
        while (gm.nextComponentMessage() != null) {
            // drop earlier chat
        }

        assertEquals(Guild.ClaimResult.PROMOTED, guild.claim(alice, member.trial()).result());

        assertEquals(Rank.B, member.rank());
        boolean heard = false;
        for (var message = gm.nextComponentMessage(); message != null; message = gm.nextComponentMessage()) {
            heard |= PlainTextComponentSerializer.plainText().serialize(message).contains("Alice has passed the Promotion Trial");
        }
        assertTrue(heard, "another player hears about a B-Rank promotion");
    }

    @Test
    void nothingPromotesWithoutTheTrial() {
        Member member = join(alice);
        guild.addReputation(alice, member, 100_000);
        member.setContractsDone(1000);
        assertEquals(Rank.E, member.rank());
    }
}
