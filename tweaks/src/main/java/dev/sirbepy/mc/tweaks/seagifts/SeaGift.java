package dev.sirbepy.mc.tweaks.seagifts;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Optional;

/** The two effects of Porunga's sea gifts wish, each switched on and off on its own. */
public enum SeaGift {
    DOLPHIN(PotionEffectType.DOLPHINS_GRACE, "Dolphin's Grace"),
    CONDUIT(PotionEffectType.CONDUIT_POWER, "Conduit Power");

    private final PotionEffectType type;
    private final String displayName;

    SeaGift(PotionEffectType type, String displayName) {
        this.type = type;
        this.displayName = displayName;
    }

    public PotionEffectType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    /** The subcommand and the state-file key. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Level I, ambient and without particles, like the console's {@code effect give ... infinite 0 true}. */
    public PotionEffect effect() {
        return new PotionEffect(type, PotionEffect.INFINITE_DURATION, 0, true, false, true);
    }

    public static Optional<SeaGift> ofType(PotionEffectType type) {
        for (SeaGift gift : values()) {
            if (gift.type.equals(type)) return Optional.of(gift);
        }
        return Optional.empty();
    }
}
