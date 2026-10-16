package dev.sirbepy.mc.classes.talent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.classes.Abilities;
import dev.sirbepy.mc.classes.ClassStore;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.Levels;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.kits.Kits;
import dev.sirbepy.mc.core.bedrock.BedrockForms;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tree mod's plugin channel, with the real Warrior kit and a fake "client" that records snapshots. */
class TreeChannelTest {

    @TempDir
    File dir;
    ServerMock server;
    PluginMock plugin;
    Classes classes;
    TalentEngine engine;
    TreeChannel channel;
    /** Players whose client registered classes:tree. */
    final Set<UUID> modded = new HashSet<>();
    final List<JsonObject> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        long now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli();
        classes = new Classes(new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test")), () -> now,
                ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        Abilities abilities = new Abilities(classes, (task, ticks) -> server.getScheduler().runTaskLater(plugin, task, ticks));
        engine = new TalentEngine(plugin, classes, abilities, () -> 0.5, Map.of(), false, false, BedrockForms.create(plugin),
                Kits::create);
        engine.start();
        channel = engine.fakeTreeChannel(p -> modded.contains(p.getUniqueId()),
                (p, bytes) -> sent.add(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    PlayerMock warrior(int level, boolean withMod) {
        PlayerMock p = server.addPlayer();
        classes.choose(p.getUniqueId(), RpgClass.WARRIOR);
        classes.setXp(p.getUniqueId(), RpgClass.WARRIOR, Levels.totalFor(level));
        if (withMod) modded.add(p.getUniqueId());
        return p;
    }

    JsonObject last() {
        assertFalse(sent.isEmpty(), "nothing was sent to the mod");
        return sent.get(sent.size() - 1);
    }

    static JsonObject node(JsonObject tree, String id) {
        for (JsonElement e : tree.getAsJsonArray("nodes")) {
            if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        }
        throw new AssertionError("no node " + id);
    }

    void request(Player p, String json) {
        channel.onPluginMessageReceived(TreeChannel.CHANNEL, p, json.getBytes(StandardCharsets.UTF_8));
    }

    int rank(Player p, String id) {
        return engine.talents().rank(p.getUniqueId(), RpgClass.WARRIOR, id);
    }

    @Test
    void withTheModClassTreeSendsTheTreeInsteadOfOpeningTheChest() {
        PlayerMock p = warrior(19, true);
        engine.treeMenu().open(p);
        JsonObject tree = last();
        assertEquals("tree", tree.get("type").getAsString());
        assertTrue(tree.get("open").getAsBoolean());
        assertEquals("Warrior", tree.get("class").getAsString());
        assertEquals(18, tree.get("free").getAsInt());
        assertEquals(33, tree.getAsJsonArray("nodes").size());
        JsonArray branches = tree.getAsJsonArray("branches");
        assertEquals("Berserker", branches.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("Battle Cry", tree.getAsJsonArray("abilities").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("item/goat_horn", tree.getAsJsonArray("abilities").get(0).getAsJsonObject().get("texture").getAsString());
        assertEquals("minecraft:redstone", node(tree, "bloodlust").get("icon").getAsString());
        assertEquals(InventoryType.CRAFTING, p.getOpenInventory().getType(), "no chest menu opened");
    }

    @Test
    void withoutTheModTheChestOpensAndNothingIsSent() {
        PlayerMock p = warrior(19, false);
        engine.treeMenu().open(p);
        assertTrue(sent.isEmpty());
        assertEquals(InventoryType.CHEST, p.getOpenInventory().getType(), "the chest menu opened");
    }

    @Test
    void learnAndRefundGoThroughTalentsAndSendAFreshTree() {
        PlayerMock p = warrior(19, true);
        request(p, "{\"op\":\"learn\",\"id\":\"bloodlust\"}");
        assertEquals(1, rank(p, "bloodlust"));
        JsonObject tree = last();
        assertFalse(tree.get("open").getAsBoolean(), "an update, not a new screen");
        assertEquals(1, node(tree, "bloodlust").get("rank").getAsInt());
        assertEquals(17, tree.get("free").getAsInt());
        request(p, "{\"op\":\"refund\",\"id\":\"bloodlust\"}");
        assertEquals(0, rank(p, "bloodlust"));
    }

    @Test
    void theServerRefusesWhatTheChestWouldRefuse() {
        PlayerMock p = warrior(19, true);
        // Tier 2 needs 3 points in the branch's lower tiers first.
        request(p, "{\"op\":\"learn\",\"id\":\"frenzy\"}");
        assertEquals(0, rank(p, "frenzy"));
        JsonObject frenzy = node(last(), "frenzy");
        assertEquals("TIER_LOCKED", frenzy.get("state").getAsString());
        assertTrue(frenzy.get("locked").getAsBoolean());
        assertTrue(frenzy.get("lock").getAsString().contains("3 more points"));
        // Keystones need level 20 and 14 points in the branch.
        request(p, "{\"op\":\"learn\",\"id\":\"undying_fury\"}");
        assertEquals(0, rank(p, "undying_fury"));
        // Refunding a node with no ranks does nothing.
        request(p, "{\"op\":\"refund\",\"id\":\"bloodlust\"}");
        assertEquals(0, rank(p, "bloodlust"));
    }

    @Test
    void aClientCanOnlySpendInItsActiveClass() {
        PlayerMock p = warrior(19, true);
        classes.leave(p.getUniqueId());
        request(p, "{\"op\":\"learn\",\"id\":\"bloodlust\"}");
        assertEquals(0, rank(p, "bloodlust"));
        // Viewing another class's tree is read-only on the screen too.
        engine.treeMenu().open(p, RpgClass.WARRIOR);
        assertFalse(last().get("own").getAsBoolean());
    }

    @Test
    void respecRefundsEverything() {
        PlayerMock p = warrior(19, true);
        request(p, "{\"op\":\"learn\",\"id\":\"bloodlust\"}");
        request(p, "{\"op\":\"learn\",\"id\":\"thick_blood\"}");
        request(p, "{\"op\":\"respec\"}");
        assertEquals(0, engine.talents().spent(p.getUniqueId(), RpgClass.WARRIOR));
        assertEquals(18, last().get("free").getAsInt());
    }

    @Test
    void theOpenKeyOpensTheActiveClassesTree() {
        PlayerMock p = warrior(5, true);
        request(p, "{\"op\":\"open\"}");
        assertTrue(last().get("open").getAsBoolean());
        assertTrue(last().get("own").getAsBoolean());
    }

    @Test
    void malformedOrUnknownRequestsAreIgnored() {
        PlayerMock p = warrior(19, true);
        request(p, "not json");
        request(p, "[1,2]");
        request(p, "{\"op\":\"learn\"}");
        request(p, "{\"op\":\"learn\",\"id\":\"no_such_node\"}");
        request(p, "{\"op\":\"learn\",\"id\":{\"x\":1}}");
        request(p, "{\"op\":\"teleport\"}");
        request(p, "{\"op\":\"cast\",\"slot\":\"9\"}");
        channel.onPluginMessageReceived("other:channel", p, "{\"op\":\"respec\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, engine.talents().spent(p.getUniqueId(), RpgClass.WARRIOR));
        assertTrue(sent.isEmpty());
    }
}
