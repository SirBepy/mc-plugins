package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Phantoms guarding the towers (Joe, 2026-10-09): every pillar whose crystal still stands always
 * has {@code cult.wardens.per-crystal} (2) phantoms circling above it, anchored there so they
 * patrol that tower and swoop at whoever climbs it. A killed warden comes back on the next top-up
 * ({@code cult.wardens.interval-ticks}, 30 s) while its crystal stands; once the crystal breaks
 * nothing replaces them. Wardens are cult mobs (type {@code phantom}) but neither guards nor wave
 * mobs, so they never sleep and never count against the wave cap.
 */
public final class CrystalWardens {

    static final NamespacedKey WARDEN = new NamespacedKey("endfight", "warden");
    /** Same "this crystal belongs to that pillar" reach as the perch gate. */
    static final double CRYSTAL_REACH = 3.0;

    private final Plugin plugin;
    private final ArenaRegistry registry;
    private final Supplier<CultConfig> config;
    private final BooleanSupplier arenaBusy;
    private BukkitTask task;

    public CrystalWardens(Plugin plugin, ArenaRegistry registry, Supplier<CultConfig> config, BooleanSupplier arenaBusy) {
        this.plugin = plugin;
        this.registry = registry;
        this.config = config;
        this.arenaBusy = arenaBusy;
    }

    public void start() {
        stop();
        long interval = Math.max(20, config.get().wardens().intervalTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::topUp, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Package-visible so tests can run a top-up without waiting on the timer. */
    void topUp() {
        CultConfig cfg = config.get();
        if (!cfg.wardens().enabled() || !registry.isBuilt() || arenaBusy.getAsBoolean()) {
            return;
        }
        List<Pillar> pillars = registry.pillars();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END || !dragonAlive(world)
                    || CultRange.playersInRange(world, cfg.guards().range()).isEmpty()) {
                continue;
            }
            List<double[]> crystals = new ArrayList<>();
            for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                Location at = crystal.getLocation();
                crystals.add(new double[] {at.getX(), at.getY(), at.getZ()});
            }
            Map<Integer, Integer> alive = new HashMap<>();
            for (Phantom phantom : world.getEntitiesByClass(Phantom.class)) {
                Integer pillar = phantom.getPersistentDataContainer().get(WARDEN, PersistentDataType.INTEGER);
                if (pillar != null && !phantom.isDead()) {
                    alive.merge(pillar, 1, Integer::sum);
                }
            }
            Map<Integer, Integer> missing = shortfall(guardedPillars(pillars, crystals), alive, cfg.wardens().perCrystal());
            missing.forEach((index, count) -> {
                for (int i = 0; i < count; i++) {
                    spawn(world, index, pillars.get(index), cfg.wardens().height());
                }
            });
        }
    }

    /** Indices of the pillars whose own crystal (topY + 1 over its centre) is still there. */
    static List<Integer> guardedPillars(List<Pillar> pillars, List<double[]> crystals) {
        List<Integer> out = new ArrayList<>();
        double reachSq = CRYSTAL_REACH * CRYSTAL_REACH;
        for (int i = 0; i < pillars.size(); i++) {
            Pillar p = pillars.get(i);
            for (double[] c : crystals) {
                double dx = c[0] - (p.cx() + 0.5);
                double dy = c[1] - (p.topY() + 1);
                double dz = c[2] - (p.cz() + 0.5);
                if (dx * dx + dy * dy + dz * dz <= reachSq) {
                    out.add(i);
                    break;
                }
            }
        }
        return out;
    }

    /** How many wardens each guarded pillar is short of {@code perCrystal}. */
    static Map<Integer, Integer> shortfall(List<Integer> guarded, Map<Integer, Integer> alive, int perCrystal) {
        Map<Integer, Integer> out = new HashMap<>();
        for (int index : guarded) {
            int need = perCrystal - alive.getOrDefault(index, 0);
            if (need > 0) {
                out.put(index, need);
            }
        }
        return out;
    }

    private void spawn(World world, int index, Pillar pillar, int height) {
        Location above = new Location(world, pillar.cx() + 0.5, pillar.topY() + height, pillar.cz() + 0.5);
        world.spawn(above, Phantom.class, phantom -> {
            CultIdentity.apply(phantom, "phantom");
            phantom.getPersistentDataContainer().set(WARDEN, PersistentDataType.INTEGER, index);
            try {
                phantom.setAnchorLocation(above);
            } catch (RuntimeException e) {
                // No anchor support (a test server): it still hunts, just without the patrol.
            }
        });
    }

    private static boolean dragonAlive(World world) {
        return DragonFighters.liveDragon(world).isPresent();
    }

    static boolean isWarden(Entity entity) {
        return entity.getPersistentDataContainer().has(WARDEN, PersistentDataType.INTEGER);
    }
}
