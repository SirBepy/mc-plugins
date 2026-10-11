package dev.sirbepy.mc.classes;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * The passives that aren't about damage: Insight (more orb XP), Careful Hands (skipped durability
 * loss), Blessed Vigor (more healing received), and the Arcanist's Frail Body and the Guardian's
 * Heavy Plate (transient max-health and movement-speed modifiers, both re-applied on join).
 */
public final class ClassPassives implements Listener {

    static final NamespacedKey FRAIL_BODY = new NamespacedKey("classes", "frail_body");
    static final NamespacedKey HEAVY_PLATE = new NamespacedKey("classes", "heavy_plate");

    private final Classes classes;
    /** 0 (inclusive) to 1 (exclusive); tests fix it. */
    private final DoubleSupplier random;
    /** Orb XP fractions carried between pickups, so +2% of a 1-point orb still adds up. */
    private final Map<UUID, Double> insightCarry = new HashMap<>();

    public ClassPassives(Classes classes, DoubleSupplier random) {
        this.classes = classes;
        this.random = random;
    }

    // After ClassXp (LOWEST), which earns class XP from the orb's own amount, before this bonus.
    @EventHandler(priority = EventPriority.LOW)
    public void onOrb(PlayerExpChangeEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        double percent = passivePercent(id, RpgClass.ARCANIST);
        if (percent <= 0 || event.getAmount() <= 0) return;
        double bonus = event.getAmount() * percent / 100 + insightCarry.getOrDefault(id, 0.0);
        int whole = (int) bonus;
        insightCarry.put(id, bonus - whole);
        if (whole > 0) event.setAmount(event.getAmount() + whole);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double percent = passivePercent(player.getUniqueId(), RpgClass.PALADIN);
        if (percent > 0) event.setAmount(event.getAmount() * (1 + percent / 100));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDurability(PlayerItemDamageEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        double percent = passivePercent(id, RpgClass.ARTIFICER);
        if (percent > 0 && random.getAsDouble() * 100 < percent) {
            event.setCancelled(true);
        }
    }

    /**
     * {@code wanted}'s passive bonus for {@code id}: full strength (at that class's own level) if it's
     * the active class, half strength rounded down (Sivra21's dual-classing wish) if it's the secondary,
     * else 0.
     */
    private double passivePercent(UUID id, RpgClass wanted) {
        RpgClass active = classes.active(id);
        if (active == wanted) return wanted.passivePercent(classes.level(id));
        // The secondary only counts alongside a main class, never on its own.
        if (active != null && classes.secondary(id) == wanted) return Math.floor(wanted.passivePercent(classes.secondaryLevel(id)) / 2);
        return 0;
    }

    /** Adds or removes Frail Body to match the player's class; the secondary's weakness is never halved. */
    public void applyFrailBody(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return;
        UUID id = player.getUniqueId();
        RpgClass active = classes.active(id);
        boolean wanted = active == RpgClass.ARCANIST || (active != null && classes.secondary(id) == RpgClass.ARCANIST);
        boolean has = max.getModifier(FRAIL_BODY) != null;
        if (wanted && !has) {
            max.addTransientModifier(new AttributeModifier(FRAIL_BODY, -RpgClass.FRAIL_BODY_HP, AttributeModifier.Operation.ADD_NUMBER));
            if (player.getHealth() > max.getValue()) player.setHealth(max.getValue());
        } else if (!wanted && has) {
            max.removeModifier(FRAIL_BODY);
        }
    }

    /** Adds or removes Heavy Plate to match the player's class; the secondary's weakness is never halved. */
    public void applyHeavyPlate(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        UUID id = player.getUniqueId();
        RpgClass active = classes.active(id);
        boolean wanted = active == RpgClass.GUARDIAN || (active != null && classes.secondary(id) == RpgClass.GUARDIAN);
        boolean has = speed.getModifier(HEAVY_PLATE) != null;
        if (wanted && !has) {
            speed.addTransientModifier(new AttributeModifier(HEAVY_PLATE, -RpgClass.HEAVY_PLATE_SPEED / 100,
                    AttributeModifier.Operation.ADD_SCALAR));
        } else if (!wanted && has) {
            speed.removeModifier(HEAVY_PLATE);
        }
    }

    public void forget(UUID player) {
        insightCarry.remove(player);
    }
}
