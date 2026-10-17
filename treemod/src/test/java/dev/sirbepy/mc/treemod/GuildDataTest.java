package dev.sirbepy.mc.treemod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the server's guild:ui snapshot (GuildChannel in the Guild plugin). */
class GuildDataTest {

    static final String SNAPSHOT = """
            {"type":"guild","v":1,"screen":"main","title":"Adventurer's Guild: E-Rank Adventurer",
             "subtitle":[],
             "rows":[
               {"id":"profile","label":"Alice","clickable":true,"lines":["E-Rank Adventurer","!Click for your titles"]},
               {"id":"info","label":"The Guild","clickable":true,"lines":["Members: 1"]},
               {"id":"activity","label":"Hunting Season (running)","clickable":false,"lines":["Your score: 3"]}]}
            """;

    static GuildData parse(String json) {
        return GuildData.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsEveryFieldTheScreenDraws() {
        GuildData data = parse(SNAPSHOT);
        assertEquals("main", data.screen());
        assertEquals("Adventurer's Guild: E-Rank Adventurer", data.title());
        assertTrue(data.subtitle().isEmpty());
        assertEquals(3, data.rows().size());
        assertEquals("profile", data.rows().get(0).id());
        assertEquals("Alice", data.rows().get(0).label());
        assertTrue(data.rows().get(0).clickable());
        assertEquals(2, data.rows().get(0).lines().size());
        assertFalse(data.rows().get(2).clickable(), "an info-only row (the running activity) isn't clickable");
    }

    @Test
    void unknownMessagesAndVersionsAreIgnored() {
        assertNull(parse(SNAPSHOT.replace("\"v\":1", "\"v\":2")));
        assertNull(parse("{\"type\":\"hub\",\"v\":1}"));
    }
}
