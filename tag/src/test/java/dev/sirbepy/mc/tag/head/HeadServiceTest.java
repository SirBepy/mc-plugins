package dev.sirbepy.mc.tag.head;

import dev.sirbepy.mc.tag.config.HeadConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Reproduces todo 11: {@code ensureWorn} calls {@code grant} again when a player equips a new
 * helmet while still IT (e.g. after a late equip click absorbed by {@code absorbDisplaced}, or a
 * plain re-equip caught by the repeating ensureWorn check), marking that second helmet too.
 * revoke() previously restored only the first marked helmet {@code takeMarkedHelmet} found and
 * left every other one marked forever, so a later revoke could mistake it for a fresh displacement.
 */
class HeadServiceTest {

    private ServerMock server;
    private HeadService heads;
    private HeadConfig config;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        heads = new HeadService(plugin);
        config = HeadConfig.from(null);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void revokeAfterTwoRegrantsLeavesNoMarkedHelmetBehind() {
        ItemStack ironHelmet = new ItemStack(Material.IRON_HELMET);
        player.getInventory().setHelmet(ironHelmet);
        heads.grant(player, config);

        // A late equip while still IT: ensureWorn must displace it the same way the first helmet was.
        ItemStack diamondHelmet = new ItemStack(Material.DIAMOND_HELMET);
        player.getInventory().setHelmet(diamondHelmet);
        heads.ensureWorn(player, config);

        heads.revoke(player);

        boolean anyMarkedLeft = Arrays.stream(player.getInventory().getContents()).anyMatch(heads::isMarkedHelmet)
                || Arrays.stream(player.getEnderChest().getContents()).anyMatch(heads::isMarkedHelmet);
        assertFalse(anyMarkedLeft, "no item in inventory or ender chest should carry the displaced-helmet marker");

        ItemStack worn = player.getInventory().getHelmet();
        assertNotNull(worn, "a helmet should be worn again after revoke");
        assertNotEquals(Material.PLAYER_HEAD, worn.getType(), "the tag head itself must not still be worn");
    }
}
