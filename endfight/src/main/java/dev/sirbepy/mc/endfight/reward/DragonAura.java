package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Particle;

import java.util.Locale;

/**
 * The 3 cosmetic dragon auras a Dragon Hoard roll can unlock, modelled on haru's
 * {@code effect.Auras} (same "one worn at a time, particle puff at the feet" approach - reused the
 * idea rather than the class, since haru's {@code Auras.tick} reads a Haru-specific
 * {@code OwnerState} for a single owner, while this one tracks every player on the server through
 * {@link RewardProgressStore}). Worn with {@code /dragonaura <name|off>} - see
 * {@link DragonAuraService}.
 */
public enum DragonAura {
    DRAGONS_BREATH("Dragon's Breath", Particle.DRAGON_BREATH, 3, 0.6),
    ENDER("Ender", Particle.PORTAL, 4, 0.5),
    VOID_EMBER("Void Ember", Particle.SOUL_FIRE_FLAME, 2, 0.5);

    private final String displayName;
    private final Particle particle;
    private final int count;
    private final double spread;

    DragonAura(String displayName, Particle particle, int count, double spread) {
        this.displayName = displayName;
        this.particle = particle;
        this.count = count;
        this.spread = spread;
    }

    public String displayName() {
        return displayName;
    }

    public Particle particle() {
        return particle;
    }

    public int count() {
        return count;
    }

    public double spread() {
        return spread;
    }

    /** Matches the enum name or its display name, case-insensitively (for {@code /dragonaura} and
     * for re-parsing a persisted value); null if nothing matches. */
    public static DragonAura byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "");
        for (DragonAura aura : values()) {
            if (aura.name().equals(normalized) || aura.displayName().equalsIgnoreCase(trimmed)) {
                return aura;
            }
        }
        return null;
    }
}
