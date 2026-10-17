package dev.sirbepy.mc.treemod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the server's snapshot (TreeChannel in the Classes plugin) and the layout it drives. */
class TreeDataTest {

    // Trimmed from a real TreeChannel snapshot: one branch with tier 1 (gate 0) and tier 2 (gate 3).
    static final String SNAPSHOT = """
            {"type":"tree","v":1,"open":true,"class":"Warrior","color":16733525,"own":true,"level":19,
             "points":18,"free":16,"resource":"Rage","resourceValue":40,
             "branches":[{"name":"Berserker","spent":2},{"name":"Weaponmaster","spent":0},{"name":"Warlord","spent":0}],
             "abilities":[{"slot":"one","label":"Ability 1","name":"Battle Cry","key":"Sneak + 1","keyShort":"1",
               "unlockLevel":1,"unlocked":true,"texture":"item/goat_horn","lines":["Battle Cry: ..."]}],
             "nodes":[
               {"id":"bloodlust","name":"Bloodlust","branch":0,"tier":1,"gate":0,"rank":2,"max":3,"keystone":false,
                "icon":"minecraft:redstone","state":"OK","locked":false,"lines":["Rank 2/3 · Tier 1"]},
               {"id":"frenzy","name":"Frenzy","branch":0,"tier":2,"gate":3,"rank":0,"max":3,"keystone":false,
                "icon":"minecraft:iron_sword","state":"TIER_LOCKED","locked":true,"lines":["Rank 0/3 · Tier 2"],
                "lock":"Locked: spend 1 more points in the lower tiers of this branch"}]}
            """;

    static TreeData parse(String json) {
        return TreeData.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsEveryFieldTheScreenDraws() {
        TreeData tree = parse(SNAPSHOT);
        assertEquals("Warrior", tree.className());
        assertTrue(tree.open());
        assertEquals(16, tree.free());
        assertEquals("Berserker", tree.branches().get(0).name());
        assertEquals("1", tree.abilities().get(0).keyShort());
        assertEquals("item/goat_horn", tree.abilities().get(0).texture());
        TreeData.Node frenzy = tree.node("frenzy");
        assertTrue(frenzy.locked());
        assertEquals("TIER_LOCKED", frenzy.state());
        assertTrue(frenzy.lock().contains("1 more"));
        assertNull(tree.node("bloodlust").lock());
    }

    @Test
    void keystoneLevelComesFromTheSnapshotWithAFallbackForAnOlderServer() {
        // SNAPSHOT predates the "keystoneLevel" field: a new mod on an old server still works.
        assertEquals(20, parse(SNAPSHOT).keystoneLevel());
        TreeData withField = parse(SNAPSHOT.replace("\"free\":16,", "\"free\":16,\"keystoneLevel\":25,"));
        assertEquals(25, withField.keystoneLevel());
    }

    @Test
    void topTierIsTheHighestTierAmongTheNodesNotAHardcodedConstant() {
        // SNAPSHOT only has tiers 1 and 2, so the keystone row sits at tier 2 here, not tier 5.
        assertEquals(2, parse(SNAPSHOT).topTier());
    }

    @Test
    void aTierOpensWhenTheBranchHasItsGateInLowerTiers() {
        TreeData tree = parse(SNAPSHOT);
        assertEquals(2, tree.spentBelow(0, 2));
        assertTrue(tree.tierOpen(0, 1));
        assertFalse(tree.tierOpen(0, 2));
        TreeData more = parse(SNAPSHOT.replace("\"rank\":2", "\"rank\":3"));
        assertTrue(more.tierOpen(0, 2));
    }

    @Test
    void unknownMessagesAndVersionsAreIgnored() {
        assertNull(parse(SNAPSHOT.replace("\"v\":1", "\"v\":2")));
        assertNull(parse("{\"type\":\"other\",\"v\":1}"));
    }

    @Test
    void tiersStackBottomToTopAndNodesCentreOnTheirColumn() {
        // topTier is a plain parameter now, no longer the TreeData.KEYSTONE_TIER constant; 5 is the
        // usual full-height tree, same shape this test checked before.
        assertTrue(TreeScreen.tierY(1, 5) > TreeScreen.tierY(2, 5), "tier 1 sits lowest");
        assertEquals(TreeScreen.TOP_TIER_Y, TreeScreen.tierY(5, 5));
        int cx = TreeScreen.columnCenter(1);
        assertEquals(cx, TreeScreen.nodeX(1, 0, 1) + TreeScreen.FRAME / 2, "a lone keystone is centred");
        assertEquals(cx, TreeScreen.nodeX(1, 1, 3) + TreeScreen.FRAME / 2, "the middle of three is centred");
        // The bottom tier's pips stay inside the panel.
        assertTrue(TreeScreen.tierY(1, 5) + TreeScreen.FRAME + 5 < TreeScreen.H - 4);
    }
}
