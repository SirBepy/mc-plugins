package dev.sirbepy.mc.treemod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the server's spell bar snapshot (TreeChannel#hudSnapshot) and counting cooldowns down between them. */
class HudDataTest {

    static final String SNAPSHOT = """
            {"type":"hud","v":1,"show":true,"color":16733525,"resource":"Rage","value":64,"max":100,"armed":true,
             "combo":"R-L",
             "abilities":[
               {"slot":"one","name":"Battle Cry","combo":"R-L-R","texture":"item/classes/battle_cry","unlocked":true,"unlockLevel":1,"cd":0,"ready":true},
               {"slot":"two","name":"Charge","combo":"R-R-L","unlocked":true,"unlockLevel":10,"cd":12,"ready":false},
               {"slot":"ultimate","name":"Ground Slam","combo":"R-L-L","unlocked":false,"unlockLevel":25,"cd":0,"ready":false}]}
            """;

    static HudData parse(String json) {
        return HudData.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsEveryFieldTheOverlayDraws() {
        HudData hud = parse(SNAPSHOT);
        assertTrue(hud.show());
        assertEquals("Rage", hud.resource());
        assertEquals(64, hud.value());
        assertEquals(100, hud.max());
        assertEquals("R-L", hud.combo());
        assertEquals(3, hud.abilities().size());
        assertEquals("item/classes/battle_cry", hud.abilities().get(0).texture());
        assertNull(hud.abilities().get(1).texture());
        assertEquals(12, hud.abilities().get(1).cd());
        assertFalse(hud.abilities().get(2).unlocked());
    }

    @Test
    void theComboSoFarLightsUpTheCombosItCanStillBecome() {
        HudData hud = parse(SNAPSHOT);
        assertEquals(2, hud.matched(hud.abilities().get(0)), "R-L is the start of R-L-R");
        assertEquals(-1, hud.matched(hud.abilities().get(1)), "R-R-L went another way");
        assertEquals(2, hud.matched(hud.abilities().get(2)));
        HudData idle = parse(SNAPSHOT.replace("\"combo\":\"R-L\"", "\"combo\":\"\""));
        assertEquals(0, idle.matched(idle.abilities().get(0)));
    }

    @Test
    void aHiddenBarAndUnknownMessagesParseSafely() {
        HudData hidden = parse("{\"type\":\"hud\",\"v\":1,\"show\":false}");
        assertFalse(hidden.show());
        assertTrue(hidden.abilities().isEmpty());
        assertEquals(1, hidden.max(), "never divides by zero");
        assertNull(parse(SNAPSHOT.replace("\"v\":1", "\"v\":2")));
        assertNull(parse("{\"type\":\"tree\",\"v\":1}"));
    }

    @Test
    void cooldownsCountDownBetweenSnapshotsAndTheFirstValueIsTheFullLength() {
        HudState state = new HudState();
        state.accept(parse(SNAPSHOT), 1_000);
        HudData.Ability charge = state.data().abilities().get(1);
        assertEquals(12_000, state.remainingMillis(charge, 1_000));
        assertEquals(9_000, state.remainingMillis(charge, 4_000));
        assertEquals(0.75f, state.sweep(charge, 4_000), 0.001);
        // A second later the server says 11 s: the full length stays 12 s.
        state.accept(parse(SNAPSHOT.replace("\"cd\":12", "\"cd\":11")), 2_000);
        charge = state.data().abilities().get(1);
        assertEquals(0.5f, state.sweep(charge, 7_000), 0.001);
        assertEquals(0, state.remainingMillis(charge, 20_000));
        // Once it's over, the next cooldown measures its own length.
        state.accept(parse(SNAPSHOT.replace("\"cd\":12", "\"cd\":0")), 13_000);
        state.accept(parse(SNAPSHOT.replace("\"cd\":12", "\"cd\":6")), 14_000);
        assertEquals(1f, state.sweep(state.data().abilities().get(1), 14_000), 0.001);
        assertEquals(0, state.sweep(state.data().abilities().get(0), 14_000), "a ready slot has no sweep");
    }
}
