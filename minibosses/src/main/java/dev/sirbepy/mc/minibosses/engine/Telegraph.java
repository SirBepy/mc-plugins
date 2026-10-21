package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Color;
import org.bukkit.Sound;

/**
 * How one area attack warns and lands: its {@link Shape}, how long the warning runs, how it is
 * drawn and what it sounds like. Immutable; the {@code with...} methods return adjusted copies.
 *
 * <p>{@code grounded} is the jump counterplay: a player who is airborne at the moment it lands is
 * not hit, so "jump the ring" works. Leave it off for attacks that must be dodged by leaving.
 * A sound of {@code null} is silent.
 */
public record Telegraph(Shape shape, int warnTicks, Color color, float size, Sound startSound, Sound resolveSound,
        boolean grounded) {

    /** Red-orange, the colour the Guardians' warnings use unless an attack picks its own. */
    public static final Color DEFAULT_COLOR = Color.fromRGB(230, 70, 30);

    public static Telegraph of(Shape shape, int warnTicks) {
        return new Telegraph(shape, warnTicks, DEFAULT_COLOR, 1.2f, Sound.ENTITY_RAVAGER_ROAR,
                Sound.ENTITY_GENERIC_EXPLODE, false);
    }

    public Telegraph withColor(Color color) {
        return new Telegraph(shape, warnTicks, color, size, startSound, resolveSound, grounded);
    }

    public Telegraph withSounds(Sound start, Sound resolve) {
        return new Telegraph(shape, warnTicks, color, size, start, resolve, grounded);
    }

    public Telegraph withGrounded(boolean grounded) {
        return new Telegraph(shape, warnTicks, color, size, startSound, resolveSound, grounded);
    }
}
