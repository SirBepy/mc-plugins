package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CousinListenerTest extends CartelTestBase {

    private CousinListener listener;

    @BeforeEach
    void setUp() {
        listener = new CousinListener(calls, cousins);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static EntityDamageByEntityEvent hit(org.bukkit.entity.Entity damager, org.bukkit.entity.Entity victim) {
        return new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(damager).withCausingEntity(damager).build(), 5.0);
    }

    @Test
    void aDeadCousinDropsNothing() {
        calls.hit(owner, victim);
        Mob cousin = calls.cousins().getFirst();
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.NETHERITE_SWORD), new ItemStack(Material.IRON_HELMET)));
        EntityDeathEvent event = new EntityDeathEvent(cousin, DamageSource.builder(DamageType.GENERIC).build(), drops, 5);

        listener.onDeath(event);

        assertTrue(event.getDrops().isEmpty());
        assertEquals(0, event.getDroppedExp());
    }

    @Test
    void anOrdinaryZombieStillDropsItsLoot() {
        Zombie plain = world.spawn(new Location(world, 3, FLOOR_Y, 3), Zombie.class);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.ROTTEN_FLESH)));
        EntityDeathEvent event = new EntityDeathEvent(plain, DamageSource.builder(DamageType.GENERIC).build(), drops, 5);

        listener.onDeath(event);

        assertEquals(1, event.getDrops().size());
    }

    @Test
    void theCousinsNeverHurtTheirBossOrEachOtherAndTheBossCantHurtThem() {
        calls.guard(owner);
        Mob leonel = calls.cousins().get(0);
        Mob marco = calls.cousins().get(1);

        EntityDamageByEntityEvent onBoss = hit(leonel, owner);
        listener.onFriendlyFire(onBoss);
        assertTrue(onBoss.isCancelled());

        EntityDamageByEntityEvent onCousin = hit(leonel, marco);
        listener.onFriendlyFire(onCousin);
        assertTrue(onCousin.isCancelled());

        EntityDamageByEntityEvent byBoss = hit(owner, marco);
        listener.onFriendlyFire(byBoss);
        assertTrue(byBoss.isCancelled());

        EntityDamageByEntityEvent onVictim = hit(leonel, victim);
        listener.onFriendlyFire(onVictim);
        assertFalse(onVictim.isCancelled());
    }

    @Test
    void aCousinCantBeTurnedOnSomeoneElse() {
        calls.hit(owner, victim);
        Mob cousin = calls.cousins().getFirst();

        EntityTargetEvent onOwner = new EntityTargetEvent(cousin, owner, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        listener.onTarget(onOwner);
        assertTrue(onOwner.isCancelled());

        EntityTargetEvent onVictim = new EntityTargetEvent(cousin, victim, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        listener.onTarget(onVictim);
        assertFalse(onVictim.isCancelled());
    }

    @Test
    void hurtingTheGuardedOwnerMakesTheAttackerATarget() {
        calls.guard(owner);
        Mob cousin = calls.cousins().getFirst();

        listener.onHurt(hit(victim, owner));

        assertTrue(calls.allowedTarget(cousin, victim));
    }
}
