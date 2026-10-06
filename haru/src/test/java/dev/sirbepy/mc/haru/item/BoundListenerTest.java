package dev.sirbepy.mc.haru.item;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.effect.OwnerEffectsListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundListenerTest extends HaruTestBase {

    private BoundListener listener;
    private PlayerMock other;

    @BeforeEach
    void setUp() {
        listener = new BoundListener(ctx);
        other = server.addPlayer("Other");
    }

    private ItemStack boundLight() {
        return ctx.bound().bind(new ItemStack(Material.LIGHT, 16), owner.getUniqueId(), owner.getName());
    }

    @Test
    void othersCannotPickUpABoundItemButItsOwnerCan() {
        Item dropped = world.dropItem(new Location(world, 0, 64, 0), boundLight());
        EntityPickupItemEvent byOther = new EntityPickupItemEvent(other, dropped, 0);
        listener.onPickup(byOther);
        assertTrue(byOther.isCancelled());
        EntityPickupItemEvent byOwner = new EntityPickupItemEvent(owner, dropped, 0);
        listener.onPickup(byOwner);
        assertFalse(byOwner.isCancelled());
    }

    @Test
    void aBoundItemInSomeoneElsesHandsGoesBackToHaruForItsOwner() {
        other.getInventory().addItem(boundLight());
        other.getInventory().addItem(new ItemStack(Material.DIRT, 3));

        listener.sweep(List.of(owner, other));

        assertEquals(0, count(other, Material.LIGHT));
        assertEquals(3, count(other, Material.DIRT));
        assertEquals(1, state().lostAndFound().size());
        assertTrue(listener.returnLostItems(owner, state()));
        assertEquals(16, count(owner, Material.LIGHT));
        assertTrue(state().lostAndFound().isEmpty());
    }

    @Test
    void lostItemsSurviveARestart() {
        other.getInventory().addItem(boundLight());
        listener.sweep(List.of(other));
        var reloaded = new dev.sirbepy.mc.haru.data.HaruStore(new java.io.File(dataDir.toFile(), "data.yml"), plugin.getLogger());
        var lost = reloaded.state(owner.getUniqueId()).lostAndFound();
        assertEquals(1, lost.size());
        assertEquals(owner.getUniqueId(), ctx.bound().ownerOf(lost.getFirst()));
    }

    @Test
    void theOwnersOwnBoundItemsStayPut() {
        owner.getInventory().addItem(boundLight());
        listener.sweep(List.of(owner));
        assertEquals(16, count(owner, Material.LIGHT));
        assertFalse(listener.returnLostItems(owner, state()));
    }

    @Test
    void ownerDeathShutsTheShopForADayOthersDeathsDont() {
        OwnerEffectsListener effects = new OwnerEffectsListener(ctx);
        effects.onDeath(death(other));
        assertFalse(state().closed(clock.nowMillis));
        effects.onDeath(death(owner));
        assertTrue(state().closed(clock.nowMillis));
        assertFalse(state().closed(clock.nowMillis + OwnerEffectsListener.CLOSED_MILLIS + 1));
    }

    @Test
    void aRebirthPetalBoundToSomeoneElseDoesNothing() {
        ItemStack petal = ctx.bound().bind(ctx.items().rebirthPetal(), owner.getUniqueId(), owner.getName());
        PlayerDeathEvent event = new PlayerDeathEvent(other,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                new ArrayList<>(List.of(petal, new ItemStack(Material.DIAMOND))), 0, 0, 0, 0, Component.text("x"), false);
        new HaruItemListener(ctx).onDeath(event);
        assertEquals(2, event.getDrops().size());
        assertTrue(event.getItemsToKeep().isEmpty());
    }

    private PlayerDeathEvent death(PlayerMock player) {
        return new PlayerDeathEvent(player, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                new ArrayList<>(), 0, 0, 0, 0, Component.text("x"), false);
    }
}
