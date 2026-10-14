package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Overgrown Sentinel's Mythic-tier mechanic: a healing-roots phase that must be interrupted by
 * damage, distinct from Heroic's {@link ThornVinesAbility} hazard. Once triggered, the boss heals
 * {@link #HEAL_FRACTION_PER_PULSE} of its max health every {@link #HEAL_INTERVAL_TICKS} for up to
 * {@link #PULSES} pulses - unless the fighters deal {@link #INTERRUPT_FRACTION} of its max health
 * in total damage first, via {@link #onIncomingDamage}, which {@code SentinelFight.onDamage} calls
 * for every hit landed while {@link #isActive} is true. An interrupted regrowth cancels the
 * remaining pulses immediately; an uninterrupted one simply finishes healing what it already
 * applied each pulse - there is no separate "full heal" lump sum, so the bar visibly climbs in real
 * time either way. Registered under the id {@code overgrown_sentinel.yml}'s Mythic tier already
 * names ({@code overgrown_sentinel_regrowth}), resolving what Phase A left as a documented no-op.
 */
public final class RegrowthAbility implements GuardianAbility {

    public static final String ID = "overgrown_sentinel_regrowth";

    static final long HEAL_INTERVAL_TICKS = 20L; // 1 second
    static final int PULSES = 5; // 5s total window if never interrupted
    static final double HEAL_FRACTION_PER_PULSE = 0.05; // 25% of max health if the whole window completes
    static final double INTERRUPT_FRACTION = 0.15; // 15% of max health dealt during the window interrupts it

    private final JavaPlugin plugin;
    private final NamespacedKey activeKey;
    private final NamespacedKey accumulatedKey;
    private final NamespacedKey thresholdKey;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public RegrowthAbility(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeKey = new NamespacedKey(plugin, "regrowth_active");
        this.accumulatedKey = new NamespacedKey(plugin, "regrowth_damage");
        this.thresholdKey = new NamespacedKey(plugin, "regrowth_threshold");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        AttributeInstance maxAttribute = boss.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxAttribute != null ? maxAttribute.getValue() : boss.getHealth();

        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        pdc.set(activeKey, PersistentDataType.BOOLEAN, true);
        pdc.set(accumulatedKey, PersistentDataType.DOUBLE, 0.0);
        pdc.set(thresholdKey, PersistentDataType.DOUBLE, maxHealth * INTERRUPT_FRACTION);

        UUID id = boss.getUniqueId();
        BukkitRunnable runnable = new BukkitRunnable() {
            int pulsesLeft = PULSES;

            @Override
            public void run() {
                if (pulsesLeft <= 0 || boss.isDead() || !boss.isValid()) {
                    finish(boss);
                    return;
                }
                pulsesLeft--;
                boss.setHealth(Math.min(maxHealth, boss.getHealth() + maxHealth * HEAL_FRACTION_PER_PULSE));
            }
        };
        tasks.put(id, runnable.runTaskTimer(plugin, HEAL_INTERVAL_TICKS, HEAL_INTERVAL_TICKS));
    }

    /** True while {@code boss} is still in its regrowth window, so {@code SentinelFight.onDamage}
     * knows to route the hit through {@link #onIncomingDamage}. */
    public boolean isActive(LivingEntity boss) {
        return Boolean.TRUE.equals(boss.getPersistentDataContainer().get(activeKey, PersistentDataType.BOOLEAN));
    }

    /** One hit landed on {@code boss} while regrowth is active: accumulates toward the interrupt
     * threshold, cancelling the remaining heal pulses the moment it is reached. */
    public void onIncomingDamage(LivingEntity boss, double damage) {
        if (damage <= 0 || !isActive(boss)) {
            return;
        }
        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        double accumulated = pdc.getOrDefault(accumulatedKey, PersistentDataType.DOUBLE, 0.0) + damage;
        pdc.set(accumulatedKey, PersistentDataType.DOUBLE, accumulated);
        double threshold = pdc.getOrDefault(thresholdKey, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        if (accumulated >= threshold) {
            finish(boss);
        }
    }

    private void finish(LivingEntity boss) {
        BukkitTask task = tasks.remove(boss.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        boss.getPersistentDataContainer().set(activeKey, PersistentDataType.BOOLEAN, false);
    }
}
