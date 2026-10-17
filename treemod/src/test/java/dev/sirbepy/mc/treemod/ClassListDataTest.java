package dev.sirbepy.mc.treemod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the server's /class browse snapshot (TreeChannel#classListSnapshot in the Classes plugin). */
class ClassListDataTest {

    static final String SNAPSHOT = """
            {"type":"classlist","v":1,"open":true,
             "entries":[
               {"id":"warrior","name":"Warrior","color":16733525,"own":true,"locked":false,
                "lines":["Weapon Master: +4.3% melee damage","Battle Cry: take 12.9% less damage for 7 s (every 73 s)","Heavy Hands: arrows and tridents deal 10% less damage"]},
               {"id":"ranger","name":"Ranger","color":5635925,"own":false,"locked":true,
                "lines":["Steady Aim: +2.0% arrow and trident damage","Fleet Foot: Speed I for 8 s (every 90 s)","Thin Skin: take 5% more damage from mobs' melee hits",
                         "You can change class in 6d 23h."]}]}
            """;

    static ClassListData parse(String json) {
        return ClassListData.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsEveryFieldTheScreenDraws() {
        ClassListData list = parse(SNAPSHOT);
        assertTrue(list.open());
        assertEquals(2, list.entries().size());
        ClassListData.Entry warrior = list.entries().get(0);
        assertEquals("warrior", warrior.id());
        assertEquals("Warrior", warrior.name());
        assertEquals(16733525, warrior.color());
        assertTrue(warrior.own());
        assertFalse(warrior.locked());
        assertEquals(3, warrior.lines().size());
        ClassListData.Entry ranger = list.entries().get(1);
        assertFalse(ranger.own());
        assertTrue(ranger.locked());
        assertEquals(4, ranger.lines().size(), "a locked entry's lock duration tags along as an extra line");
    }

    @Test
    void unknownMessagesAndVersionsAreIgnored() {
        assertNull(parse(SNAPSHOT.replace("\"v\":1", "\"v\":2")));
        assertNull(parse("{\"type\":\"hub\",\"v\":1}"));
    }
}
