package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.Levels;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sharpshooter's 11 nodes, Volley, Arrow Rain and Railshot. Every damage assertion includes Steady
 * Aim's +10% at level 25 ({@code RpgClass.passivePercent}), applied separately by {@code ClassCombat}.
 */
class RangerArrowsTest extends KitTestBase {

    private static final double STEADY_AIM_25 = 1.10; // RpgClass.RANGER.passivePercent(25) == 10%

    private PlayerMock ranger(int level) {
        return player(RpgClass.RANGER, level);
    }

    private Arrow shoot(PlayerMock p, double x, float force) {
        Arrow arrow = world.spawn(at(x), Arrow.class);
        arrow.setShooter(p);
        server.getPluginManager().callEvent(new EntityShootBowEvent(p, new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW), arrow, EquipmentSlot.HAND, force, true));
        return arrow;
    }

    private double hitWith(AbstractArrow arrow, Zombie z) {
        return hit(arrow, z, DamageCause.PROJECTILE, 10);
    }

    /** Spawns a trident, sets its shooter and fires the launch event the real plugin reacts to. */
    private Trident throwTrident(PlayerMock p, double x) {
        Trident trident = world.spawn(at(x), Trident.class);
        trident.setShooter(p);
        trident.setVelocity(new org.bukkit.util.Vector(2, 0, 0));
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(trident));
        return trident;
    }

    @Test
    void talentShapeIsThirtyFourNodesWithSharpshooterCarryingTheExtraHomecomingNode() {
        List<dev.sirbepy.mc.classes.talent.Talent> all = engine.talents().all(RpgClass.RANGER);
        assertEquals(34, all.size());
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var t : all) assertTrue(ids.add(t.id()), "duplicate id " + t.id());
        for (int branch = 0; branch < 3; branch++) {
            java.util.Map<Integer, Long> perTier = new java.util.TreeMap<>();
            for (var t : all) {
                if (t.branch() == branch) perTier.merge(t.tier(), 1L, Long::sum);
            }
            // Sharpshooter (branch 0) carries Homecoming as a third tier-4 node.
            java.util.Map<Integer, Long> expected = branch == 0
                    ? java.util.Map.of(1, 2L, 2, 3L, 3, 3L, 4, 3L, 5, 1L)
                    : java.util.Map.of(1, 2L, 2, 3L, 3, 3L, 4, 2L, 5, 1L);
            assertEquals(expected, perTier, "branch " + branch);
        }
    }

    @Test
    void longShotBonusOnlyAppliesToChargedShotsBeyondFifteenBlocks() {
        PlayerMock p = ranger(25);
        rank(p, "long_shot", 2);
        Zombie far = zombie(20);
        Arrow charged = shoot(p, 0, 1.0f);
        double expected = 10 * STEADY_AIM_25 * (1 + 0.08); // rank 2 = +8%
        assertEquals(expected, hitWith(charged, far), 0.001);

        Zombie near = zombie(5);
        Arrow uncharged = shoot(p, 0, 0.5f);
        assertEquals(10 * STEADY_AIM_25, hitWith(uncharged, near), 0.001, "no bonus: not charged and not far");
    }

    @Test
    void eagleEyeGlowsAChargedHitMobForYouOnly() {
        PlayerMock p = ranger(25);
        rank(p, "eagle_eye", 1);
        Zombie z = zombie(5);
        Arrow charged = shoot(p, 0, 1.0f);
        hitWith(charged, z);
        assertTrue(z.isGlowing());
        advance(5 * 20 + 1);
        assertFalse(z.isGlowing());
    }

    @Test
    void piercingShotSetsPierceLevelOnlyOnAFullyChargedArrow() {
        PlayerMock p = ranger(25);
        rank(p, "piercing_shot", 2);
        Arrow charged = shoot(p, 0, 1.0f);
        assertEquals(2, charged.getPierceLevel());
        Arrow weak = shoot(p, 1, 0.3f);
        assertEquals(0, weak.getPierceLevel());
    }

    @Test
    void calledShotOnlyBonusesTheHitThatFindsAFullHealthMob() {
        PlayerMock p = ranger(25);
        rank(p, "called_shot", 3);
        Zombie z = zombie(5);
        Arrow first = shoot(p, 0, 0.3f);
        assertEquals(10 * STEADY_AIM_25 * 1.30, hitWith(first, z), 0.001);
        z.setHealth(Math.max(1, z.getHealth() - 1));
        Arrow second = shoot(p, 0, 0.3f);
        assertEquals(10 * STEADY_AIM_25, hitWith(second, z), 0.001, "no longer full health");
    }

    @Test
    void recoveryReturnsAnArrowOnlyWhenTheRollLandsUnderItsChance() {
        PlayerMock p = ranger(25);
        rank(p, "recovery", 1);
        Zombie z = zombie(5);
        roll = 0.1; // under 20%
        hitWith(shoot(p, 0, 0.3f), z);
        assertEquals(1, p.getInventory().all(Material.ARROW).size());
        roll = 0.9; // over 20%
        p.getInventory().clear();
        hitWith(shoot(p, 0, 0.3f), z);
        assertTrue(p.getInventory().all(Material.ARROW).isEmpty());
    }

    @Test
    void markedAddsTenPercentOnlyAfterEagleEyeIsGlowingTheMob() {
        PlayerMock p = ranger(25);
        rank(p, "eagle_eye", 1);
        rank(p, "marked", 1);
        Zombie z = zombie(5);
        Arrow first = shoot(p, 0, 1.0f);
        double firstHit = hitWith(first, z); // glow starts only after this hit resolves
        assertEquals(10 * STEADY_AIM_25, firstHit, 0.001);
        Arrow second = shoot(p, 0, 0.3f);
        assertEquals(10 * STEADY_AIM_25 * 1.10, hitWith(second, z), 0.001, "now glowing from Eagle Eye");
    }

    @Test
    void deadeyeStacksBackToBackHitsOnTheSameMobUpToItsCap() {
        PlayerMock p = ranger(25);
        rank(p, "deadeye", 1); // cap = rank + 2 = 3 stacks
        Zombie z = zombie(5);
        assertEquals(10 * STEADY_AIM_25, hitWith(shoot(p, 0, 0.3f), z), 0.001, "1st hit: no stack yet");
        assertEquals(10 * STEADY_AIM_25 * 1.05, hitWith(shoot(p, 0, 0.3f), z), 0.001, "2nd hit: +5%");
        assertEquals(10 * STEADY_AIM_25 * 1.10, hitWith(shoot(p, 0, 0.3f), z), 0.001, "3rd hit: +10%, capped");
        assertEquals(10 * STEADY_AIM_25 * 1.10, hitWith(shoot(p, 0, 0.3f), z), 0.001, "4th hit: still capped at 3 stacks");
    }

    @Test
    void windReaderSpeedsUpTheArrowOnlyWhenLearned() {
        PlayerMock p = ranger(25);
        Arrow base = shoot(p, 0, 0.5f);
        double baseSpeed = base.getVelocity().length();
        rank(p, "wind_reader", 2);
        Arrow faster = shoot(p, 0, 0.5f);
        assertEquals(baseSpeed * 1.2, faster.getVelocity().length(), 0.01);
    }

    @Test
    void ricochetFiresAMobOnlyArrowAtTheNearestMobOnAKill() {
        PlayerMock p = ranger(25);
        rank(p, "ricochet", 1);
        Zombie dead = zombie(0);
        Zombie other = zombie(3);
        Arrow killer = shoot(p, 0, 0.3f);
        int arrowsBefore = world.getEntitiesByClass(Arrow.class).size();
        EntityDamageByEntityEvent cause = new EntityDamageByEntityEvent(killer, dead, DamageCause.PROJECTILE,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).build(), 20);
        dead.setLastDamageCause(cause);
        server.getPluginManager().callEvent(new EntityDeathEvent(dead, cause.getDamageSource(), new java.util.ArrayList<>()));
        assertTrue(world.getEntitiesByClass(Arrow.class).size() > arrowsBefore);
        assertFalse(other.isDead());
    }

    @Test
    void snipersNestOnlyBonusesAfterStandingStillAndSneakingAFullSecond() {
        PlayerMock p = ranger(25);
        rank(p, "snipers_nest", 1);
        p.setSneaking(true);
        Zombie z = zombie(5);
        assertEquals(10 * STEADY_AIM_25, hitWith(shoot(p, 0, 0.3f), z), 0.001, "just started standing still");
        now += 1_100;
        assertEquals(10 * STEADY_AIM_25 * 1.15, hitWith(shoot(p, 0, 0.3f), z), 0.001);
    }

    @Test
    void mobOnlyArrowsNeverDamageAPlayer() {
        PlayerMock p = ranger(25);
        rank(p, "ricochet", 1);
        Zombie dead = zombie(0);
        zombie(3);
        Arrow killer = shoot(p, 0, 0.3f);
        EntityDamageByEntityEvent cause = new EntityDamageByEntityEvent(killer, dead, DamageCause.PROJECTILE,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).build(), 20);
        dead.setLastDamageCause(cause);
        server.getPluginManager().callEvent(new EntityDeathEvent(dead, cause.getDamageSource(), new java.util.ArrayList<>()));
        Arrow mobOnly = world.getEntitiesByClass(Arrow.class).stream()
                .filter(a -> a != killer).findFirst().orElseThrow();
        PlayerMock victim = server.addPlayer();
        assertEquals(0, hit(mobOnly, victim, DamageCause.PROJECTILE, 5), "a mob-only arrow never hurts a player");
    }

    @Test
    void volleyArmsTheNextShotIntoAFiveArrowFan() {
        PlayerMock p = ranger(10);
        assertTrue(cast(p, Slot.TWO));
        int before = world.getEntitiesByClass(Arrow.class).size();
        shoot(p, 0, 1.0f);
        assertEquals(before + 1 + 4, world.getEntitiesByClass(Arrow.class).size(), "the real shot plus 4 extras");
    }

    @Test
    void volleyDoesNotArmASecondShotAfterFiveSeconds() {
        PlayerMock p = ranger(10);
        assertTrue(cast(p, Slot.TWO));
        advance(5 * 20 + 1);
        int before = world.getEntitiesByClass(Arrow.class).size();
        shoot(p, 0, 1.0f);
        assertEquals(before + 1, world.getEntitiesByClass(Arrow.class).size(), "the window already closed");
    }

    @Test
    void arrowRainDealsRepeatedDamageOnACircleAroundTheLookDirection() {
        PlayerMock p = ranger(25);
        roll = 0; // deterministic: every drop lands exactly on the look-direction centre
        Location center = p.getEyeLocation().add(p.getEyeLocation().getDirection().normalize().multiply(40));
        Zombie z = world.spawn(center, Zombie.class);
        assertTrue(cast(p, Slot.ULTIMATE));
        advance(31); // 3 ticks of the ability's 10-tick repeat
        assertEquals(20 - 3 * 3, z.getHealth(), 0.01);
    }

    // Tridents: every throw counts as a fully charged shot.

    @Test
    void aThrownTridentGetsLongShotLikeAFullyChargedArrow() {
        PlayerMock p = ranger(25);
        rank(p, "long_shot", 2);
        Zombie far = zombie(20);
        assertEquals(10 * STEADY_AIM_25 * 1.08, hitWith(throwTrident(p, 0), far), 0.001);
    }

    @Test
    void aThrownTridentGetsPiercingShotAndWindReader() {
        PlayerMock p = ranger(25);
        assertEquals(0, throwTrident(p, 0).getPierceLevel(), "nothing learned yet");
        double baseSpeed = throwTrident(p, 0).getVelocity().length();
        rank(p, "piercing_shot", 2);
        rank(p, "wind_reader", 2);
        Trident t = throwTrident(p, 0);
        assertEquals(2, t.getPierceLevel());
        assertEquals(baseSpeed * 1.2, t.getVelocity().length(), 0.01);
    }

    @Test
    void aThrownTridentHitGlowsTheMobForEagleEyeAndMarked() {
        PlayerMock p = ranger(25);
        rank(p, "eagle_eye", 1);
        rank(p, "marked", 1);
        Zombie z = zombie(5);
        hitWith(throwTrident(p, 0), z);
        assertTrue(z.isGlowing());
        assertEquals(10 * STEADY_AIM_25 * 1.10, hitWith(throwTrident(p, 0), z), 0.001, "Marked on the next throw");
    }

    @Test
    void aThrownTridentHitFillsFocusLikeAFullyChargedShot() {
        PlayerMock p = ranger(25);
        Zombie z = zombie(5);
        double before = resource(p);
        hitWith(throwTrident(p, 0), z);
        assertEquals(before + 15, resource(p), 0.001);
    }

    @Test
    void volleyArmsTheNextTridentThrowToo() {
        PlayerMock p = ranger(10);
        assertTrue(cast(p, Slot.TWO));
        int beforeArrows = world.getEntitiesByClass(Arrow.class).size();
        int beforeTridents = world.getEntitiesByClass(Trident.class).size();
        throwTrident(p, 0);
        assertEquals(beforeArrows, world.getEntitiesByClass(Arrow.class).size(),
                "a trident throw's fan must not spawn arrows");
        assertEquals(beforeTridents + 1 + 4, world.getEntitiesByClass(Trident.class).size(),
                "the real throw plus 4 fan tridents");
    }

    @Test
    void volleyFanTridentsIgnoreTheirOwnProjectileLaunchEvent() {
        // On a real server world.spawn(Trident.class, ...) fires ProjectileLaunchEvent for the fan
        // tridents too; onThrowTrident must not re-apply Wind Reader/Piercing Shot to them on top of
        // the velocity the fan already computed from the real throw.
        PlayerMock p = ranger(10);
        rank(p, "wind_reader", 2);
        assertTrue(cast(p, Slot.TWO));
        Trident real = throwTrident(p, 0);
        Trident fan = world.getEntitiesByClass(Trident.class).stream()
                .filter(t -> t != real).findFirst().orElseThrow();
        double fanSpeedBefore = fan.getVelocity().length();
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(fan));
        assertEquals(fanSpeedBefore, fan.getVelocity().length(), 0.0001,
                "a fan trident's own launch event must not reapply Wind Reader again");
    }

    @Test
    void aThrownTridentNeverTriggersRecoveryArrows() {
        PlayerMock p = ranger(25);
        rank(p, "recovery", 2);
        roll = 0;
        hitWith(throwTrident(p, 0), zombie(5));
        assertTrue(p.getInventory().all(Material.ARROW).isEmpty());
    }

    @Test
    void anUnclassedTridentThrowIsNotTreatedAsCharged() {
        PlayerMock p = server.addPlayer();
        Zombie far = zombie(20);
        assertEquals(10, hitWith(throwTrident(p, 0), far), 0.001);
    }

    // Homecoming: a ranged kill's drops and XP go straight to the shooter.

    private EntityDeathEvent killWith(AbstractArrow projectile, Zombie dead, List<ItemStack> drops, int exp) {
        EntityDamageByEntityEvent cause = new EntityDamageByEntityEvent(projectile, dead, DamageCause.PROJECTILE,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.TRIDENT).build(), 20);
        dead.setLastDamageCause(cause);
        EntityDeathEvent death = new EntityDeathEvent(dead, cause.getDamageSource(), new java.util.ArrayList<>(drops), exp);
        server.getPluginManager().callEvent(death);
        return death;
    }

    @Test
    void homecomingSendsATridentKillsDropsAndXpToTheThrower() {
        PlayerMock p = ranger(25);
        rank(p, "homecoming", 1);
        Zombie dead = zombie(30);
        int expBefore = p.getTotalExperience();
        EntityDeathEvent death = killWith(throwTrident(p, 0), dead, List.of(new ItemStack(Material.ROTTEN_FLESH, 2)), 5);
        assertTrue(death.getDrops().isEmpty(), "nothing left on the ground where it died");
        assertEquals(0, death.getDroppedExp());
        assertTrue(p.getInventory().containsAtLeast(new ItemStack(Material.ROTTEN_FLESH), 2));
        assertEquals(expBefore + 5, p.getTotalExperience());
    }

    @Test
    void homecomingWorksForArrowKillsToo() {
        PlayerMock p = ranger(25);
        rank(p, "homecoming", 1);
        EntityDeathEvent death = killWith(shoot(p, 0, 0.3f), zombie(30), List.of(new ItemStack(Material.BONE)), 0);
        assertTrue(death.getDrops().isEmpty());
        assertTrue(p.getInventory().contains(Material.BONE));
    }

    @Test
    void withoutHomecomingDropsStayWhereTheMobDied() {
        PlayerMock p = ranger(25);
        EntityDeathEvent death = killWith(throwTrident(p, 0), zombie(30), List.of(new ItemStack(Material.ROTTEN_FLESH)), 5);
        assertEquals(1, death.getDrops().size());
        assertEquals(5, death.getDroppedExp());
        assertFalse(p.getInventory().contains(Material.ROTTEN_FLESH));
    }

    /**
     * Homecoming runs at HIGHEST so it reads whatever an earlier, lower-priority plugin left in
     * event.getDrops(): it must redistribute that final list exactly once, neither reverting a
     * NORMAL-priority plugin's edit nor leaving a second copy behind.
     */
    @Test
    void homecomingSeesALowerPriorityPluginsEditAndRedistributesItOnce() {
        PlayerMock p = ranger(25);
        rank(p, "homecoming", 1);
        org.bukkit.event.Listener otherPlugin = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.NORMAL)
            public void onDeath(EntityDeathEvent event) {
                event.getDrops().clear();
                event.getDrops().add(new ItemStack(Material.BONE, 3));
            }
        };
        server.getPluginManager().registerEvents(otherPlugin, plugin);

        EntityDeathEvent death = killWith(throwTrident(p, 0), zombie(30), List.of(new ItemStack(Material.ROTTEN_FLESH)), 0);

        assertTrue(death.getDrops().isEmpty(), "Homecoming claims the final drop list, not the original one");
        assertTrue(p.getInventory().containsAtLeast(new ItemStack(Material.BONE), 3), "the other plugin's bones, not the rotten flesh it replaced");
        assertFalse(p.getInventory().contains(Material.ROTTEN_FLESH), "original drop was already replaced before Homecoming ran");
    }

    @Test
    void railshotHitsEveryMobOnTheBeamAndIgnoresArmour() {
        PlayerMock p = ranger(25);
        Location center = p.getEyeLocation().add(p.getEyeLocation().getDirection().normalize().multiply(10));
        Zombie onBeam = world.spawn(center, Zombie.class);
        Zombie offBeam = world.spawn(center.clone().add(10, 0, 0), Zombie.class);
        engine.talents().force(p.getUniqueId(), RpgClass.RANGER, "railshot", 1);
        assertTrue(cast(p, Slot.TWO)); // Railshot replaces Volley's slot
        assertEquals(8, onBeam.getHealth(), 0.01);
        assertEquals(20, offBeam.getHealth(), 0.01, "well off the line");
    }
}
