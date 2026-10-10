package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.NameStore;
import dev.sirbepy.mc.names.render.CustomPrefix;
import dev.sirbepy.mc.names.render.NameService;
import dev.sirbepy.mc.names.render.PrefixRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The op-given custom prefix (Joe, 2026-10-10, first for ItsLogiks' gold [Royal Supplier]): set and
 * cleared from the console, saved across restarts, placed between Tag's marker and the Guild title,
 * and never colouring the name after it.
 */
class NamesPrefixCommandTest {

    private static final String ROYAL = "<gold>[Royal Supplier]</gold>";

    private ServerMock server;
    private PluginMock plugin;
    private NamesConfig config;
    private NameStore store;
    private PrefixRegistry prefixes;
    private NameService names;
    private NamesPrefixCommand command;
    private ConsoleCommandSenderMock console;
    private PlayerMock logiks;
    private final List<Player> rerendered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Names");
        plugin.getConfig().set("messages.prefix-set", "set <target> <prefix>");
        plugin.getConfig().set("messages.prefix-cleared", "cleared <target>");
        plugin.getConfig().set("messages.prefix-usage", "usage");
        plugin.getConfig().set("messages.prefix-blank", "blank");
        plugin.getConfig().set("messages.no-permission", "no permission");
        plugin.getConfig().set("messages.nick-player-not-found", "no player <target>");
        plugin.saveConfig();
        config = new NamesConfig(plugin);
        store = new NameStore(plugin);
        store.load();
        prefixes = new PrefixRegistry();
        names = new NameService(store, config, prefixes);
        command = new NamesPrefixCommand(config, new CustomPrefix(store, prefixes), rerendered::add);
        console = server.getConsoleSender();
        logiks = server.addPlayer("ItsLogiks");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void run(org.bukkit.command.CommandSender sender, String line) {
        command.execute(sender, line.split(" "));
    }

    private static String next(org.mockbukkit.mockbukkit.command.MessageTarget target) {
        return PlainTextComponentSerializer.plainText().serialize(target.nextComponentMessage());
    }

    private String rendered(Player player) {
        return PlainTextComponentSerializer.plainText().serialize(names.nameComponent(player));
    }

    @Test
    void theConsoleSetsAPrefixThatShowsBeforeTheNickAndReRendersThePlayer() {
        store.setNick(logiks.getUniqueId(), "LordOfTheDeep");

        run(console, "prefix set ItsLogiks " + ROYAL);

        assertEquals("set ItsLogiks [Royal Supplier] ", next(console));
        assertEquals("[Royal Supplier] LordOfTheDeep", rendered(logiks));
        assertEquals(List.of(logiks), rerendered);
    }

    @Test
    void thePrefixIsGoldAndTheNameKeepsItsOwnColour() {
        store.setColor(logiks.getUniqueId(), "hex:6DD5ED");
        PlayerMock plain = server.addPlayer("Alice");
        run(console, "prefix set ItsLogiks " + ROYAL);
        run(console, "prefix set Alice " + ROYAL);

        assertEquals(NamedTextColor.GOLD, colorOf(names.nameComponent(logiks), "[Royal Supplier]"));
        assertEquals(TextColor.fromHexString("#6DD5ED"), colorOf(names.nameComponent(logiks), "ItsLogiks"));
        assertNull(colorOf(names.nameComponent(plain), "Alice"), "an uncoloured name must not turn gold");
    }

    @Test
    void itSitsBetweenTagsMarkerAndTheGuildTitle() {
        // Tag's marker is priority 0 and Guild's NamesTitleSink 10.
        prefixes.set(logiks.getUniqueId(), "guild-title", Component.text("[Dragonslayer] ", NamedTextColor.GOLD), 10);
        prefixes.set(logiks.getUniqueId(), "tag", Component.text("☣ "), 0);

        run(console, "prefix set ItsLogiks " + ROYAL);

        assertEquals("☣ [Royal Supplier] [Dragonslayer] ItsLogiks", rendered(logiks));
    }

    @Test
    void itIsSavedAndLoadedBackAfterARestart() {
        run(console, "prefix set ItsLogiks " + ROYAL);

        NameStore reloaded = new NameStore(plugin);
        reloaded.load();
        PrefixRegistry fresh = new PrefixRegistry();
        new CustomPrefix(reloaded, fresh).loadAll();

        assertEquals("[Royal Supplier] ItsLogiks",
                PlainTextComponentSerializer.plainText().serialize(new NameService(reloaded, config, fresh).nameComponent(logiks)));
    }

    @Test
    void anOfflinePlayerTheServerHasSeenCanBeGivenOne() {
        logiks.disconnect();

        run(console, "prefix set ItsLogiks " + ROYAL);

        assertEquals("set ItsLogiks [Royal Supplier] ", next(console));
        assertEquals(ROYAL, store.prefixes().get(logiks.getUniqueId()));
        assertEquals(List.of(), rerendered, "nobody online to re-render");
    }

    @Test
    void clearRemovesItFromTheNameAndTheSaveFile() {
        run(console, "prefix set ItsLogiks " + ROYAL);
        next(console);

        run(console, "prefix clear ItsLogiks");

        assertEquals("cleared ItsLogiks", next(console));
        assertEquals("ItsLogiks", rendered(logiks));
        assertTrue(store.prefixes().isEmpty());
    }

    @Test
    void badInputIsRefused() {
        run(console, "prefix set NoSuchPlayer " + ROYAL);
        assertEquals("no player NoSuchPlayer", next(console));
        run(console, "prefix set ItsLogiks <gold></gold>");
        assertEquals("blank", next(console));
        run(console, "prefix set ItsLogiks");
        assertEquals("usage", next(console));
        assertTrue(store.prefixes().isEmpty());
    }

    @Test
    void aPlayerWithoutNamesAdminCannotUseIt() {
        run(logiks, "prefix set ItsLogiks " + ROYAL);

        assertEquals("no permission", next(logiks));
        assertTrue(store.prefixes().isEmpty());
    }

    /** The colour a text node would show with, inherited from its parents as Adventure renders it. */
    private static TextColor colorOf(Component root, String text) {
        return find(root, text, null);
    }

    private static TextColor find(Component node, String text, TextColor inherited) {
        TextColor color = node.color() != null ? node.color() : inherited;
        if (node instanceof TextComponent t && t.content().contains(text)) {
            return color;
        }
        for (Component child : node.children()) {
            TextColor found = find(child, text, color);
            if (found != null || containsText(child, text)) {
                return found;
            }
        }
        return null;
    }

    private static boolean containsText(Component node, String text) {
        return PlainTextComponentSerializer.plainText().serialize(node).contains(text);
    }
}
