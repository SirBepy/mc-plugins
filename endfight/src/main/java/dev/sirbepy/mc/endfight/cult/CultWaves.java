package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.WaveSpawn;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Pours a wave of cultists out of the fortress gates each time the dragon's health crosses one of
 * {@code cult.waves.thresholds} for the first time this fight. Health bookkeeping is read fresh off
 * the dragon (so a datapack-raised max health is respected) and "already crossed" lives on the
 * dragon's own PDC, same restart-safe pattern as {@code CultGuards}' spawn flag.
 */
public final class CultWaves implements Listener {

    static final NamespacedKey CROSSED = new NamespacedKey("endfight", "waves-crossed");

    private final ArenaRegistry registry;
    private final Supplier<CultConfig> config;
    private final Logger logger;

    public CultWaves(ArenaRegistry registry, Supplier<CultConfig> config, Logger logger) {
        this.registry = registry;
        this.config = config;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        CultConfig cfg = config.get();
        if (!cfg.waves().enabled() || event.getFinalDamage() <= 0) {
            return;
        }
        EnderDragon dragon = dragonOf(event.getEntity());
        if (dragon == null) {
            return;
        }
        double maxHealth = maxHealth(dragon);
        double healthAfter = Math.max(0, dragon.getHealth());
        double healthBefore = Math.min(maxHealth, healthAfter + event.getFinalDamage());
        PersistentDataContainer pdc = dragon.getPersistentDataContainer();
        Set<Integer> crossed = crossedIndices(pdc);
        List<Integer> newly = WaveMath.newlyCrossedIndices(healthBefore, healthAfter, maxHealth,
                cfg.waves().thresholds(), crossed);
        for (int index : newly) {
            markCrossed(pdc, index);
            spawnWave(dragon, index, cfg);
        }
    }

    private void spawnWave(EnderDragon dragon, int waveIndex, CultConfig cfg) {
        List<WaveMath.WaveEntry> entries = cfg.waves().composition(waveIndex);
        if (entries.isEmpty()) {
            return;
        }
        World world = dragon.getWorld();
        List<Player> nearby = CultRange.playersInRange(world, cfg.waves().range());
        int alive = CultRange.aliveWaveCount(world);
        List<String> spawnTypes = WaveMath.spawnList(entries, nearby.size(), alive, cfg.maxAlive());
        if (spawnTypes.isEmpty()) {
            return;
        }
        List<WaveSpawn> points = registry.waveSpawns();
        if (points.isEmpty()) {
            logger.warning("Cult wave triggered with no wave spawn points registered; is the arena built?");
            return;
        }
        int i = 0;
        for (String typeName : spawnTypes) {
            spawnWaveMob(world, points.get(i % points.size()), typeName);
            i++;
        }
        for (Player player : nearby) {
            cfg.messages().send(player, "wave-announce");
        }
    }

    private void spawnWaveMob(World world, WaveSpawn point, String typeName) {
        EntityType type = CultEntityTypes.parse(typeName, logger);
        if (type == null) {
            return;
        }
        Location at = new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5);
        Entity entity = world.spawnEntity(at, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return;
        }
        CultIdentity.apply(living);
        if (living instanceof Mob mob) {
            nearestPlayer(world, at).ifPresent(mob::setTarget);
        }
    }

    private static java.util.Optional<Player> nearestPlayer(World world, Location at) {
        return world.getPlayers().stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(at)));
    }

    private static double maxHealth(EnderDragon dragon) {
        AttributeInstance attribute = dragon.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : dragon.getHealth();
    }

    /** The dragon behind a hit, whether the event names the dragon or one of its body parts -
     * same resolution {@code guild}'s DragonSlayers uses for the same reason. */
    private static EnderDragon dragonOf(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return entity instanceof EnderDragon dragon ? dragon : null;
    }

    static Set<Integer> crossedIndices(PersistentDataContainer pdc) {
        Set<Integer> out = new HashSet<>();
        String raw = pdc.get(CROSSED, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            try {
                out.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // a hand-edited value; skip it
            }
        }
        return out;
    }

    static void markCrossed(PersistentDataContainer pdc, int index) {
        Set<Integer> crossed = crossedIndices(pdc);
        if (crossed.add(index)) {
            List<String> parts = new ArrayList<>();
            for (int value : crossed) {
                parts.add(String.valueOf(value));
            }
            pdc.set(CROSSED, PersistentDataType.STRING, String.join(",", parts));
        }
    }
}
