package dev.sirbepy.mc.classes;

import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
}
