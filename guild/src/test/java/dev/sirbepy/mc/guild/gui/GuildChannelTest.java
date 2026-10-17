package dev.sirbepy.mc.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code guild:ui} plugin channel, with a fake "client" that records every snapshot sent. */
class GuildChannelTest extends GuildTestBase {

    private GuildMenus menus;
    private GuildChannel channel;
    /** Players whose client registered guild:ui. */
    private final Set<UUID> modded = new HashSet<>();
    private final List<JsonObject> sent = new ArrayList<>();

    @BeforeEach
    void setUpChannel() {
        menus = new GuildMenus(guild);
        channel = new GuildChannel(guild, menus, p -> modded.contains(p.getUniqueId()),
                (p, bytes) -> sent.add(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()));
        menus.setChannel(channel);
    }

    @AfterEach
    void tearDownChannel() {
        // GuildTestBase#tearDownGuild (MockBukkit.unmock) runs after this via its own @AfterEach.
    }

    private void modded(Player player) {
        modded.add(player.getUniqueId());
    }

    private JsonObject last() {
        assertFalse(sent.isEmpty(), "nothing was sent to the mod");
        return sent.get(sent.size() - 1);
    }

    private void request(Player p, String json) {
        channel.onPluginMessageReceived(GuildChannel.CHANNEL, p, json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject row(JsonObject snapshot, String id) {
        for (JsonElement e : snapshot.getAsJsonArray("rows")) {
            JsonObject r = e.getAsJsonObject();
            if (r.get("id").getAsString().equals(id)) return r;
        }
        throw new AssertionError("no row " + id + " in " + snapshot);
    }

    private static boolean hasRow(JsonObject snapshot, String id) {
        for (JsonElement e : snapshot.getAsJsonArray("rows")) {
            if (e.getAsJsonObject().get("id").getAsString().equals(id)) return true;
        }
        return false;
    }

    private void click(Player p, String screen, String row) {
        click(p, screen, row, "left");
    }

    private void click(Player p, String screen, String row, String button) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "click");
        o.addProperty("screen", screen);
        o.addProperty("row", row);
        o.addProperty("button", button);
        channel.onPluginMessageReceived(GuildChannel.CHANNEL, p, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void withTheModOpenSendsTheBoardInsteadOfTheChest() {
        join(alice);
        modded(alice);
        menus.open(alice, GuildMenus.Screen.MAIN);
        JsonObject board = last();
        assertEquals("guild", board.get("type").getAsString());
        assertEquals("main", board.get("screen").getAsString());
        assertTrue(hasRow(board, "profile"));
        assertEquals(InventoryType.CRAFTING, alice.getOpenInventory().getType(), "no chest menu opened");
    }

    @Test
    void withoutTheModTheChestOpensAndNothingIsSent() {
        join(alice);
        menus.open(alice, GuildMenus.Screen.MAIN);
        assertTrue(sent.isEmpty());
        assertEquals(InventoryType.CHEST, alice.getOpenInventory().getType(), "the chest menu opened");
    }

    @Test
    void aStrangerGetsTheRegistrationScreenAndCanJoinFromIt() {
        modded(alice);
        bank.set(alice, 600);
        menus.desk().visit(alice, alice.getLocation());
        menus.open(alice, GuildMenus.Screen.MAIN);
        assertEquals("registration", last().get("screen").getAsString());

        click(alice, "registration", "register");

        assertNotNull(guild.member(alice));
        assertEquals(100, bank.balance(alice));
        assertEquals("main", last().get("screen").getAsString());
    }

    @Test
    void awayFromTheHallAStrangerCannotRegister() {
        modded(alice);
        bank.set(alice, 600);
        click(alice, "registration", "register");
        assertNull(store.member(alice.getUniqueId()));
        assertTrue(lastLine(alice).contains("Guild Hall"));
    }

    @Test
    void atTheDeskADailyClaimPaysAndARerollDraws() {
        Member member = join(alice);
        modded(alice);
        ActiveContract daily = placeDaily(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        menus.desk().visit(alice, alice.getLocation());

        click(alice, "main", "daily1");
        assertTrue(daily.claimed());
        assertTrue(bank.balance(alice) > 0);

        click(alice, "main", "daily0", "right");
        assertEquals(1, member.rerollsUsed());
    }

    @Test
    void awayFromTheHallClaimsAndRerollsDoNothing() {
        Member member = join(alice);
        modded(alice);
        ActiveContract daily = placeDaily(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);

        click(alice, "main", "daily1");

        assertFalse(daily.claimed());
        assertEquals(0, bank.balance(alice));
        assertTrue(lastLine(alice).contains("Guild Hall"));
    }

    @Test
    void infoNavigatesToTheHallOfFameAndBackReturnsToTheBoard() {
        join(alice);
        modded(alice);
        click(alice, "main", "info");
        assertEquals("hallOfFame", last().get("screen").getAsString());
        assertTrue(hasRow(last(), "firstDragon"));

        click(alice, "hallOfFame", "back");
        assertEquals("main", last().get("screen").getAsString());
    }

    @Test
    void profileNavigatesToTitlesAndWearingATitleUpdatesTheMember() {
        Member member = join(alice);
        modded(alice);
        guild.achievements().grant(alice, member, "founder");
        click(alice, "main", "profile");
        assertEquals("titles", last().get("screen").getAsString());
        assertTrue(hasRow(last(), "title:founder"));

        click(alice, "titles", "title:founder");
        assertEquals("founder", member.activeTitle());

        click(alice, "titles", "showToggle");
        assertTrue(member.showTitle());
    }

    @Test
    void onlyTheGuildmasterReachesTheOfficeAndCanWithdraw() {
        join(alice);
        Member gmMember = join(gm);
        modded(alice);
        modded(gm);

        click(alice, "main", "office");
        assertTrue(sent.isEmpty(), "a member clicking the office row (never shown to one) gets nothing");

        click(gm, "main", "office");
        assertEquals("office", last().get("screen").getAsString());

        click(gm, "office", "withdraw100");
        assertEquals(100, bank.balance(gm));
        assertEquals(900, store.treasury().balance());
        assertEquals(gmMember, guild.member(gm));
    }

    @Test
    void commissionsScreenLetsAnEligibleMemberClaimAndTheGuildmasterManage() {
        join(alice);
        join(gm);
        store.treasury().record(clock.nowMillis(), LedgerEntry.Kind.ADJUST, 1000, "test");
        guild.office().postCommission(gm, new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie"), 50, Rank.E);
        Commission commission = store.commissions().getFirst();
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        modded(alice);
        modded(gm);

        click(alice, "commissions", "commission:" + commission.id());
        assertEquals(Commission.Status.OPEN, commission.status(), "not at the desk yet");

        menus.desk().visit(alice, alice.getLocation());
        click(alice, "commissions", "commission:" + commission.id());
        assertEquals(Commission.Status.CLAIMED, commission.status());
    }

    @Test
    void malformedOrUnknownRequestsAreIgnored() {
        join(alice);
        modded(alice);
        request(alice, "not json");
        request(alice, "[1,2]");
        request(alice, "{\"op\":\"click\"}");
        request(alice, "{\"op\":\"click\",\"screen\":\"nowhere\",\"row\":\"x\"}");
        request(alice, "{\"op\":\"teleport\"}");
        channel.onPluginMessageReceived("other:channel", alice, "{\"op\":\"hello\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(sent.isEmpty());
    }

    @Test
    void helloIsRecordedWithoutError() {
        request(alice, "{\"op\":\"hello\",\"v\":1}");
        assertTrue(sent.isEmpty(), "hello never answers with a snapshot");
    }
}
