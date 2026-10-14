package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The Ashen Tyrant's Mythic-tier mechanic: an arena-wide ash-and-ember hazard phase, distinct from
 * Normal's single-Blaze trickle ({@link BlazeSummonAbility}) and Heroic's simultaneous
 * {@link BlazeWaveAbility} - this one adds no extra mobs at all, it is a periodic damage/burn tick
 * against every player anywhere in the fight area for a sustained duration. Chosen over the todo's
 * other suggestion (a rage mechanic triggered by Blaze deaths) because that would need
 * {@code TyrantFight} to listen for its own summoned Blazes dying, a second event-wiring path this
 * dispatch would rather not add when a direct periodic hazard already reads as "ancient fortress
 * fills with ash and fire" and is simpler to test deterministically. {@link #RADIUS} is
 * deliberately larger than any melee-range ability in this repo ({@code RootAbility}/
 * {@code ThornVinesAbility} use 6.0) since this hazard is meant to reach across the whole arena, not
 * just whoever is standing next to the boss. Fires {@link #PULSES} times, every
 * {@link #PULSE_INTERVAL_TICKS}, via a plain scheduler tick counter - never
 * {@code World#getFullTime()}, which is always 0 in the Nether on this server (memory:
 * nether-end-no-day-clock.md), the same convention {@code BlazeSummonAbility} already uses.
 * Registered under the id {@code ashen_tyrant.yml}'s Mythic tier already names
 * ({@code ashen_tyrant_ashen_storm}), resolving what Phase A left as a documented no-op.
 */
public final class AshenStormAbility implements GuardianAbility {

    public static final String ID = "ashen_tyrant_ashen_storm";

    static final double RADIUS = 10.0; // arena-wide, not melee-range
    static final long PULSE_INTERVAL_TICKS = 40L; // 2 seconds
    static final int PULSES = 5; // 5 * 2s = 10s of storm uptime
    static final double DAMAGE_PER_PULSE = 3.0; // 1.5 hearts per pulse, real but not a one-shot
    static final int FIRE_TICKS_PER_PULSE = 40; // 2 seconds of burning per pulse

    private final JavaPlugin plugin;

    public AshenStormAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        new BukkitRunnable() {
            int pulsesLeft = PULSES;

            @Override
            public void run() {
                if (pulsesLeft <= 0 || boss.isDead() || !boss.isValid()) {
                    cancel();
                    return;
                }
                pulsesLeft--;
                for (var entity : boss.getWorld().getNearbyEntities(boss.getLocation(), RADIUS, RADIUS, RADIUS)) {
                    if (entity instanceof Player player) {
                        player.damage(DAMAGE_PER_PULSE);
                        player.setFireTicks(Math.max(player.getFireTicks(), FIRE_TICKS_PER_PULSE));
                    }
                }
            }
        }.runTaskTimer(plugin, PULSE_INTERVAL_TICKS, PULSE_INTERVAL_TICKS);
    }
}
