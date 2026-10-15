package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One case per Assassin node (docs/classes-revamp.md, Rogue). No sneaking unless a case needs it. */
class RogueAssassinTest extends KitTestBase {

    /** A zombie at the origin facing +X, with {@code p} placed {@code behind} it or not. */
    private Zombie faceEastZombie() {
        Zombie z = zombie(0);
        Location facing = at(0);
        facing.setDirection(new Vector(1, 0, 0));
        z.teleport(facing);
        return z;
    }

    @Test
    void backstabOnlyAppliesFromBehindTheVictim() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "backstab", 3);
        Zombie z = faceEastZombie();
        p.teleport(at(-1)); // west of the zombie, which faces east: this is its back.
        assertEquals(10 * 1.30, melee(p, z), 1e-9, "rank 3: +30% from behind");
        p.teleport(at(1)); // east of the zombie: its front.
        assertEquals(10, melee(p, z), 1e-9, "no bonus from the front");
    }

    @Test
    void cutthroatAppliesWhenTheMobIsntTargetingTheAttacker() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "cutthroat", 2);
        Zombie z = zombie(1);
        z.setTarget(null);
        assertEquals(10 * 1.20, melee(p, z), 1e-9, "rank 2: +20% when not targeted");
        z.setTarget(p);
        assertEquals(10, melee(p, z), 1e-9, "no bonus once it targets the attacker");
    }

    @Test
    void ambushBoostsOnlyTheFirstHitAfterInvisibility() {
        PlayerMock p = player(RpgClass.ROGUE, 10);
        rank(p, "ambush", 2);
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.TWO), "Smoke Bomb grants Invisibility and arms Ambush");
        assertEquals(10 * 1.60, melee(p, z), 1e-9, "rank 2: +60% on the first hit");
        assertEquals(10, melee(p, z), 1e-9, "the window is spent after one hit");
    }

    @Test
    void shadowStrikeTeleportsTheCasterBehindTheLookedAtMob() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "shadow_strike", 2);
        Zombie z = faceEastZombie();
        Location facing = at(-5);
        facing.setDirection(new Vector(1, 0, 0));
        p.teleport(facing);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(p.getLocation().distance(z.getLocation()) < 2, "landed next to the mob");
        assertEquals(10 * 1.0, melee(p, z), 1e-9); // sanity: still a valid position, no NPE
        assertTrue(RogueListener.isBehind(p, z), "Shadow Strike lands behind the mob it faced");
    }

    @Test
    void finisherShortensShadowStepsCooldownOnAKill() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "finisher", 2);
        assertTrue(cast(p, Slot.ONE));
        long before = classes.cooldownLeftMillis(p.getUniqueId());
        kill(zombie(1), p);
        long after = classes.cooldownLeftMillis(p.getUniqueId());
        assertEquals(before - 10_000, after);
    }

    @Test
    void assassinateDoublesABackstabOnAMobTargetingNobody() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "assassinate", 1);
        Zombie z = faceEastZombie();
        z.setTarget(null);
        p.teleport(at(-1));
        assertEquals(20, melee(p, z), 1e-9, "double on an untargeted backstab");
    }

    @Test
    void swiftKillGivesSpeedTwoOnAKill() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "swift_kill", 2);
        kill(zombie(1), p);
        assertTrue(p.hasPotionEffect(PotionEffectType.SPEED));
        assertEquals(1, p.getPotionEffect(PotionEffectType.SPEED).getAmplifier(), "Speed II is amplifier 1");
    }

    @Test
    void precisionBoostsCriticalHits() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "precision", 2);
        p.setFallDistance(3);
        p.setOnGround(false);
        Zombie z = zombie(1);
        assertEquals(10 * 1.20, melee(p, z), 1e-9, "rank 2: +20% on a critical hit");
        p.setFallDistance(0);
        p.setOnGround(true);
        assertEquals(10, melee(p, z), 1e-9, "no bonus on a grounded hit");
    }

    @Test
    void chainKillMakesShadowStepReadyAgainOncePerWindow() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "chain_kill", 1);
        assertTrue(cast(p, Slot.ONE));
        kill(zombie(1), p);
        assertEquals(0, classes.cooldownLeftMillis(p.getUniqueId()), "a kill within 2 s makes it ready again");

        assertTrue(cast(p, Slot.ONE));
        kill(zombie(2), p);
        assertTrue(classes.cooldownLeftMillis(p.getUniqueId()) > 0, "once per 30 s: the second proc is blocked");
    }

    @Test
    void vanishingActGrantsInvisibilityAfterShadowStep() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "vanishing_act", 1);
        assertFalse(p.hasPotionEffect(PotionEffectType.INVISIBILITY));
        assertTrue(cast(p, Slot.ONE));
        assertTrue(p.hasPotionEffect(PotionEffectType.INVISIBILITY));
    }

    private void kill(Zombie dead, PlayerMock killer) {
        dead.setKiller(killer);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(dead,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                new java.util.ArrayList<>(), 5));
    }
}
