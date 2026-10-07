package dev.sirbepy.mc.core.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void texturedProfileCarriesTheTextureUnderItsId() {
        UUID id = UUID.fromString("6d1e7a3b-0000-4000-8000-000000000001");
        var profile = Heads.texturedProfile(id, "deadbeef");
        assertEquals(id, profile.getId());
        assertTrue(profile.getProperties().stream()
                .anyMatch(p -> p.getName().equals("textures") && p.getValue().equals("deadbeef")));
    }

    @Test
    void texturedProfileIsNullForABlankTexture() {
        UUID id = UUID.randomUUID();
        assertNull(Heads.texturedProfile(id, ""));
        assertNull(Heads.texturedProfile(id, null));
    }

    @Test
    void texturedHeadIsAPlayerHeadCarryingTheProfile() {
        UUID id = UUID.fromString("6d1e7a3b-0000-4000-8000-000000000002");
        ItemStack item = Heads.texturedHead(id, "cafebabe");

        assertEquals(Material.PLAYER_HEAD, item.getType());
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        assertEquals(id, meta.getPlayerProfile().getId());
    }

    @Test
    void texturedHeadIsStillAPlayerHeadForABlankTexture() {
        // A blank texture skips setPlayerProfile entirely (see texturedProfileIsNullForABlankTexture);
        // this only confirms the item itself still builds rather than throwing.
        ItemStack item = Heads.texturedHead(UUID.randomUUID(), "");

        assertEquals(Material.PLAYER_HEAD, item.getType());
    }
}
