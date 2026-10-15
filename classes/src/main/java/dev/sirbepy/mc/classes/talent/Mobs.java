package dev.sirbepy.mc.classes.talent;

import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/** Which entities class abilities may touch. */
public final class Mobs {

    /**
     * Max health at or above this counts as a boss. The Wither and the Ender Dragon implement
     * {@link Boss}; the Minibosses Guardians are ordinary mob types with boosted health, so this
     * catches them (and the Warden) without depending on that plugin.
     */
    static final double BOSS_HEALTH = 100;

    private Mobs() {
    }

    /** A hostile mob abilities may damage: never a player, never a tamed pet. */
    public static boolean hostile(Entity e) {
        return e instanceof Enemy && e instanceof LivingEntity living && !living.isDead()
                && !(e instanceof Player) && !(e instanceof Tameable t && t.isTamed());
    }

    /** Roots, stuns, taunts and grabs skip bosses; percentage-of-health effects are capped on them. */
    public static boolean boss(Entity e) {
        if (e instanceof Boss) return true;
        if (!(e instanceof LivingEntity living)) return false;
        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
        return max != null && max.getValue() >= BOSS_HEALTH;
    }

    public static boolean undead(Entity e) {
        return Tag.ENTITY_TYPES_UNDEAD.isTagged(e.getType());
    }
}
