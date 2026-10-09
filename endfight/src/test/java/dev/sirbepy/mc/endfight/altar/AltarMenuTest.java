package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.tier.Tier;
import dev.sirbepy.mc.endfight.tier.TierService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the menu's click wiring: {@code core.gui.MenuListener} already cancels every click
 * generically (every other menu in this repo relies on the same thing), so this only needs to
 * check this menu's own behaviour - setting the altar's next tier, and refusing to during the
 * first-kill-ever lock. */
class AltarMenuTest {

    private static final Logger LOGGER = Logger.getLogger("altar-menu-test");
    // AltarMenu.TIER_SLOTS[i] for Tier.values()[i] (FLEDGELING, POWERFUL, CRUEL, ...) - slot 12 is Cruel.
    private static final int CRUEL_SLOT = 12;

    private ServerMock server;
    private PlayerMock player;
    private File dir;
    private EndFightConfig config;

    @BeforeEach
    void setUp(@TempDir File dir) {
        this.dir = dir;
        server = MockBukkit.mock();
        WorldMock end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        player = server.addPlayer("Alice");
        player.teleport(new Location(end, 0, 80, 0));
        config = new EndFightConfig(new YamlConfiguration());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TierService tiers(String fileName, boolean previouslyKilled) {
        return new TierService(new File(dir, fileName), Tier.FLEDGELING, () -> previouslyKilled, LOGGER);
    }

    private InventoryClickEvent click(Menu shown, int slot) {
        player.openInventory(shown.getInventory());
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        new MenuListener().onClick(event);
        return event;
    }

    @Test
    void clickingATierSetsItAsTheAltarsNextChoice() {
        TierService tiers = tiers("tier.yml", true); // not the server's first fight
        AltarMenu menu = new AltarMenu(tiers, () -> config, WeeklyHoardInfo.NONE_TAKEN, AuraMenu.NONE);
        Menu shown = menu.build(player);

        InventoryClickEvent event = click(shown, CRUEL_SLOT);

        assertEquals(Tier.CRUEL, tiers.next());
        assertTrue(event.isCancelled(), "a menu click must never be allowed to take the icon");
    }

    @Test
    void duringTheFirstKillEverStateClickingATierIsALockedNoOp() {
        TierService forced = tiers("forced.yml", false); // no dragon has ever died here
        AltarMenu menu = new AltarMenu(forced, () -> config, WeeklyHoardInfo.NONE_TAKEN, AuraMenu.NONE);
        Menu shown = menu.build(player);

        InventoryClickEvent event = click(shown, CRUEL_SLOT);

        assertEquals(Tier.FLEDGELING, forced.next(), "the altar is locked during the first-kill-ever state");
        assertTrue(event.isCancelled());
    }

    @Test
    void theAurasSlotOpensTheAuraMenuInstead() {
        TierService tiers = tiers("tier2.yml", true);
        boolean[] opened = {false};
        AuraMenu recordingAuraMenu = p -> opened[0] = true;
        AltarMenu menu = new AltarMenu(tiers, () -> config, WeeklyHoardInfo.NONE_TAKEN, recordingAuraMenu);
        Menu shown = menu.build(player);

        click(shown, 22);

        assertTrue(opened[0], "the Auras slot should delegate to the AuraMenu");
    }
}
