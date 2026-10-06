package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * CoffiebagSupreme's Shenron wish (granted 2026-10-07): every phantom is fireproof, bigger, chases
 * twice its vanilla target speed, and is either venomous (poisons on hit, purple swirls) or tough
 * (more health).
 * The 2-night trigger and the doubled spawn rate are Paper config, see docs/server.md.
 *
 * Size, effects and attribute modifiers are saved with the entity, so a phantom is buffed once;
 * only the speed boost needs a per-tick task, over the phantoms currently in a loaded chunk.
 */
public final class PhantomBuffListener implements Listener {

    public enum Variant { VENOMOUS, TOUGH }

    // The client draws a phantom 1 + 0.15 * size times larger, so 3 is 1.45x.
    static final int SIZE = 3;
    // Phantom.updatePhantomSizeInfo sets attack damage to 6 + size; this keeps the bite at vanilla's 6.
    static final double SIZE_DAMAGE_OFFSET = -SIZE;
    static final double TOUGH_EXTRA_HEALTH = 10.0;
    static final int POISON_TICKS = 5 * 20;

    // Phantom flight speed is a constant inside its move controller (no attribute reaches it), so
    // the target velocity is doubled from outside. Each tick the controller sets v' = 0.8 v + 0.2 T
    // (T: its target velocity) and air drag leaves 0.91 v'. From the velocity this task set last
    // tick and the one it finds now, T = (now / 0.91 - 0.8 set) / 0.2; adding T / 4 before the next
    // tick makes the controller's step 0.8 v + 0.4 T, the same steering with twice the target.
    // The controller only speeds up while it faces its target, so turning caps the gain: measured
    // on 26.3 (6 circling phantoms, 20 s) mean 5.6 -> 7.9 blocks/s and top 0.5 s speed 10 -> 14-18.
    // Plain velocity scaling did worse (1.12x): it slows the response, so phantoms overshoot more.
    static final double DRAG = 0.91;
    static final double BLEND = 0.2;
    static final double TARGET_BOOST = 0.25;
    // The controller's speed caps at 1.8 blocks/tick; a larger estimate is a knockback or a wall hit.
    static final double MAX_TARGET = 1.8;

    // A mob screeches about every 6 s (80-tick cooldown, then a growing chance per tick). The
    // server only sends a volume-1 sound to players within 16 blocks, and the server pack stretches
    // phantom sounds to 32, so players 16-32 blocks out get their screech from here.
    static final int AMBIENT_CHECK_TICKS = 20;
    static final double AMBIENT_CHANCE = 1.0 / 6.0;
    static final double VANILLA_SOUND_RANGE = 16.0;
    static final double PACK_SOUND_RANGE = 32.0;

    private final NamespacedKey variantKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey healthKey;
    private final RandomGenerator random;
    // Loaded phantoms and the velocity this task gave each one last tick (null until the first).
    private final Map<Phantom, Vector> loaded = new HashMap<>();
    private final Set<Phantom> unbuffed = new HashSet<>();
    private int ticks;

    public PhantomBuffListener(Plugin plugin, RandomGenerator random) {
        this.variantKey = new NamespacedKey(plugin, "phantom_variant");
        this.damageKey = new NamespacedKey(plugin, "phantom_size_damage");
        this.healthKey = new NamespacedKey(plugin, "phantom_tough_health");
        this.random = random;
    }

    /** Fires before the phantom joins the world, so it is never seen at vanilla size. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Phantom phantom) buff(phantom);
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom)) return;
        loaded.put(phantom, null);
        // A phantom from an older chunk. Resizing moves its hitbox, which Paper 26.3 refuses (and
        // logs as an error) while the entity is being added, so it is buffed on the next tick.
        if (variant(phantom) == null) unbuffed.add(phantom);
    }

    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Phantom phantom) {
            loaded.remove(phantom);
            unbuffed.remove(phantom);
        }
    }

    /** Phantoms already in loaded chunks when the plugin enables never fire the add event. */
    public void trackLoaded(Iterable<World> worlds) {
        for (World world : worlds) {
            for (Phantom phantom : world.getEntitiesByClass(Phantom.class)) {
                buff(phantom);
                loaded.put(phantom, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Phantom phantom
                && variant(phantom) == Variant.VENOMOUS
                && event.getEntity() instanceof LivingEntity victim) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_TICKS, 0));
        }
    }

    public void buff(Phantom phantom) {
        if (variant(phantom) != null) return;
        Variant variant = random.nextBoolean() ? Variant.VENOMOUS : Variant.TOUGH;
        phantom.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, variant.name());

        phantom.setSize(SIZE);
        addModifier(phantom.getAttribute(Attribute.ATTACK_DAMAGE), damageKey, SIZE_DAMAGE_OFFSET);
        phantom.setShouldBurnInDay(false);
        phantom.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));

        if (variant == Variant.VENOMOUS) {
            // Nausea does nothing to a mob; it is here for its dark purple swirl particles, the only
            // per-phantom look 26.3 allows without client mods (phantoms have no texture variants).
            phantom.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    PotionEffect.INFINITE_DURATION, 0, false, true, false));
        } else {
            AttributeInstance maxHealth = phantom.getAttribute(Attribute.MAX_HEALTH);
            addModifier(maxHealth, healthKey, TOUGH_EXTRA_HEALTH);
            if (maxHealth != null) phantom.setHealth(maxHealth.getValue());
        }
    }

    public Variant variant(Phantom phantom) {
        String stored = phantom.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING);
        if (stored == null) return null;
        try {
            return Variant.valueOf(stored);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static void addModifier(AttributeInstance attribute, NamespacedKey key, double amount) {
        if (attribute == null || attribute.getModifier(key) != null) return;
        attribute.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** Runs every tick, before entities tick. */
    public void tick() {
        for (Phantom phantom : unbuffed) {
            if (phantom.isValid()) buff(phantom);
        }
        unbuffed.clear();
        boolean ambientCheck = ++ticks % AMBIENT_CHECK_TICKS == 0;
        for (Iterator<Map.Entry<Phantom, Vector>> it = loaded.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Phantom, Vector> entry = it.next();
            Phantom phantom = entry.getKey();
            if (!phantom.isValid()) {
                it.remove();
                continue;
            }
            Vector velocity = phantom.getVelocity();
            if (entry.getValue() != null) velocity.add(boost(entry.getValue(), velocity));
            phantom.setVelocity(velocity);
            entry.setValue(velocity);
            if (ambientCheck && random.nextDouble() < AMBIENT_CHANCE) screechFarAway(phantom);
        }
    }

    /** A quarter of the controller's last target velocity, recovered from what it did to {@code set}. */
    static Vector boost(Vector set, Vector now) {
        Vector target = now.clone().multiply(1 / DRAG).subtract(set.clone().multiply(1 - BLEND)).multiply(1 / BLEND);
        if (target.lengthSquared() > MAX_TARGET * MAX_TARGET) target.normalize().multiply(MAX_TARGET);
        return target.multiply(TARGET_BOOST);
    }

    private void screechFarAway(Phantom phantom) {
        float pitch = (float) ((random.nextDouble() - random.nextDouble()) * 0.2 + 1.0);
        for (Player player : phantom.getWorld().getPlayers()) {
            double distanceSq = player.getLocation().distanceSquared(phantom.getLocation());
            if (distanceSq > VANILLA_SOUND_RANGE * VANILLA_SOUND_RANGE
                    && distanceSq <= PACK_SOUND_RANGE * PACK_SOUND_RANGE) {
                player.playSound((Entity) phantom, Sound.ENTITY_PHANTOM_AMBIENT, SoundCategory.HOSTILE, 1.0f, pitch);
            }
        }
    }

    int loadedCount() {
        return loaded.size();
    }
}
