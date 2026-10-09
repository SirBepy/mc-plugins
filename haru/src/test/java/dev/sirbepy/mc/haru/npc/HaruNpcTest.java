package dev.sirbepy.mc.haru.npc;

import dev.sirbepy.mc.core.npc.NpcSkin;
import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.gui.HaruMenus;
import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.haru.item.BoundListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaruNpcTest extends HaruTestBase {

    private HaruMenus menus;
    private HaruInteractListener interact;
    private Villager haru;

    @BeforeEach
    void setUp() {
        menus = new HaruMenus(ctx);
        interact = new HaruInteractListener(ctx, menus, new BoundListener(ctx));
        ctx.npc().setLooks(mannequin -> { }, display -> { });
        ctx.npc().place(new Location(world, 10, 64, 10, 90, 0));
        haru = ctx.npc().villager();
        // MockBukkit only counts a chunk as loaded once something asks for it.
        world.getChunkAt(0, 0);
    }

    @Test
    void placedHaruIsAnUnkillableAiLessClericThatStays() {
        assertTrue(ctx.npc().isHaru(haru));
        assertFalse(haru.hasAI());
        assertTrue(haru.isInvulnerable());
        assertTrue(haru.isPersistent());
        assertEquals(Villager.Profession.CLERIC, haru.getProfession());
        assertEquals(haru.getUniqueId(), store.npcId());
        assertEquals(10.5, store.anchor().x());
    }

    @Test
    void javaPlayersGetTheMannequinAndBedrockPlayersTheVillager() {
        Mannequin mannequin = ctx.npc().mannequin();
        assertNotNull(mannequin);
        assertTrue(ctx.npc().isHaru(mannequin));
        assertTrue(mannequin.isInvulnerable());
        assertTrue(mannequin.isImmovable());

        PlayerMock bedrockPlayer = server.addPlayer(".Pocket");
        HaruNpc npc = new HaruNpc(plugin, store, id -> id.equals(bedrockPlayer.getUniqueId()), new HaruLook(config::get));
        npc.applyVisibility(owner);
        npc.applyVisibility(bedrockPlayer);

        assertTrue(owner.canSee(mannequin));
        assertFalse(owner.canSee(haru));
        assertFalse(bedrockPlayer.canSee(mannequin));
        assertTrue(bedrockPlayer.canSee(haru));
    }

    @Test
    void aPackSkinIsHiddenOnlyFromPlayersWhosePackFailed() {
        config.set(new HaruConfig(owner.getUniqueId(), config.get().zone(), "world", 3, 0.2,
                NpcSkin.pack("claudesmp:entity/haru", "", false)));
        ctx.npc().applyVisibility(owner);
        assertTrue(owner.canSee(ctx.npc().mannequin()), "status unknown (cached pack): Mannequin");
        assertFalse(owner.canSee(haru));

        ctx.npc().viewers().setPackFailed(owner, true);
        assertFalse(owner.canSee(ctx.npc().mannequin()), "declined or failed: villager");
        assertTrue(owner.canSee(haru));

        ctx.npc().viewers().setPackFailed(owner, false);
        assertTrue(owner.canSee(ctx.npc().mannequin()));
    }

    @Test
    void rightClickingTheMannequinAlsoOpensTheMenu() {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(owner, ctx.npc().mannequin(), EquipmentSlot.HAND);
        interact.onInteract(event);
        assertTrue(event.isCancelled());
        assertInstanceOf(Menu.class, owner.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    void anOldVillagerOnlyHaruGetsHisMannequinOnTheNextGuardTick() {
        ctx.npc().mannequin().remove();
        store.setMannequinId(null);
        ctx.npc().ensure();
        assertNotNull(ctx.npc().mannequin());
        assertEquals(haru.getUniqueId(), ctx.npc().villager().getUniqueId());
    }

    @Test
    void damageNeverLands() {
        EntityDamageEvent event = new EntityDamageEvent(haru, EntityDamageEvent.DamageCause.LIGHTNING,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.LIGHTNING_BOLT).build(), 5);
        new HaruProtectionListener(ctx.npc()).onDamage(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void aKilledHaruIsBackOnTheNextGuardTick() {
        haru.remove();
        ctx.npc().ensure();
        Villager back = ctx.npc().villager();
        assertNotNull(back);
        assertNotEquals(haru.getUniqueId(), back.getUniqueId());
        assertTrue(back.getLocation().distance(new Location(world, 10.5, 64, 10.5)) < 0.01);
    }

    @Test
    void nothingIsRespawnedWhileHisChunkIsUnloaded() {
        haru.remove();
        world.unloadChunk(0, 0);
        ctx.npc().ensure();
        assertNull(ctx.npc().villager());
    }

    @Test
    void aMovedHaruIsSnappedBack() {
        haru.teleport(new Location(world, 30, 64, 30));
        ctx.npc().ensure();
        assertTrue(haru.getLocation().distance(new Location(world, 10.5, 64, 10.5)) < 0.01);
    }

    @Test
    void ownerRightClickOpensTheMenuAndNoTradeScreen() {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(owner, haru, EquipmentSlot.HAND);
        interact.onInteract(event);
        assertTrue(event.isCancelled());
        assertInstanceOf(Menu.class, owner.getOpenInventory().getTopInventory().getHolder());
        assertTrue(store.state(owner.getUniqueId()).has(HaruInteractListener.MET));
    }

    @Test
    void strangersOnlyGetTheEverydayShelfOnTheirOwnLedger() {
        PlayerMock stranger = server.addPlayer("Wanderer");
        stranger.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.EMERALD, 5));
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(stranger, haru, EquipmentSlot.HAND);
        interact.onInteract(event);
        assertTrue(event.isCancelled());
        assertNotNull(stranger.nextMessage());
        Menu shelf = (Menu) stranger.getOpenInventory().getTopInventory().getHolder();
        assertEquals(-1, slotOrMissing(shelf, Material.ENCHANTED_BOOK), "no trust-gated wares for guests");
        click(stranger, shelf, slotOf(shelf, Material.REDSTONE));
        assertEquals(5, count(stranger, Material.REDSTONE));
        assertEquals(1, store.state(stranger.getUniqueId()).bought("ev_redstone", ctx.shop().period(
                dev.sirbepy.mc.haru.shop.Restock.ROTATION)));
        assertEquals(0, state().bought("ev_redstone", ctx.shop().period(dev.sirbepy.mc.haru.shop.Restock.ROTATION)));
        assertEquals(HaruMenus.Screen.EVERYDAY.name(), screenName(menus.build(stranger, HaruMenus.Screen.SECRET)));
    }

    @Test
    void afterTheOwnerDiesTheShopIsShutForADay() {
        state().setClosedUntil(clock.nowMillis + dev.sirbepy.mc.haru.effect.OwnerEffectsListener.CLOSED_MILLIS);
        PlayerMock stranger = server.addPlayer("Wanderer");
        interact.onInteract(new PlayerInteractEntityEvent(stranger, haru, EquipmentSlot.HAND));
        var top = stranger.getOpenInventory().getTopInventory();
        assertFalse(top != null && top.getHolder() instanceof Menu, "guests get nothing while closed");

        interact.onInteract(new PlayerInteractEntityEvent(owner, haru, EquipmentSlot.HAND));
        Menu shown = (Menu) owner.getOpenInventory().getTopInventory().getHolder();
        assertEquals(HaruMenus.Screen.QUESTS.name(), screenName(shown), "the owner can still settle requests");

        clock.nowMillis += dev.sirbepy.mc.haru.effect.OwnerEffectsListener.CLOSED_MILLIS + 1;
        assertEquals(HaruMenus.Screen.MAIN.name(), screenName(menus.build(owner, HaruMenus.Screen.MAIN)));
    }

    @Test
    void buyingFromTheMenuWorksAndNothingCanBeTakenOut() {
        owner.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.EMERALD, 5));
        Menu main = menus.open(owner, HaruMenus.Screen.MAIN);
        click(owner, main, 20);
        Menu everyday = (Menu) owner.getOpenInventory().getTopInventory().getHolder();
        assertNotEquals(main, everyday);
        int redstoneSlot = slotOf(everyday, Material.REDSTONE);
        InventoryClickEvent event = click(owner, everyday, redstoneSlot);
        assertTrue(event.isCancelled());
        assertEquals(5, count(owner, Material.REDSTONE));
        assertEquals(3, count(owner, Material.EMERALD));
    }

    @Test
    void secretStockStaysShutBelowMaxTrust() {
        Menu main = menus.open(owner, HaruMenus.Screen.MAIN);
        assertEquals(Material.GRAY_DYE, main.getInventory().getItem(49).getType());
        click(owner, main, 49);
        assertEquals(main, owner.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    void mysteryCurioFromTheMenuSpendsPetals() {
        state().setPetals(100);
        Menu main = menus.open(owner, HaruMenus.Screen.MAIN);
        click(owner, main, 31);
        assertEquals(1, ctx.curios().boughtToday(state(), clock.today));
        assertTrue(state().petals() < 100);
    }

    private InventoryClickEvent click(PlayerMock player, Menu menu, int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        new MenuListener().onClick(event);
        return event;
    }

    private static int slotOf(Menu menu, Material material) {
        int slot = slotOrMissing(menu, material);
        assertNotEquals(-1, slot, material + " is on the shelf");
        return slot;
    }

    private static int slotOrMissing(Menu menu, Material material) {
        for (int slot = 0; slot < menu.getInventory().getSize(); slot++) {
            var item = menu.getInventory().getItem(slot);
            if (item != null && item.getType() == material) {
                return slot;
            }
        }
        return -1;
    }

    /** Identifies a screen by its title text. */
    private static String screenName(Menu menu) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(menu.title());
        if (title.startsWith("Everyday")) {
            return HaruMenus.Screen.EVERYDAY.name();
        }
        if (title.startsWith("Spirit Requests")) {
            return HaruMenus.Screen.QUESTS.name();
        }
        if (title.contains("Sanctuary")) {
            return HaruMenus.Screen.MAIN.name();
        }
        return title;
    }

}
