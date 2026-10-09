package dev.sirbepy.mc.dragonballs.bedrock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.IOException;
import java.nio.file.Files;
import java.io.InputStream;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The Geyser hook registers exactly the entities the shipped Bedrock pack draws (a part missing
 * from either side would be invisible on Bedrock), turns fold into the yaw property's range, and
 * with bedrock-dragon-models off the hook leaves Geyser alone even when Geyser is installed.
 */
class GeyserDragonsTest {

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void aDisabledHookRegistersNothingWithGeyser() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin("Geyser-Spigot");
        PluginMock plugin = MockBukkit.createMockPlugin();

        assertSame(BedrockDragons.NONE, BedrockDragons.hook(plugin, false));
        // GeyserDragons copies its pack out before it subscribes; disabled, it never got that far.
        assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve(GeyserDragons.PACK_RESOURCE)));
    }

    @Test
    void theHookRegistersEveryEntityThePackDraws() throws IOException {
        TreeSet<String> inPack = new TreeSet<>();
        Pattern entity = Pattern.compile("entity/dragon_(.+)\\.entity\\.json");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(GeyserDragons.PACK_RESOURCE)) {
            assertNotNull(in, "the jar ships the Bedrock pack");
            ZipInputStream zip = new ZipInputStream(in);
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                Matcher m = entity.matcher(e.getName());
                if (m.matches()) {
                    inPack.add(m.group(1));
                }
            }
        }
        assertEquals(inPack, new TreeSet<>(GeyserDragons.PARTS));
    }

    @Test
    void yawFoldsIntoMinus180To180() {
        assertEquals(0f, BedrockDragons.wrapYaw(360f), 1e-4);
        assertEquals(-90f, BedrockDragons.wrapYaw(270f), 1e-4);
        assertEquals(170f, BedrockDragons.wrapYaw(-190f), 1e-4);
        assertEquals(-180f, BedrockDragons.wrapYaw(180f), 1e-4);
        assertEquals(45f, BedrockDragons.wrapYaw(45f + 720f), 1e-4);
    }
}
