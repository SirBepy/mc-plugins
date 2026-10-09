package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.arena.ArenaLayout;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import dev.sirbepy.mc.endfight.tier.Tier;
import dev.sirbepy.mc.endfight.tier.TierService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.block.Action;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltarInteractListenerTest {

    private static final int SURFACE_Y = 64;

    private ServerMock server;
    private WorldMock end;
    private PlayerMock player;
    private ArenaRegistry registry;
    private AltarLocator locator;
    private AltarInteractListener listener;

    @BeforeEach
    void setUp(@TempDir File dir) {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        player = server.addPlayer("Alice");

        List<Pillar> pillars = List.of(new Pillar(100, 100, 3, 80, SURFACE_Y));
        registry = new ArenaRegistry();
        registry.update(pillars, ArenaLayout.build(pillars, SURFACE_Y), true);
        locator = new AltarLocator(registry);

        TierService tiers = new TierService(new File(dir, "tier.yml"), Tier.FLEDGELING, () -> true, Logger.getLogger("test"));
        EndFightConfig config = new EndFightConfig(new YamlConfiguration());
        AltarMenu menu = new AltarMenu(tiers, () -> config, WeeklyHoardInfo.NONE_TAKEN, AuraMenu.NONE);
        listener = new AltarInteractListener(locator, menu);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerInteractEvent interact(Location location) {
        var block = end.getBlockAt(location);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block,
                org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(event);
        return event;
    }

    @Test
    void rightClickingTheAltarsLodestoneOpensTheMenuAndCancelsTheEvent() {
        Location altarLocation = new Location(end, 0, SURFACE_Y + 1, -9);

        PlayerInteractEvent event = interact(altarLocation);

        assertTrue(event.isCancelled());
        assertInstanceOf(Menu.class, player.getOpenInventory().getTopInventory().getHolder(),
                "the altar menu should now be open for the player");
    }

    @Test
    void rightClickingAnyOtherBlockDoesNothing() {
        Location elsewhere = new Location(end, 5, SURFACE_Y + 1, 5);

        PlayerInteractEvent event = interact(elsewhere);

        assertFalse(event.isCancelled());
        var topInventory = player.getOpenInventory() == null ? null : player.getOpenInventory().getTopInventory();
        assertFalse(topInventory != null && topInventory.getHolder() instanceof Menu, "no menu should have opened");
    }
}
