package dev.sirbepy.mc.guild.fame;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.listener.TitleListener;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleTagsTest extends GuildTestBase {

    /** What Names would render beside each name: player -> tag. */
    private final Map<UUID, Component> shown = new HashMap<>();

    @BeforeEach
    void attachSink() {
        guild.titleTags().attach(new TitleTags.Sink() {
            @Override
            public void show(UUID player, Component tag) {
                shown.put(player, tag);
            }

            @Override
            public void hide(UUID player) {
                shown.remove(player);
            }
        });
    }

    private String tagOf(UUID player) {
        Component tag = shown.get(player);
        return tag == null ? null : PlainTextComponentSerializer.plainText().serialize(tag);
    }

    @Test
    void nothingShowsUntilTheMemberOptsIn() {
        Member member = join(alice);

        assertFalse(member.showTitle());
        assertNull(tagOf(alice.getUniqueId()));
        assertNull(TitleTags.tag(member));
    }

    @Test
    void showingTheRankTitleTagsTheShortRank() {
        Member member = join(alice);

        guild.showTitle(member, true);

        assertEquals("[E-Rank] ", tagOf(alice.getUniqueId()));
        assertEquals(Rank.E.color(), shown.get(alice.getUniqueId()).color());
    }

    @Test
    void wearingAnotherTitleChangesTheTagAndHidingRemovesIt() {
        Member member = join(alice);
        guild.achievements().grant(null, member, "night-owl");
        guild.showTitle(member, true);
        assertEquals("[Night Owl] ", tagOf(alice.getUniqueId()));

        guild.wearTitle(member, "rank-E");
        assertEquals("[E-Rank] ", tagOf(alice.getUniqueId()));

        guild.showTitle(member, false);
        assertNull(tagOf(alice.getUniqueId()));
    }

    @Test
    void aNewlyEarnedTitleThatIsAutoWornUpdatesTheTag() {
        Member member = join(alice);
        guild.showTitle(member, true);

        guild.achievements().grant(alice, member, "angler");

        assertEquals("[Master Angler] ", tagOf(alice.getUniqueId()));
    }

    @Test
    void anUnearnedTitleNeverShows() {
        Member member = join(alice);
        member.setShowTitle(true);
        member.setActiveTitle("heir");

        assertNull(TitleTags.tag(member));
    }

    @Test
    void theChoiceSurvivesARestart() {
        Member member = join(alice);
        guild.showTitle(member, true);

        Member back = newStore().member(alice.getUniqueId());

        assertTrue(back.showTitle());
        assertEquals("[E-Rank] ", PlainTextComponentSerializer.plainText().serialize(TitleTags.tag(back)));
    }

    @Test
    void theGuildmasterCanWearGuildmaster() {
        Member founder = join(gm);

        assertTrue(founder.titles().contains("guildmaster"));
        guild.wearTitle(founder, "guildmaster");
        guild.showTitle(founder, true);
        assertEquals("[Guildmaster] ", tagOf(gm.getUniqueId()));
    }

    @Test
    void joiningReappliesTheTagAndBackfillsTheGuildmasterTitle() {
        Member founder = join(gm);
        founder.titles().remove("guildmaster");
        founder.setShowTitle(true);
        shown.clear();

        new TitleListener(guild).onJoin(new PlayerJoinEvent(gm, Component.empty()));

        assertTrue(founder.titles().contains("guildmaster"));
        assertEquals("[Founder of the Guild] ", tagOf(gm.getUniqueId()));
    }

    @Test
    void nonMembersGetNothingOnJoin() {
        new TitleListener(guild).onJoin(new PlayerJoinEvent(alice, Component.empty()));

        assertTrue(shown.isEmpty());
    }
}
