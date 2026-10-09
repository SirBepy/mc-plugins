package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /cosmetic}: request/list/done/refund (Joe, 2026-10-09). {@link CosmeticCommand} is
 * registration only, so every behaviour here is driven straight through {@link CosmeticRequests}.
 */
class CosmeticRequestsTest {

    @TempDir
    File dir;

    private ServerMock server;
    private PluginMock plugin;
    private MarketConfig config;
    private CosmeticToken token;
    private final List<String> logMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        token = new CosmeticToken(plugin);

        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logMessages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().setUseParentHandlers(false);
        plugin.getLogger().addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CosmeticRequests requests(File stateFile) {
        return new CosmeticRequests(plugin, () -> config, token, stateFile, plugin.getLogger());
    }

    private CosmeticRequests requests() {
        return requests(new File(dir, "cosmetics.yml"));
    }

    // An explicit slot away from slot 0 (the default held slot): addItem() would otherwise fill the
    // main hand first on a bare inventory, fighting with setItemInMainHand() calls in these tests.
    private static void giveToken(PlayerMock player, CosmeticToken token) {
        giveToken(player, token, 9);
    }

    private static void giveToken(PlayerMock player, CosmeticToken token, int slot) {
        player.getInventory().setItem(slot, token.create());
    }

    @Test
    void emptyMainHandIsRefused() {
        PlayerMock player = server.addPlayer();
        giveToken(player, token);
        requests().request(player, "make it glow");
        assertTrue(player.nextMessage().contains("Hold the item"));
    }

    @Test
    void holdingTheTokenItselfIsRefused() {
        PlayerMock player = server.addPlayer();
        ItemStack theToken = token.create();
        player.getInventory().setItemInMainHand(theToken);
        requests().request(player, "make it glow");
        assertTrue(player.nextMessage().contains("can't restyle a Cosmetic Token"));
    }

    @Test
    void aStackOfMoreThanOneIsRefused() {
        PlayerMock player = server.addPlayer();
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE, 2));
        requests().request(player, "make it glow");
        assertTrue(player.nextMessage().contains("Hold just one of it."));
    }

    @Test
    void noTokenAnywhereIsRefused() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        requests().request(player, "make it glow");
        assertTrue(player.nextMessage().contains("You need a Cosmetic Token"));
    }

    @Test
    void anEmptyIdeaIsRefused() {
        PlayerMock player = server.addPlayer();
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        requests().request(player, "   ");
        assertTrue(player.nextMessage().contains("Describe what you want"));
    }

    @Test
    void aSuccessfulRequestConsumesOneTokenStampsTheItemAndLogsTheExactLine() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token, 9);
        giveToken(player, token, 10);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));

        CosmeticRequests requests = requests();
        requests.request(player, "make it glow purple");

        // Exactly one token spent, not both.
        int tokensLeft = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (token.is(stack)) {
                tokensLeft++;
            }
        }
        assertEquals(1, tokensLeft);

        ItemStack held = player.getInventory().getItemInMainHand();
        ItemMeta meta = held.getItemMeta();
        assertEquals(1, (int) meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "cosmetic_id"), PersistentDataType.INTEGER));
        assertEquals(List.of("cosmetic_1"), meta.getCustomModelDataComponent().getStrings());

        CosmeticRequests.Request stored = requests.get(1);
        assertEquals(CosmeticRequests.Status.PENDING, stored.status());
        assertEquals("Steve", stored.playerName());
        assertEquals("make it glow purple", stored.idea());
        assertEquals("diamond_sword", stored.material());

        assertEquals(List.of("Cosmetic request #1 from Steve (" + player.getUniqueId() + "): diamond_sword - make it glow purple"),
                logMessages);
        assertTrue(player.nextMessage().contains("Token used! Request #1"));
    }

    @Test
    void stampingKeepsAnExistingDragonHeadStringAndReplacesAnOlderCosmeticOne() {
        PlayerMock player = server.addPlayer();
        giveToken(player, token);
        ItemStack item = ItemStack.of(Material.NETHERITE_HELMET);
        item.editMeta(meta -> {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("dragon_head", "cosmetic_7"));
            meta.setCustomModelDataComponent(cmd);
        });
        player.getInventory().setItemInMainHand(item);

        requests().request(player, "shinier");

        List<String> strings = player.getInventory().getItemInMainHand().getItemMeta().getCustomModelDataComponent().getStrings();
        assertTrue(strings.contains("dragon_head"), strings.toString());
        assertFalse(strings.contains("cosmetic_7"), strings.toString());
        assertTrue(strings.contains("cosmetic_1"), strings.toString());
        assertEquals(2, strings.size());
    }

    @Test
    void nonAdminCannotUseListDoneOrRefund() {
        PlayerMock nobody = server.addPlayer();
        CosmeticRequests requests = requests();
        requests.list(nobody);
        assertTrue(nobody.nextMessage().contains("can't do that"));
        requests.done(nobody, 1);
        assertTrue(nobody.nextMessage().contains("can't do that"));
        requests.refund(nobody, 1);
        assertTrue(nobody.nextMessage().contains("can't do that"));
    }

    @Test
    void doneNotifiesAnOnlinePlayerImmediately() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        CosmeticRequests requests = requests();
        requests.request(player, "shinier");
        player.nextMessage(); // drains the request's own "Token used!" confirmation

        requests.done(server.getConsoleSender(), 1);

        assertTrue(player.nextMessage().contains("#1 is live"));
        assertTrue(requests.get(1).notified());
        assertEquals(CosmeticRequests.Status.DONE, requests.get(1).status());
    }

    @Test
    void doneForAnOfflinePlayerIsDeliveredOnTheirNextJoinAndSurvivesAReload() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        File stateFile = new File(dir, "cosmetics.yml");
        CosmeticRequests first = requests(stateFile);
        first.request(player, "shinier");
        player.nextMessage(); // drains the request's own "Token used!" confirmation
        player.disconnect();

        first.done(server.getConsoleSender(), 1);
        assertFalse(first.get(1).notified(), "nobody online to tell yet");

        // A restart: a fresh instance reloads from disk and still owes the notification.
        CosmeticRequests reloaded = requests(stateFile);
        assertEquals(CosmeticRequests.Status.DONE, reloaded.get(1).status());
        assertFalse(reloaded.get(1).notified());

        reloaded.onJoin(new PlayerJoinEvent(player, Component.empty()));
        assertTrue(player.nextMessage().contains("#1 is live"));
        assertTrue(reloaded.get(1).notified());
    }

    @Test
    void refundGivesTheOnlinePlayerAFreshTokenImmediately() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        CosmeticRequests requests = requests();
        requests.request(player, "shinier");
        player.nextMessage(); // drains the request's own "Token used!" confirmation
        // The one token was spent on the request; none should remain right now.
        boolean hasTokenNow = token.hasToken(player.getInventory());
        assertFalse(hasTokenNow);

        requests.refund(server.getConsoleSender(), 1);

        assertTrue(token.hasToken(player.getInventory()), "a fresh token was given back");
        assertTrue(player.nextMessage().contains("refunded"));
        assertEquals(CosmeticRequests.Status.REFUNDED, requests.get(1).status());
    }

    @Test
    void refundForAnOfflinePlayerGivesTheTokenOnTheirNextJoin() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        CosmeticRequests requests = requests();
        requests.request(player, "shinier");
        player.nextMessage(); // drains the request's own "Token used!" confirmation
        player.disconnect();

        requests.refund(server.getConsoleSender(), 1);
        assertFalse(token.hasToken(player.getInventory()), "not given while offline");

        requests.onJoin(new PlayerJoinEvent(player, Component.empty()));
        assertTrue(token.hasToken(player.getInventory()));
        assertTrue(player.nextMessage().contains("refunded"));
    }

    @Test
    void anUnknownIdOrTheWrongStatusIsRefusedClearly() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        CosmeticRequests requests = requests();
        requests.request(player, "shinier");

        requests.done(server.getConsoleSender(), 999);
        assertTrue(server.getConsoleSender().nextMessage().contains("No cosmetic request"));

        requests.done(server.getConsoleSender(), 1);
        server.getConsoleSender().nextMessage();
        requests.done(server.getConsoleSender(), 1);
        assertTrue(server.getConsoleSender().nextMessage().contains("already"));
    }

    @Test
    void listShowsOnlyPendingRequests() {
        PlayerMock player = server.addPlayer("Steve");
        giveToken(player, token, 9);
        giveToken(player, token, 10);
        player.getInventory().setItemInMainHand(ItemStack.of(Material.STONE));
        CosmeticRequests requests = requests();
        requests.request(player, "shinier");
        player.getInventory().setItemInMainHand(ItemStack.of(Material.DIRT));
        requests.request(player, "muddier");
        requests.done(server.getConsoleSender(), 1);
        server.getConsoleSender().nextMessage();

        requests.list(server.getConsoleSender());
        assertTrue(server.getConsoleSender().nextMessage().contains("muddier"));
        assertNull(server.getConsoleSender().nextMessage(), "the done request #1 is not listed");
    }
}
