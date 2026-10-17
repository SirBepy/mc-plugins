package dev.sirbepy.mc.treemod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the server's /class hub snapshot (TreeChannel#hubSnapshot in the Classes plugin). */
class HubDataTest {

    static final String SNAPSHOT = """
            {"type":"hub","v":1,"open":true,"class":"Warrior","color":16733525,"level":19,"points":16,
             "entries":[
               {"id":"tree","label":"Skill tree","lines":["Spend your talent points, see every ability"]},
               {"id":"binds","label":"Binds","lines":["Which sneak + key fires each ability"]},
               {"id":"info","label":"Class info","lines":["Your passive, ability and weakness"]},
               {"id":"change","label":"Change class","lines":["Pick a different class"]}]}
            """;

    static HubData parse(String json) {
        return HubData.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsEveryFieldTheScreenDraws() {
        HubData hub = parse(SNAPSHOT);
        assertTrue(hub.open());
        assertEquals("Warrior", hub.className());
        assertEquals(19, hub.level());
        assertEquals(16, hub.points());
        assertEquals(4, hub.entries().size());
        assertEquals("tree", hub.entries().get(0).id());
        assertEquals("Skill tree", hub.entries().get(0).label());
        assertEquals(1, hub.entries().get(1).lines().size());
    }

    @Test
    void unknownMessagesAndVersionsAreIgnored() {
        assertNull(parse(SNAPSHOT.replace("\"v\":1", "\"v\":2")));
        assertNull(parse("{\"type\":\"tree\",\"v\":1}"));
    }
}
