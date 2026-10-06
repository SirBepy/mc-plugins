package dev.sirbepy.mc.haru.item;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.NpcAnchor;
import dev.sirbepy.mc.haru.dialogue.HeldItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaruItemListenerTest extends HaruTestBase {

    private HaruItemListener listener;
    private HaruItems items;

    @BeforeEach
    void setUp() {
        listener = new HaruItemListener(ctx);
        items = ctx.items();
    }

    private static ItemStack dragonBall() {
        ItemStack ball = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = ball.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("dragonballs", "star"), PersistentDataType.INTEGER, 4);
        ball.setItemMeta(meta);
        return ball;
    }

    private PlayerDeathEvent death(List<ItemStack> drops) {
        return new PlayerDeathEvent(owner, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                drops, 30, 0, 0, 0, Component.text("died"), false);
    }

    @Test
    void petalOfRebirthKeepsEverythingButDragonBalls() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack ball = dragonBall();
        List<ItemStack> drops = new ArrayList<>(List.of(sword, ball, items.rebirthPetal()));
        PlayerDeathEvent event = death(drops);

        listener.onDeath(event);

        assertEquals(List.of(ball), event.getDrops());
        assertEquals(List.of(sword), event.getItemsToKeep());
        assertTrue(event.getKeepLevel());
        assertEquals(0, event.getDroppedExp());
    }

    @Test
    void onlyOnePetalIsSpentPerDeath() {
        ItemStack petals = items.rebirthPetal();
        petals.setAmount(2);
        PlayerDeathEvent event = death(new ArrayList<>(List.of(petals)));
        listener.onDeath(event);
        assertEquals(1, event.getItemsToKeep().size());
        assertEquals(1, event.getItemsToKeep().getFirst().getAmount());
    }

    @Test
    void withoutAPetalDeathIsNormal() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        PlayerDeathEvent event = death(new ArrayList<>(List.of(sword)));
        listener.onDeath(event);
        assertEquals(List.of(sword), event.getDrops());
        assertTrue(event.getItemsToKeep().isEmpty());
    }

    @Test
    void haruItemsCannotBePlaced() {
        Block block = world.getBlockAt(1, 64, 1);
        ItemStack lantern = items.homewardPetal(1);
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), world.getBlockAt(1, 63, 1), lantern, owner,
                true, EquipmentSlot.HAND);
        listener.onPlace(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void homewardPetalWithoutASanctuaryDoesNothing() {
        owner.getInventory().setItemInMainHand(items.homewardPetal(1));
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_AIR, owner.getInventory().getItemInMainHand(),
                null, BlockFace.SELF, EquipmentSlot.HAND);
        listener.onUse(event);
        assertTrue(event.useItemInHand() == org.bukkit.event.Event.Result.DENY);
        assertEquals(1, owner.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void homewardPetalCarriesYouToHaruIfYouStandStill() {
        store.setAnchor(new NpcAnchor("world", 100.5, 70, 100.5, 0));
        owner.getInventory().setItemInMainHand(items.homewardPetal(2));
        listener.onUse(new PlayerInteractEvent(owner, Action.RIGHT_CLICK_AIR, owner.getInventory().getItemInMainHand(),
                null, BlockFace.SELF, EquipmentSlot.HAND));

        server.getScheduler().performTicks(20L * HaruItemListener.HOMEWARD_SECONDS + 1);

        assertTrue(owner.getLocation().distance(new Location(world, 100.5, 70, 102)) < 0.01);
        assertEquals(1, owner.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void movingCancelsTheHomewardPetal() {
        store.setAnchor(new NpcAnchor("world", 100.5, 70, 100.5, 0));
        owner.getInventory().setItemInMainHand(items.homewardPetal(1));
        listener.onUse(new PlayerInteractEvent(owner, Action.RIGHT_CLICK_AIR, owner.getInventory().getItemInMainHand(),
                null, BlockFace.SELF, EquipmentSlot.HAND));
        server.getScheduler().performTicks(10);
        owner.teleport(owner.getLocation().add(3, 0, 0));
        server.getScheduler().performTicks(20L * HaruItemListener.HOMEWARD_SECONDS + 1);
        assertEquals(1, owner.getInventory().getItemInMainHand().getAmount());
        assertTrue(owner.getLocation().distance(new Location(world, 100.5, 70, 100.5)) > 10);
    }

    @Test
    void heldItemTagsRecogniseOtherPluginsItems() {
        assertEquals("dragon_ball", HeldItems.tag(dragonBall(), items));
        ItemStack katana = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = katana.getItemMeta();
        var cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("katana"));
        meta.setCustomModelDataComponent(cmd);
        katana.setItemMeta(meta);
        assertEquals("katana", HeldItems.tag(katana, items));
        assertEquals("netherite", HeldItems.tag(new ItemStack(Material.NETHERITE_SWORD), items));
        assertEquals("cherry", HeldItems.tag(new ItemStack(Material.CHERRY_SAPLING), items));
        assertNull(HeldItems.tag(new ItemStack(Material.DIRT), items));
        assertFalse("collectible".equals(HeldItems.tag(new ItemStack(Material.PLAYER_HEAD), items)));
        assertEquals("collectible", HeldItems.tag(Heads.COLLECTION.getFirst().item(), items));
    }
}
