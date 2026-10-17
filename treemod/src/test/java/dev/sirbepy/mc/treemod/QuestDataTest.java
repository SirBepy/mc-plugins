package dev.sirbepy.mc.treemod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the server's quest brief snapshot (QuestChannel in the Haru plugin). */
class QuestDataTest {

    // Trimmed from a real QuestChannel snapshot: one unfinished request, one claimed.
    static final String SNAPSHOT = """
            {"type":"quest","v":1,"enabled":true,
             "requests":[
               {"text":"Defeat 12 Zombies","difficulty":"EASY","progress":5,"amount":12,"claimed":false,"ready":false},
               {"text":"Defeat 6 Creepers","difficulty":"MEDIUM","progress":6,"amount":6,"claimed":true,"ready":false}
             ]}
            """;

    static QuestData parse(String json) {
        return QuestData.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsEveryFieldTheOverlayDraws() {
        QuestData data = parse(SNAPSHOT);
        assertTrue(data.enabled());
        assertEquals(2, data.requests().size());
        QuestData.Request zombies = data.requests().get(0);
        assertEquals("Defeat 12 Zombies", zombies.text());
        assertEquals("EASY", zombies.difficulty());
        assertEquals(5, zombies.progress());
        assertEquals(12, zombies.amount());
        assertFalse(zombies.claimed());
        assertFalse(zombies.ready());
        assertTrue(data.requests().get(1).claimed());
    }

    @Test
    void theDisabledFlagSurvivesWithNoRequests() {
        QuestData data = parse("{\"type\":\"quest\",\"v\":1,\"enabled\":false,\"requests\":[]}");
        assertFalse(data.enabled());
        assertTrue(data.requests().isEmpty());
    }

    @Test
    void unknownMessagesAndVersionsAreIgnored() {
        assertNull(parse(SNAPSHOT.replace("\"v\":1", "\"v\":2")));
        assertNull(parse("{\"type\":\"tree\",\"v\":1}"));
    }
}
