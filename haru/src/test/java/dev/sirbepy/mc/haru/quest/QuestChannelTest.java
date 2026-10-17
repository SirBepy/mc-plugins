package dev.sirbepy.mc.haru.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code haru:quest} plugin channel, with a fake "client" that records snapshots. */
class QuestChannelTest extends HaruTestBase {

    /** Players whose client registered haru:quest. */
    final Set<UUID> modded = new HashSet<>();
    final List<JsonObject> sent = new ArrayList<>();
    QuestChannel channel;

    private QuestChannel fakeChannel() {
        return new QuestChannel(p -> modded.contains(p.getUniqueId()),
                (p, bytes) -> sent.add(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()));
    }

    private void setBoard(OwnerState state, String... ids) {
        state.board().clear();
        for (String id : ids) {
            state.board().add(new ActiveQuest(id, 0, false));
        }
    }

    JsonObject last() {
        assertFalse(sent.isEmpty(), "nothing was sent to the mod");
        return sent.get(sent.size() - 1);
    }

    void request(String json) {
        channel.onPluginMessageReceived(QuestChannel.CHANNEL, owner, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void snapshotCarriesEveryActiveRequestAndTheToggle() {
        channel = fakeChannel();
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().get(0).setProgress(5);
        state.board().get(1).setProgress(100);
        ctx.board().claim(owner, state, 1, new ArrayList<>());

        channel.send(owner, state);
        JsonObject tree = last();
        assertEquals("quest", tree.get("type").getAsString());
        assertTrue(tree.get("enabled").getAsBoolean());
        JsonArray requests = tree.getAsJsonArray("requests");
        assertEquals(3, requests.size());
        JsonObject zombies = requests.get(0).getAsJsonObject();
        assertEquals("Defeat 12 Zombies", zombies.get("text").getAsString());
        assertEquals("EASY", zombies.get("difficulty").getAsString());
        assertEquals(5, zombies.get("progress").getAsInt());
        assertEquals(12, zombies.get("amount").getAsInt());
        assertFalse(zombies.get("ready").getAsBoolean());
        assertFalse(zombies.get("claimed").getAsBoolean());
        JsonObject creepers = requests.get(1).getAsJsonObject();
        assertTrue(creepers.get("claimed").getAsBoolean());
        assertFalse(creepers.get("ready").getAsBoolean(), "already claimed, not still ready");
    }

    @Test
    void readyIsTrueOnceProgressReachesTheGoalButNotYetClaimed() {
        channel = fakeChannel();
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().get(0).setProgress(12);

        channel.send(owner, state);
        assertTrue(last().getAsJsonArray("requests").get(0).getAsJsonObject().get("ready").getAsBoolean());
    }

    @Test
    void helloTracksTheClientVersionWithoutSendingAnythingBack() {
        channel = fakeChannel();
        request("{\"op\":\"hello\",\"v\":1}");
        assertTrue(sent.isEmpty());
    }

    @Test
    void toggleRunsTheWiredAction() {
        channel = fakeChannel();
        List<Object> toggled = new ArrayList<>();
        channel.setToggleAction(toggled::add);
        request("{\"op\":\"toggle\"}");
        assertEquals(List.of(owner), toggled);
    }

    @Test
    void malformedOrUnknownRequestsAreIgnored() {
        channel = fakeChannel();
        List<Object> toggled = new ArrayList<>();
        channel.setToggleAction(toggled::add);
        request("not json");
        request("[1,2]");
        request("{\"op\":\"teleport\"}");
        request("{}");
        channel.onPluginMessageReceived("other:channel", owner, "{\"op\":\"toggle\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(toggled.isEmpty());
        assertTrue(sent.isEmpty());
    }
}
