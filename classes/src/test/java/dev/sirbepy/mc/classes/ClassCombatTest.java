package dev.sirbepy.mc.classes;

import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassCombatTest extends MockClassesTest {

    @Test
    void aPlayerWithNoClassHitsExactlyAsToday() {
        PlayerMock plain = server.addPlayer("Plain");
        assertEquals(10, hit(plain, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10, hit(arrowFrom(plain), zombie(), DamageCause.PROJECTILE), 1e-9);
        assertEquals(10, hit(zombie(), plain, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void weaponMasterAddsMeleeDamageAndCountsHalfAgainstPlayers() {
        PlayerMock warrior = server.addPlayer("Warrior");
        PlayerMock other = server.addPlayer("Other");
        pick(warrior, RpgClass.WARRIOR, 1);
        assertEquals(10.2, hit(warrior, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        pick(warrior, RpgClass.WARRIOR, 25);
        assertEquals(11.0, hit(warrior, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(11.0, hit(warrior, zombie(), DamageCause.ENTITY_SWEEP_ATTACK), 1e-9);
        assertEquals(10.5, hit(warrior, other, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void heavyHandsCutsTheWarriorsArrows() {
        PlayerMock warrior = server.addPlayer("Warrior");
        pick(warrior, RpgClass.WARRIOR, 10);
        assertEquals(9.0, hit(arrowFrom(warrior), zombie(), DamageCause.PROJECTILE), 1e-9);
        assertEquals(9.5, hit(arrowFrom(warrior), server.addPlayer("Other"), DamageCause.PROJECTILE), 1e-9);
    }

    @Test
    void steadyAimAddsArrowDamageButNotMelee() {
        PlayerMock ranger = server.addPlayer("Ranger");
        pick(ranger, RpgClass.RANGER, 25);
        assertEquals(11.0, hit(arrowFrom(ranger), zombie(), DamageCause.PROJECTILE), 1e-9);
        assertEquals(10.0, hit(ranger, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10.5, hit(arrowFrom(ranger), server.addPlayer("Other"), DamageCause.PROJECTILE), 1e-9);
    }

    @Test
    void thinSkinOnlyCountsMobsMeleeHits() {
        PlayerMock ranger = server.addPlayer("Ranger");
        pick(ranger, RpgClass.RANGER, 1);
        Zombie zombie = zombie();
        assertEquals(10.5, hit(zombie, ranger, DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10.0, hit(server.addPlayer("Other"), ranger, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void softHandsCutsTheArtificersMelee() {
        PlayerMock artificer = server.addPlayer("Artificer");
        pick(artificer, RpgClass.ARTIFICER, 25);
        assertEquals(9.5, hit(artificer, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(9.75, hit(artificer, server.addPlayer("Other"), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10.0, hit(arrowFrom(artificer), zombie(), DamageCause.PROJECTILE), 1e-9);
    }

    @Test
    void secondaryClassAddsHalfItsPassiveAlongsideTheMainClass() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.RANGER, 1);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.WARRIOR);
        classes.setXp(dual.getUniqueId(), RpgClass.WARRIOR, Levels.totalFor(25));
        // Ranger contributes nothing on melee; secondary Warrior's +10% passive halves and floors to 5%.
        assertEquals(10.5, hit(dual, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void secondaryClassWeaknessIsFullStrengthNotHalved() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.WARRIOR, 25);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.ARTIFICER);
        // Warrior's own +10% melee passive, then Artificer's full (unhalved) -5% Soft Hands weakness.
        assertEquals(10.5, hit(dual, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void secondaryClassNeverCountsWithoutAnActiveMainClass() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.WARRIOR, 25);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.ARTIFICER);
        picker.leave(dual);
        assertEquals(10.0, hit(dual, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void secondaryRangerAddsFullThinSkinWeaknessToo() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.WARRIOR, 1);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.RANGER);
        assertEquals(10.5, hit(zombie(), dual, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void battleCryCutsDamageTakenAndHalfAgainstPlayers() {
        PlayerMock warrior = server.addPlayer("Warrior");
        pick(warrior, RpgClass.WARRIOR, 25);
        abilities.use(warrior);
        assertEquals(8.0, hit(zombie(), warrior, DamageCause.ENTITY_ATTACK), 1e-9);
        PlayerMock other = server.addPlayer("Other");
        assertEquals(9.0, hit(other, warrior, DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(9.0, hit(arrowFrom(other), warrior, DamageCause.PROJECTILE), 1e-9);
        now += 10_000;
        assertEquals(10.0, hit(zombie(), warrior, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void stalwartCutsMobsMeleeHitsOnTheGuardian() {
        PlayerMock guardian = server.addPlayer("Guardian");
        pick(guardian, RpgClass.GUARDIAN, 25);
        assertEquals(9.0, hit(zombie(), guardian, DamageCause.ENTITY_ATTACK), 1e-9);
        // Projectiles and player hits aren't "mobs' melee hits".
        assertEquals(10.0, hit(arrowFrom(server.addPlayer("Shooter")), guardian, DamageCause.PROJECTILE), 1e-9);
        assertEquals(10.0, hit(server.addPlayer("Other"), guardian, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void lastStandCutsDamageTakenAndHalfAgainstPlayers() {
        PlayerMock guardian = server.addPlayer("Guardian");
        pick(guardian, RpgClass.GUARDIAN, 25);
        abilities.use(guardian);
        // Stalwart's 10% and Last Stand's 20% both apply.
        assertEquals(7.0, hit(zombie(), guardian, DamageCause.ENTITY_ATTACK), 1e-9);
        PlayerMock other = server.addPlayer("Other");
        assertEquals(9.0, hit(other, guardian, DamageCause.ENTITY_ATTACK), 1e-9);
        now += 10_000;
        assertEquals(9.0, hit(zombie(), guardian, DamageCause.ENTITY_ATTACK), 1e-9);
    }

    @Test
    void sneakAttackOnlyAddsMeleeDamageWhileSneaking() {
        PlayerMock rogue = server.addPlayer("Rogue");
        pick(rogue, RpgClass.ROGUE, 25);
        assertEquals(10.0, hit(rogue, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        rogue.setSneaking(true);
        assertEquals(11.0, hit(rogue, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10.5, hit(rogue, server.addPlayer("Other"), DamageCause.ENTITY_ATTACK), 1e-9);
        // Not melee: Sneak Attack doesn't touch arrows.
        assertEquals(10.0, hit(arrowFrom(rogue), zombie(), DamageCause.PROJECTILE), 1e-9);
    }

    @Test
    void lightFingersOnlyCountsMobsRangedHits() {
        PlayerMock rogue = server.addPlayer("Rogue");
        pick(rogue, RpgClass.ROGUE, 1);
        Zombie zombie = zombie();
        assertEquals(10.5, hit(arrowFrom(zombie), rogue, DamageCause.PROJECTILE), 1e-9);
        assertEquals(10.0, hit(zombie, rogue, DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10.0, hit(arrowFrom(server.addPlayer("Other")), rogue, DamageCause.PROJECTILE), 1e-9);
    }

    @Test
    void mercifulHandCutsThePaladinsMelee() {
        PlayerMock paladin = server.addPlayer("Paladin");
        pick(paladin, RpgClass.PALADIN, 25);
        assertEquals(9.5, hit(paladin, zombie(), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(9.75, hit(paladin, server.addPlayer("Other"), DamageCause.ENTITY_ATTACK), 1e-9);
        assertEquals(10.0, hit(arrowFrom(paladin), zombie(), DamageCause.PROJECTILE), 1e-9);
    }
}
