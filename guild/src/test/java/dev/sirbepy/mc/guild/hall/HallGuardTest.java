package dev.sirbepy.mc.guild.hall;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.Place;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wolf;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HallGuardTest extends GuildTestBase {

    private HallGuard guard;
    private Block inside;
    private Block outside;

    @BeforeEach
    void setUpGuard() {
        guard = new HallGuard(store);
        world.getChunkAt(0, 0);
        inside = world.getBlockAt(5, 64, 5);
        inside.setType(Material.SPRUCE_PLANKS);
        outside = world.getBlockAt(40, 64, 40);
        outside.setType(Material.STONE);
    }

    private void guardBox() {
        store.setGuard(Place.Box.of("world", 10, 80, 10, 0, 50, 0));
    }

    @Test
    void withNoBoxSetNothingIsGuarded() {
        BlockBreakEvent event = new BlockBreakEvent(inside, alice);
        guard.onBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void nobodyButAnOpBreaksTheHall() {
        guardBox();
        BlockBreakEvent event = new BlockBreakEvent(inside, alice);
        guard.onBreak(event);
        assertTrue(event.isCancelled());

        gm.setOp(true);
        BlockBreakEvent byOp = new BlockBreakEvent(inside, gm);
        guard.onBreak(byOp);
        assertFalse(byOp.isCancelled());
    }

    @Test
    void outsideTheBoxNothingIsGuarded() {
        guardBox();
        BlockBreakEvent event = new BlockBreakEvent(outside, alice);
        guard.onBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void placingInsideIsRefused() {
        guardBox();
        Block above = inside.getRelative(0, 1, 0);
        BlockPlaceEvent event = new BlockPlaceEvent(above, above.getState(), inside, new ItemStack(Material.DIRT), alice, true,
                EquipmentSlot.HAND);
        guard.onPlace(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void explosionsSpareTheHall() {
        guardBox();
        List<Block> blocks = new ArrayList<>(List.of(inside, outside));
        EntityExplodeEvent event = new EntityExplodeEvent(alice, new Location(world, 5, 64, 5), blocks, 1f,
                org.bukkit.ExplosionResult.DESTROY);
        guard.onEntityExplode(event);
        assertEquals(List.of(outside), event.blockList());
    }

    @Test
    void theBoxSurvivesASave() {
        guardBox();
        store.save();
        assertEquals(store.guard(), newStore().guard());
    }

    @Test
    void aTamedPetsOwnerStandsInForTheGuard() {
        guardBox();
        gm.setOp(true);
        Wolf wolf = world.spawn(new Location(world, 5, 64, 5), Wolf.class);
        wolf.setOwner(gm);
        ArmorStand stand = (ArmorStand) world.spawnEntity(new Location(world, 5, 64, 5), EntityType.ARMOR_STAND);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(wolf, stand, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(wolf).build(), 1);
        guard.onDecorDamage(event);
        assertFalse(event.isCancelled());
    }
}
