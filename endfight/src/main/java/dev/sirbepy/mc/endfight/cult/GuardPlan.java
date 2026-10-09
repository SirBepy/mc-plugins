package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.GuardKind;
import dev.sirbepy.mc.endfight.arena.GuardPost;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure: which entity type(s) go on each {@link GuardPost}, given the configured composition per
 * {@link GuardKind}. No world access, no randomness.
 *
 * <p>A {@link GuardPost} alone doesn't say which physical outpost or gatehouse it belongs to (that
 * identity lives only in {@code arena}'s placement code, out of this phase's reach) - so "one
 * vindicator per outpost" / "one evoker on each gatehouse" group posts back together from their
 * own x/y/z:
 * <ul>
 *   <li>STAIR_OUTPOST/LADDER_OUTPOST/BALCONY: every post at one physical outpost sits at the exact
 *   same Y (one {@code outpostRing} call, one Y) - grouping by (kind, y) recovers "one outpost".</li>
 *   <li>GATEHOUSE: one gatehouse's 4 posts split across two Y levels (ground + tower roof), so Y
 *   can't be the key; grouping by which cardinal direction the post sits in from the portal (the
 *   same N/E/S/W a gatehouse is built on) recovers "one gatehouse" instead.</li>
 * </ul>
 * The first post (in listing order) of each group is where the bonus mob stands.
 */
public final class GuardPlan {

    /** {@code bonus} is null when a kind gets no extra mob (LADDER_OUTPOST, BALCONY, WALL by
     * default). */
    public record Composition(String base, String bonus) {

        public Composition(String base) {
            this(base, null);
        }
    }

    public record PlannedGuard(GuardPost post, List<String> types) {
    }

    private GuardPlan() {
    }

    /** Always returns exactly one {@link PlannedGuard} per input post, in the same order - a kind
     * with no configured composition just gets an empty type list - so a caller may zip the result
     * back up against {@code posts} by position (e.g. to recover each post's index for
     * {@link CultMarker#markGuard}). */
    public static List<PlannedGuard> plan(List<GuardPost> posts, Map<GuardKind, Composition> compositions) {
        Set<GuardPost> bonusPosts = firstOfEachGroup(posts);
        List<PlannedGuard> out = new ArrayList<>(posts.size());
        for (GuardPost post : posts) {
            Composition composition = compositions.get(post.kind());
            if (composition == null) {
                out.add(new PlannedGuard(post, List.of()));
                continue;
            }
            List<String> types = new ArrayList<>(2);
            types.add(composition.base());
            if (composition.bonus() != null && bonusPosts.contains(post)) {
                types.add(composition.bonus());
            }
            out.add(new PlannedGuard(post, types));
        }
        return out;
    }

    private static Set<GuardPost> firstOfEachGroup(List<GuardPost> posts) {
        Map<String, GuardPost> firstPerGroup = new LinkedHashMap<>();
        for (GuardPost post : posts) {
            firstPerGroup.putIfAbsent(groupKey(post), post);
        }
        return new HashSet<>(firstPerGroup.values());
    }

    private static String groupKey(GuardPost post) {
        if (post.kind() == GuardKind.GATEHOUSE) {
            return "GATEHOUSE:" + cardinal(post.x(), post.z());
        }
        return post.kind() + ":" + post.y();
    }

    /** Same "dominant axis" rule {@code Geometry.cardinalFacing} uses: +x=east, -x=west,
     * +z=south, -z=north, measured from the portal at 0,0 which every gatehouse post sits well
     * away from in exactly one of those 4 directions. */
    static String cardinal(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? "east" : "west";
        }
        return z > 0 ? "south" : "north";
    }
}
