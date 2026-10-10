package dev.sirbepy.mc.guild.fame;

import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.UUID;

/**
 * The worn title as a tag beside a member's name in chat and the tab list (ImMijo's ask, Joe's OK,
 * 2026-10-10). Opt-in per member ({@link Member#showTitle()}); only the title the member wears ever
 * shows, so nobody's unearned or unworn titles are revealed. Rendering belongs to the Names plugin,
 * reached through a {@link Sink} so the Guild still runs without it.
 */
public final class TitleTags {

    /** Where tags go: the Names plugin's prefix API on a server, nothing in tests unless one is set. */
    public interface Sink {
        void show(UUID player, Component tag);

        void hide(UUID player);
    }

    private static final Sink NONE = new Sink() {
        @Override
        public void show(UUID player, Component tag) {
        }

        @Override
        public void hide(UUID player) {
        }
    };

    private Sink sink = NONE;

    public void attach(Sink sink) {
        this.sink = sink == null ? NONE : sink;
    }

    /** Shows or hides the member's tag to match what they wear and whether they chose to show it. */
    public void update(Member member) {
        Component tag = tag(member);
        if (tag == null) {
            sink.hide(member.id());
        } else {
            sink.show(member.id(), tag);
        }
    }

    public void updateAll(Collection<Member> members) {
        members.forEach(this::update);
    }

    /** The tag for a member, or null when nothing should show. Rank titles shorten to "S-Rank". */
    public static Component tag(Member member) {
        if (!member.showTitle()) {
            return null;
        }
        String id = member.activeTitle();
        Titles.Title title = Titles.get(id);
        if (title == null || !member.titles().contains(id)) {
            return null;
        }
        if (id.startsWith("rank-")) {
            Rank rank = Rank.parse(id.substring("rank-".length()), null);
            if (rank != null) {
                return Component.text("[" + rank.name() + "-Rank] ", rank.color());
            }
        }
        return Component.text("[" + title.name() + "] ", NamedTextColor.GOLD);
    }
}
