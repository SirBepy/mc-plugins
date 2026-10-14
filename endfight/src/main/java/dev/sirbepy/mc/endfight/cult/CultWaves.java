package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.endfight.fight.FightStats;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fires each wave of {@code cult.waves.schedule} the first time the dragon's health drops to its
 * fraction of max health this fight (Joe's HP schedule, 2026-10-09), sized by the players actually
 * fighting, and hands the mobs to {@link WaveSpawner} to bring out over a couple of seconds.
 * Health bookkeeping is read fresh off the dragon (so a datapack-raised max health is respected)
 * and "already crossed" lives on the dragon's own PDC, same restart-safe pattern as
 * {@code CultGuards}' spawn flag.
 */
public final class CultWaves implements Listener {

    static final NamespacedKey CROSSED = new NamespacedKey("endfight", "waves-crossed");

    private final Supplier<CultConfig> config;
    private final WaveSpawner spawner;

    public CultWaves(Supplier<CultConfig> config, WaveSpawner spawner) {
        this.config = config;
        this.spawner = spawner;
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
        // The event fires before the hit lands (javap on 26.3: LivingEntity.actuallyHurt checks
        // the event, then calls setHealth), so the dragon's health here is still from before it.
        double healthBefore = Math.min(maxHealth, dragon.getHealth());
        double healthAfter = Math.max(0, healthBefore - event.getFinalDamage());
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
        WaveMath.Wave wave = cfg.waves().schedule().get(waveIndex);
        World world = dragon.getWorld();
        List<Player> fighters = CultRange.fighters(world, cfg.waves().range());
        List<UUID> damagers = topDamagers(dragon, fighters);
        int alive = CultRange.aliveWaveCount(world) + spawner.queued();
        WaveMath.PlanResult result = WaveMath.planWithOverflow(wave.entries(), fighters.size(), damagers, alive, cfg.maxAlive());
        List<WaveMath.PlannedSpawn> spawns = result.spawns();
        if (!result.overflow().isEmpty()) {
            spawner.enqueueOverflow(dragon, result.overflow());
        }
        if (spawns.isEmpty()) {
            return;
        }
        spawner.enqueue(dragon, spawns);
        for (Player player : CultRange.playersInRange(world, cfg.waves().range())) {
            if (wave.announce() != null && !wave.announce().isBlank()) {
                player.sendMessage(Messages.mini().deserialize(wave.announce()));
            } else {
                cfg.messages().send(player, "wave-announce");
            }
        }
    }

    /** The fighters who hurt this dragon most, most first; if nobody has been credited with any
     * damage (an op's {@code /damage}, say), every fighter in range is fair game instead. */
    private static List<UUID> topDamagers(EnderDragon dragon, List<Player> fighters) {
        Set<UUID> fighting = new HashSet<>();
        for (Player player : fighters) {
            fighting.add(player.getUniqueId());
        }
        List<UUID> top = new ArrayList<>();
        for (UUID id : FightStats.topDamagers(dragon.getPersistentDataContainer(), Integer.MAX_VALUE)) {
            if (fighting.contains(id)) {
                top.add(id);
            }
        }
        if (top.isEmpty()) {
            top.addAll(fighting);
        }
        return top;
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
