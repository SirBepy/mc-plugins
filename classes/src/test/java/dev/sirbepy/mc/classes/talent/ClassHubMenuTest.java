package dev.sirbepy.mc.classes.talent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.classes.Abilities;
import dev.sirbepy.mc.classes.ClassHubMenu;
import dev.sirbepy.mc.classes.ClassMenu;
import dev.sirbepy.mc.classes.ClassPassives;
import dev.sirbepy.mc.classes.ClassPicker;
import dev.sirbepy.mc.classes.ClassStore;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.Levels;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.kits.Kits;
import dev.sirbepy.mc.core.bedrock.BedrockForms;
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

/** {@code /class}'s hub: the short menu a player with a class gets instead of the full seven-class list. */
class ClassHubMenuTest {

    @TempDir
    File dir;
    ServerMock server;
    PluginMock plugin;
    Classes classes;
    TalentEngine engine;
    TreeChannel channel;
    ClassMenu classMenu;
    ClassHubMenu hub;
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
        engine = new TalentEngine(plugin, classes, abilities, () -> 0.5, false, false, BedrockForms.create(plugin),
                Kits::create);
        engine.start();
        channel = engine.fakeTreeChannel(p -> modded.contains(p.getUniqueId()),
                (p, bytes) -> sent.add(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()));
        ClassPassives passives = new ClassPassives(classes, () -> 0.5);
        ClassPicker picker = new ClassPicker(classes, passives, () -> now);
        classMenu = new ClassMenu(classes, picker, engine.slots(), engine.treeMenu(), () -> now);
        hub = new ClassHubMenu(classes, classMenu, engine);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    PlayerMock warrior(boolean withMod, boolean hubCapable) {
        PlayerMock p = server.addPlayer();
        classes.choose(p.getUniqueId(), RpgClass.WARRIOR);
        classes.setXp(p.getUniqueId(), RpgClass.WARRIOR, Levels.totalFor(19));
        if (withMod) modded.add(p.getUniqueId());
        if (hubCapable) channel.handle(p, JsonParser.parseString("{\"op\":\"hello\",\"v\":2}").getAsJsonObject());
        return p;
    }

    @Test
    void withNoClassTheFullListOpens() {
        PlayerMock p = server.addPlayer();
        hub.open(p);
        assertEquals(InventoryType.CHEST, p.getOpenInventory().getType());
        // The full 7-class list is 3 rows (ClassMenu); the trimmed hub is 1, so size is the tell.
        assertEquals(27, p.getOpenInventory().getTopInventory().getSize());
    }

    @Test
    void withAClassButNoModTheChestHubOpens() {
        PlayerMock p = warrior(false, false);
        hub.open(p);
        assertEquals(InventoryType.CHEST, p.getOpenInventory().getType());
        assertTrue(sent.isEmpty());
        assertEquals(9, p.getOpenInventory().getTopInventory().getSize(), "the trimmed hub is one row");
    }

    @Test
    void anOldModClientThatNeverSaidHelloGetsTheChestToo() {
        PlayerMock p = warrior(true, false);
        hub.open(p);
        assertTrue(sent.isEmpty(), "a mod that never advertised v2 can't be sent the hub type");
        assertEquals(InventoryType.CHEST, p.getOpenInventory().getType());
    }

    @Test
    void aHubCapableModClientGetsTheHubSnapshotInstead() {
        PlayerMock p = warrior(true, true);
        hub.open(p);
        assertFalse(sent.isEmpty());
        JsonObject out = sent.get(sent.size() - 1);
        assertEquals("hub", out.get("type").getAsString());
        assertEquals("Warrior", out.get("class").getAsString());
        assertEquals(4, out.getAsJsonArray("entries").size());
    }

    @Test
    void changeClassStillReachesTheExistingList() {
        PlayerMock p = warrior(false, false);
        hub.open(p);
        dev.sirbepy.mc.core.gui.Menu shown = (dev.sirbepy.mc.core.gui.Menu) p.getOpenInventory().getTopInventory().getHolder();
        shown.click(ClassHubMenu.CHANGE_SLOT, org.bukkit.event.inventory.ClickType.LEFT);
        assertEquals(InventoryType.CHEST, p.getOpenInventory().getType());
        assertEquals(27, p.getOpenInventory().getTopInventory().getSize(), "the full 7-class list opened");
    }
}
