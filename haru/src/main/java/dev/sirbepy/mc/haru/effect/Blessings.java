package dev.sirbepy.mc.haru.effect;

import dev.sirbepy.mc.haru.data.OwnerState;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.function.LongSupplier;

/**
 * Timed potion effects bought from Haru. The expiry is wall-clock time kept in {@link OwnerState},
 * so a blessing survives death, logout and restarts; buying the same one again adds its minutes on
 * top of what's left.
 */
public final class Blessings {

    private static final long MILLIS_PER_TICK = 50;

    private final LongSupplier nowMillis;

    public Blessings(LongSupplier nowMillis) {
        this.nowMillis = nowMillis;
    }

    public void grant(Player player, OwnerState state, String effect, int amplifier, int minutes) {
        long now = nowMillis.getAsLong();
        OwnerState.Blessing existing = state.blessings().get(effect);
        long base = existing != null && existing.expiryMillis() > now ? existing.expiryMillis() : now;
        int keptAmplifier = existing != null && existing.expiryMillis() > now ? Math.max(existing.amplifier(), amplifier) : amplifier;
        state.blessings().put(effect, new OwnerState.Blessing(effect, keptAmplifier, base + minutes * 60_000L));
        reapply(player, state);
    }

    /** Puts every unexpired blessing back on the player and forgets expired ones. */
    public void reapply(Player player, OwnerState state) {
        long now = nowMillis.getAsLong();
        state.blessings().values().removeIf(b -> b.expiryMillis() <= now);
        for (OwnerState.Blessing blessing : state.blessings().values()) {
            PotionEffectType type = type(blessing.effectKey());
            if (type == null) {
                continue;
            }
            int ticks = (int) Math.min(Integer.MAX_VALUE, (blessing.expiryMillis() - now) / MILLIS_PER_TICK);
            player.addPotionEffect(new PotionEffect(type, ticks, blessing.amplifier(), true, false, true));
        }
    }

    public static PotionEffectType type(String effect) {
        return Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effect));
    }
}
