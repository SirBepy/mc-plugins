package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.NameStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The [BE] prefix (Joe, 2026-10-09): Bedrock players get it, Java players don't, it renders left
 * of Tag's IT marker, and turning it off in config takes it away on the next update.
 */
class BedrockTagTest {

    private ServerMock server;
    private PluginMock plugin;
    private NamesConfig config;
    private PrefixRegistry prefixes;
    private NameService names;
    private PlayerMock bedrockPlayer;
    private PlayerMock javaPlayer;
    private BedrockTag tag;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Names");
        config = new NamesConfig(plugin);
        NameStore store = new NameStore(plugin);
        store.load();
        prefixes = new PrefixRegistry();
        names = new NameService(store, config, prefixes);
        bedrockPlayer = server.addPlayer(".Steve");
        javaPlayer = server.addPlayer("Alex");
        tag = new BedrockTag(config, new FakeBedrock(Set.of(bedrockPlayer.getUniqueId())), prefixes);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String rendered(Player player) {
        return PlainTextComponentSerializer.plainText().serialize(names.nameComponent(player));
    }

    @Test
    void aBedrockPlayerGetsTheTagInFrontOfTheirNameDotIncluded() {
        tag.update(bedrockPlayer);

        assertEquals("[BE] .Steve", rendered(bedrockPlayer));
    }

    @Test
    void aJavaPlayerGetsNoTag() {
        tag.update(javaPlayer);

        assertEquals("Alex", rendered(javaPlayer));
    }

    @Test
    void theTagRendersLeftOfTagsItMarker() {
        // Tag's PrefixService sets its marker at priority 0 (tag/src/main/resources/config.yml).
        prefixes.set(bedrockPlayer.getUniqueId(), "tag", Component.text("☣ "), 0);
        tag.update(bedrockPlayer);

        assertEquals("[BE] ☣ .Steve", rendered(bedrockPlayer));
    }

    @Test
    void disablingItInConfigRemovesTheTag() {
        tag.update(bedrockPlayer);
        // NamesConfig.reload() re-reads from disk, so the override must be saved.
        plugin.getConfig().set("bedrock.tag.enabled", false);
        plugin.saveConfig();
        config.reload();

        tag.update(bedrockPlayer);

        assertEquals(".Steve", rendered(bedrockPlayer));
    }

    private record FakeBedrock(Set<UUID> bedrock) implements BedrockForms {

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock.contains(player);
        }

        @Override
        public void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit) {
        }

        @Override
        public void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick) {
        }
    }
}
