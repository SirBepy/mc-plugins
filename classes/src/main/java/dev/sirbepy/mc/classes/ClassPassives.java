package dev.sirbepy.mc.classes;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * The passives that aren't about damage: Insight (more orb XP), Careful Hands (skipped durability
 * loss), and the Arcanist's Frail Body (a transient max-health modifier, re-applied on join).
 */
public final class ClassPassives implements Listener {

    static final NamespacedKey FRAIL_BODY = new NamespacedKey("classes", "frail_body");

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
        if (classes.active(id) != RpgClass.ARCANIST || event.getAmount() <= 0) return;
        double bonus = event.getAmount() * RpgClass.ARCANIST.passivePercent(classes.level(id)) / 100
                + insightCarry.getOrDefault(id, 0.0);
        int whole = (int) bonus;
        insightCarry.put(id, bonus - whole);
        if (whole > 0) event.setAmount(event.getAmount() + whole);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDurability(PlayerItemDamageEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (classes.active(id) != RpgClass.ARTIFICER) return;
        if (random.getAsDouble() * 100 < RpgClass.ARTIFICER.passivePercent(classes.level(id))) {
            event.setCancelled(true);
        }
    }

    /** Adds or removes Frail Body to match the player's class. */
    public void applyFrailBody(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return;
        boolean wanted = classes.active(player.getUniqueId()) == RpgClass.ARCANIST;
        boolean has = max.getModifier(FRAIL_BODY) != null;
        if (wanted && !has) {
            max.addTransientModifier(new AttributeModifier(FRAIL_BODY, -RpgClass.FRAIL_BODY_HP, AttributeModifier.Operation.ADD_NUMBER));
            if (player.getHealth() > max.getValue()) player.setHealth(max.getValue());
        } else if (!wanted && has) {
            max.removeModifier(FRAIL_BODY);
        }
    }

    public void forget(UUID player) {
        insightCarry.remove(player);
    }
}
