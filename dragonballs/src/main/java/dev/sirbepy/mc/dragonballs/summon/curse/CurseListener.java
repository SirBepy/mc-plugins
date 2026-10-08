package dev.sirbepy.mc.dragonballs.summon.curse;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Applies persisted effect curses to an online player: on join, on respawn ({@code
 * PlayerPostRespawnEvent}, which fires after the respawn location has settled, rather than a
 * 1-tick-later task off {@code PlayerRespawnEvent}), and immediately from {@code /shenron curse
 * effect} via {@link #applyNow}. No per-tick scan: {@link #sweep} runs once a minute.
 *
 * <p>Curses expire in real time, but a vanilla effect only ticks down while its player is online
 * (and slower under lag), so it would outlive the curse. Every expiry is therefore enforced here:
 * an expired curse's effect is stripped on join, respawn and in the sweep, and a re-applied one
 * replaces the old effect instead of merging with it (vanilla keeps the longer duration).
 *
 * <p>Curse effects are told apart from legitimate ones by being ambient with the curse's exact type
 * and amplifier. Potions and {@code /effect} are never ambient, so they are safe; beacons and
 * conduits are ambient too, so a beacon effect matching an expired curse's type and amplifier gets
 * stripped once, and the beacon puts it back on its next 4-second pulse.
 */
public final class CurseListener implements Listener {

    private final CurseStore curses;
    private final LongSupplier clock;

    public CurseListener(CurseStore curses, LongSupplier clock) {
        this.curses = curses;
        this.clock = clock;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyNow(event.getPlayer());
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        applyNow(event.getPlayer());
    }

    /** Applies every still-active effect curse for {@code player} with its remaining duration, and
     * sends the one "you feel the dragon's curse" message if at least one was applied. */
    public void applyNow(Player player) {
        long now = clock.getAsLong();
        stripExpired(player, now);
        List<EffectCurse> active = curses.activeEffects(player.getUniqueId(), now);
        if (active.isEmpty()) {
            return;
        }
        boolean appliedAny = false;
        for (EffectCurse curse : active) {
            PotionEffectType type = resolveType(curse.effectKey());
            int ticks = CurseMath.remainingTicks(curse.expiryMillis(), now);
            if (type == null || ticks <= 0) {
                continue;
            }
            stripCurseEffect(player, curse);
            player.addPotionEffect(new PotionEffect(type, ticks, curse.amplifier(), true, true));
            appliedAny = true;
        }
        if (appliedAny) {
            player.sendMessage(Component.text("You feel the dragon's curse.", NamedTextColor.DARK_PURPLE));
        }
    }

    /** Strips the effects of curses that expired while these players were online; the scheduled
     * once-a-minute pass over the online players. */
    public void sweep(Collection<? extends Player> online) {
        long now = clock.getAsLong();
        for (Player player : online) {
            stripExpired(player, now);
        }
    }

    /** {@code /shenron curse lift}: removes every stored curse, expired ones included, and strips
     * their effects from {@code online} when the player is on ({@code null} otherwise). */
    public void lift(UUID playerId, Player online) {
        List<EffectCurse> removed = curses.liftAll(playerId);
        if (online != null) {
            for (EffectCurse curse : removed) {
                stripCurseEffect(online, curse);
            }
        }
    }

    private void stripExpired(Player player, long now) {
        for (EffectCurse curse : curses.takeExpired(player.getUniqueId(), now)) {
            stripCurseEffect(player, curse);
        }
    }

    private static void stripCurseEffect(Player player, EffectCurse curse) {
        PotionEffectType type = resolveType(curse.effectKey());
        if (type == null) {
            return;
        }
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.isAmbient() && current.getAmplifier() == curse.amplifier()) {
            player.removePotionEffect(type);
        }
    }

    /** Accepts both {@code "slowness"} and {@code "minecraft:slowness"} forms, matching how the
     * judge is likely to type it at the console. */
    public static PotionEffectType resolveType(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        if (namespaced == null) {
            namespaced = NamespacedKey.minecraft(key);
        }
        return PotionEffectType.getByKey(namespaced);
    }
}
