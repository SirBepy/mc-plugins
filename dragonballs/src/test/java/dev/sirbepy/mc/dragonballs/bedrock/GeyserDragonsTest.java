package dev.sirbepy.mc.dragonballs.bedrock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The Geyser hook registers exactly the entities the shipped Bedrock pack draws (a part missing
 * from either side would be invisible on Bedrock), and turns fold into the yaw property's range.
 */
class GeyserDragonsTest {

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
