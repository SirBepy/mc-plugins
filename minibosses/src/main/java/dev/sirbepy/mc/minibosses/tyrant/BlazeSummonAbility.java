package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * The Ashen Tyrant's Normal-tier offensive ability: once triggered (by {@code TyrantFight}, the
 * first time the boss's projected health crosses half its max, the same threshold convention
 * {@code sentinel.RootAbility} established), it summons exactly one {@link Blaze} near the Tyrant
 * every {@link #SUMMON_INTERVAL_TICKS}, for up to {@link #SUMMONS} pulses - "nothing more
 * elaborate" per this dispatch's scope; Heroic/Mythic get real multi-blaze/wave mechanics in a
 * later dispatch. The repeat cadence is a plain {@link BukkitRunnable#runTaskTimer} tick counter,
 * not {@code World#getFullTime()}, which is always 0 in the Nether on this server (memory:
 * nether-end-no-day-clock.md) - the same tick-counter convention
 * {@code sentinel.ThornVinesAbility}/{@code RegrowthAbility} already use for their own periodic
 * triggers.
 */
public final class BlazeSummonAbility implements GuardianAbility {

    public static final String ID = "ashen_tyrant_blaze_summon";

    static final long SUMMON_INTERVAL_TICKS = 100L; // 5 seconds
    static final int SUMMONS = 3; // 3 blazes total across the window if the Tyrant survives that long
    static final double SPAWN_RADIUS = 3.0;

    private final JavaPlugin plugin;
    private final Random random;

    public BlazeSummonAbility(JavaPlugin plugin, Random random) {
        this.plugin = plugin;
        this.random = random;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        new BukkitRunnable() {
            int summonsLeft = SUMMONS;

            @Override
            public void run() {
                if (summonsLeft <= 0 || boss.isDead() || !boss.isValid()) {
                    cancel();
                    return;
                }
                summonsLeft--;
                summonOneBlaze(boss);
            }
        }.runTaskTimer(plugin, SUMMON_INTERVAL_TICKS, SUMMON_INTERVAL_TICKS);
    }

    private void summonOneBlaze(LivingEntity boss) {
        World world = boss.getWorld();
        Location bossLocation = boss.getLocation();
        double angle = random.nextDouble() * Math.PI * 2;
        double x = bossLocation.getX() + Math.cos(angle) * SPAWN_RADIUS;
        double z = bossLocation.getZ() + Math.sin(angle) * SPAWN_RADIUS;
        Location spawnAt = new Location(world, x, bossLocation.getY() + 1, z);
        world.spawnEntity(spawnAt, EntityType.BLAZE);
    }
}
