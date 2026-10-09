package dev.sirbepy.mc.endfight.perch;

import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Keeps the dragon from perching on the exit portal while any pillar's crystal is still alive -
 * the fight should end by killing it, not by it landing early. Cancelling {@link
 * EnderDragonChangePhaseEvent} (rather than redirecting it to a specific phase) leaves the dragon
 * exactly where vanilla's own AI already had it - still circling/strafing/attacking, whatever
 * phase it was in before it decided to go land - so it keeps fighting with no extra phase forced
 * on it. The event supports both; cancelling is the less invasive of the two.
 */
public final class PerchGateListener implements Listener {

    private final ArenaRegistry registry;
    private final Supplier<PerchGateConfig> config;

    public PerchGateListener(ArenaRegistry registry, Supplier<PerchGateConfig> config) {
        this.registry = registry;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhaseChange(EnderDragonChangePhaseEvent event) {
        PerchGateConfig cfg = config.get();
        // DYING is never one of the two landing phases below, so it's never touched here either
        // way - no special case needed.
        if (!cfg.enabled() || !PerchGate.isLandingPhase(event.getNewPhase())) {
            return;
        }
        EnderDragon dragon = event.getEntity();
        World world = dragon.getWorld();
        List<Pillar> pillars = registry.pillars();
        List<PerchGate.Point> crystals = world.getEntitiesByClass(EnderCrystal.class).stream()
                .map(crystal -> new PerchGate.Point(
                        crystal.getLocation().getX(), crystal.getLocation().getY(), crystal.getLocation().getZ()))
                .toList();
        if (PerchGate.anyPillarCrystalAlive(pillars, crystals, cfg.pillarTopRadius(), cfg.fallbackRadius(), cfg.fallbackMinY())) {
            event.setCancelled(true);
        }
    }
}
