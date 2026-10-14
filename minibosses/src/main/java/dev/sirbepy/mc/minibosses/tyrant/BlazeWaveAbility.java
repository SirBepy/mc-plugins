package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Random;

/**
 * The Ashen Tyrant's Heroic-tier offensive ability: a single coordinated wave of
 * {@link #WAVE_SIZE} Blazes spawned all at once around the Tyrant, distinct from Normal's
 * {@link BlazeSummonAbility} one-at-a-time trickle (3 Blazes staggered across 15 seconds). Heroic's
 * escalation is about overwhelming a fight with simultaneous adds rather than a slow drip -
 * fighters have to deal with a real crowd the instant the Tyrant crosses half health, not react to
 * one Blaze every 5 seconds. Fires exactly once per trigger (no repeat loop): {@code
 * ashen_tyrant.yml}'s Heroic tier still also lists {@link BlazeSummonAbility#ID}, so the Normal
 * trickle keeps running in parallel after this wave lands - same cumulative-abilities convention
 * {@code overgrown_sentinel.yml}'s Heroic row already established (Root stays listed alongside
 * Thorn Vines).
 */
public final class BlazeWaveAbility implements GuardianAbility {

    public static final String ID = "ashen_tyrant_blaze_wave";

    static final int WAVE_SIZE = 4;
    static final double SPAWN_RADIUS = 4.0; // wider than the Normal trickle's 3.0 so 4 Blazes have room

    private final Random random;

    public BlazeWaveAbility(Random random) {
        this.random = random;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        World world = boss.getWorld();
        Location bossLocation = boss.getLocation();
        for (int i = 0; i < WAVE_SIZE; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double x = bossLocation.getX() + Math.cos(angle) * SPAWN_RADIUS;
            double z = bossLocation.getZ() + Math.sin(angle) * SPAWN_RADIUS;
            Location spawnAt = new Location(world, x, bossLocation.getY() + 1, z);
            world.spawnEntity(spawnAt, EntityType.BLAZE);
        }
    }
}
