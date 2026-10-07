package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CousinListenerTest extends CartelTestBase {

    private CousinListener listener;

    @BeforeEach
    void setUp() {
        listener = new CousinListener(calls, cousins, trophies);
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

    private Arrow arrowFrom(org.bukkit.entity.LivingEntity shooter) {
        Arrow arrow = world.spawn(shooter.getLocation().add(0, 1.5, 0), Arrow.class);
        arrow.setShooter(shooter);
        return arrow;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static EntityDamageByEntityEvent shot(Arrow arrow, org.bukkit.entity.Entity victim, double damage) {
        return new EntityDamageByEntityEvent(arrow, victim, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity((org.bukkit.entity.Entity) arrow.getShooter()).build(),
                damage);
    }

    @Test
    void aHitsBoltsHurtTheTargetAtFullDamageAndNobodyElse() {
        calls.hit(owner, victim);
        Arrow bolt = arrowFrom(calls.cousins().get(1));

        EntityDamageByEntityEvent onTarget = shot(bolt, victim, 12.0);
        listener.onShot(onTarget);
        assertFalse(onTarget.isCancelled());
        assertEquals(12.0, onTarget.getDamage(), "no cap: Power V's damage stands");

        PlayerMock bystander = server.addPlayer("Bystander");
        EntityDamageByEntityEvent onBystander = shot(bolt, bystander, 8.0);
        listener.onShot(onBystander);
        assertTrue(onBystander.isCancelled());
    }

    @Test
    void guardBoltsHurtMonstersButNeitherAnInnocentPlayerNorTheOtherCousin() {
        calls.guard(owner);
        Mob leonel = calls.cousins().get(0);
        Mob marco = calls.cousins().get(1);
        Arrow bolt = arrowFrom(marco);
        Zombie monster = world.spawn(new Location(world, 4, FLOOR_Y, 4), Zombie.class);

        EntityDamageByEntityEvent onMonster = shot(bolt, monster, 8.0);
        listener.onShot(onMonster);
        assertFalse(onMonster.isCancelled());

        EntityDamageByEntityEvent onVictim = shot(bolt, victim, 8.0);
        listener.onShot(onVictim);
        assertTrue(onVictim.isCancelled(), "a player who did nothing is left alone");

        EntityDamageByEntityEvent onLeonel = shot(bolt, leonel, 8.0);
        listener.onFriendlyFire(onLeonel);
        assertTrue(onLeonel.isCancelled());

        EntityDamageByEntityEvent onBoss = shot(bolt, owner, 8.0);
        listener.onFriendlyFire(onBoss);
        assertTrue(onBoss.isCancelled());
    }

    @Test
    void boltsNeitherKnockBackNorSetOnFire() {
        calls.hit(owner, victim);
        Arrow arrow = arrowFrom(calls.cousins().get(1));

        EntityPushedByEntityAttackEvent push = new EntityPushedByEntityAttackEvent(victim,
                EntityKnockbackEvent.Cause.ENTITY_ATTACK, arrow, new Vector(0.4, 0.4, 0));
        listener.onShotKnockback(push);
        assertTrue(push.isCancelled());

        EntityCombustByEntityEvent burn = new EntityCombustByEntityEvent(arrow, victim, 5f);
        listener.onShotFire(burn);
        assertTrue(burn.isCancelled());
    }

    @Test
    void someoneElsesArrowIsUntouched() {
        calls.hit(owner, victim);
        Arrow arrow = arrowFrom(owner);

        EntityDamageByEntityEvent damage = shot(arrow, victim, 6.0);
        listener.onShot(damage);
        assertEquals(6.0, damage.getDamage());

        EntityPushedByEntityAttackEvent push = new EntityPushedByEntityAttackEvent(victim,
                EntityKnockbackEvent.Cause.ENTITY_ATTACK, arrow, new Vector(0.4, 0.4, 0));
        listener.onShotKnockback(push);
        assertFalse(push.isCancelled());
    }

    @Test
    void hurtingTheGuardedOwnerMakesTheAttackerATarget() {
        calls.guard(owner);
        Mob cousin = calls.cousins().getFirst();

        listener.onHurt(hit(victim, owner));

        assertTrue(calls.allowedTarget(cousin, victim));
    }
}
