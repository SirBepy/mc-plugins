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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proves {@link HeadService#syncFightExemption} is the pause/resume switch for the
 * forced sheep head during an Ender Dragon fight - not a one-shot opt-out, and not a change to
 * anyone's IT status itself (that stays owned by {@code TagService}).
 */
class HeadServiceFightExemptionTest {

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
    void nonFighterStillGetsTheSheepHeadForcedAsBefore() {
        ItemStack ironHelmet = new ItemStack(Material.IRON_HELMET);
        player.getInventory().setHelmet(ironHelmet);

        heads.syncFightExemption(player, config, false);

        assertEquals(Material.PLAYER_HEAD, player.getInventory().getHelmet().getType(),
                "a non-fighter IT player must still have the sheep head forced on");
    }

    @Test
    void becomingItWhileFightingNeverTakesTheRealHelmet() {
        ItemStack ironHelmet = new ItemStack(Material.IRON_HELMET);
        player.getInventory().setHelmet(ironHelmet);

        // Mirrors TagService.crownIt: the very first head sync for a brand-new IT, with fighting=true.
        heads.syncFightExemption(player, config, true);

        assertEquals(Material.IRON_HELMET, player.getInventory().getHelmet().getType(),
                "a fighter must keep their real helmet instead of having it displaced for the sheep head");
    }

    @Test
    void startingAFightWhileAlreadyWearingTheSheepHeadRestoresTheRealHelmet() {
        ItemStack diamondHelmet = new ItemStack(Material.DIAMOND_HELMET);
        player.getInventory().setHelmet(diamondHelmet);
        heads.grant(player, config);
        assertEquals(Material.PLAYER_HEAD, player.getInventory().getHelmet().getType(), "sanity: head was forced on first");

        heads.syncFightExemption(player, config, true);

        assertEquals(Material.DIAMOND_HELMET, player.getInventory().getHelmet().getType(),
                "starting a fight must hand the real helmet back");
    }

    @Test
    void endingTheFightResumesForcingTheSheepHead() {
        ItemStack diamondHelmet = new ItemStack(Material.DIAMOND_HELMET);
        player.getInventory().setHelmet(diamondHelmet);
        heads.grant(player, config);
        heads.syncFightExemption(player, config, true);
        assertNotEquals(Material.PLAYER_HEAD, player.getInventory().getHelmet().getType(), "sanity: paused during the fight");

        heads.syncFightExemption(player, config, false);

        assertEquals(Material.PLAYER_HEAD, player.getInventory().getHelmet().getType(),
                "the fight ending must resume forcing the sheep head");
    }
}
