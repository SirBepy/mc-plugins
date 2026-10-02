package dev.sirbepy.mc.tag.head;

import dev.sirbepy.mc.tag.config.HeadConfig;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the e2e-discovered race (todo: see docs root cause writeup): a player's own click to
 * equip something onto their head slot can land exactly when they become IT. HeadService has
 * already overwritten the slot with the tag head by then, so the click must be cancelled - but
 * cancelling alone leaves the item the player was holding stranded on their cursor, invisible to
 * both their inventory and ender chest (confirmed live: it reappears only once something else
 * places the cursor down). A real player disconnecting with the window in that state would lose
 * the item for good.
 */
class HeadProtectionListenerTest {

    private static final int HEAD_SLOT = 5;

    private ServerMock server;
    private HeadService heads;
    private HeadProtectionListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        heads = new HeadService(plugin);
        listener = new HeadProtectionListener(heads);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cancelledClickOntoTheHeadReturnsTheStrandedCursorItemInstead() {
        ItemStack ironHelmet = new ItemStack(Material.IRON_HELMET);
        player.getInventory().setHelmet(heads.buildHead(HeadConfig.from(null)));

        PlayerInventoryViewMock view = new PlayerInventoryViewMock(player, player.getInventory());
        view.setItem(HEAD_SLOT, player.getInventory().getHelmet());
        view.setCursor(ironHelmet);

        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.ARMOR, HEAD_SLOT,
                ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "click onto the tag head must be cancelled");
        assertTrue(view.getCursor() == null || view.getCursor().isEmpty(),
                "the cursor must not be left holding the displaced item");
        assertEquals(Material.PLAYER_HEAD, player.getInventory().getHelmet().getType(),
                "the tag head itself must stay put");

        // Comparing against the original ironHelmet reference would be unreliable here: mark()
        // mutates whichever ItemStack instance absorbDisplaced actually received, which may be a
        // mock-internal clone of what was put on the cursor, not the same object this test holds.
        ItemStack landed = java.util.Arrays.stream(player.getInventory().getContents())
                .filter(item -> item != null && item.getType() == Material.IRON_HELMET)
                .findFirst().orElseThrow(() -> new AssertionError("the displaced item must land back in the player's inventory, not vanish"));
        assertTrue(heads.isMarkedHelmet(landed),
                "the rescued item must be marked like any other displaced helmet, or revoke() can never find it again");
    }

    @Test
    void clickNotTouchingTheHeadIsLeftAlone() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        PlayerInventoryViewMock view = new PlayerInventoryViewMock(player, player.getInventory());
        view.setItem(9, diamond);

        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 9,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onClick(event);

        assertFalse(event.isCancelled(), "a click with no tag head involved must not be cancelled");
    }
}
