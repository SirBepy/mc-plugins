package dev.sirbepy.mc.tweaks.seagifts;

import dev.sirbepy.mc.core.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * ItsLogiks' Porunga wish (2026-10-08, session jam0yw): permanent Dolphin's Grace and Conduit Power on him
 * alone, each switched on and off with {@code /seagifts}, at most once per real hour; death switches both
 * off until he calls them back. While a gift is on the plugin keeps it on him: on join, every second (so
 * a totem or {@code /effect clear} can't strip it for good) and by refusing milk's clear outright.
 */
public final class SeaGifts implements Listener {

    private final Server server;
    private final SeaGiftsStore store;
    private final Supplier<UUID> owner;
    private final LongSupplier clock;

    public SeaGifts(Server server, SeaGiftsStore store, Supplier<UUID> owner, LongSupplier clock) {
        this.server = server;
        this.store = store;
        this.owner = owner;
        this.clock = clock;
    }

    public boolean isOwner(Entity entity) {
        return entity instanceof Player && entity.getUniqueId().equals(owner.get());
    }

    public void flip(Player player, SeaGift gift) {
        if (!isOwner(player)) return;
        SeaGiftsStore.Flip flip = store.flip(player.getUniqueId(), gift, clock.getAsLong());
        if (flip.refused()) {
            tell(player, gift.displayName() + " was switched less than an hour ago; you can switch it again in "
                    + Durations.format(flip.remaining()) + ".");
            return;
        }
        if (flip.on()) {
            player.addPotionEffect(gift.effect());
            tell(player, gift.displayName() + " is yours again. You can switch it off in "
                    + Durations.format(SeaGiftsStore.COOLDOWN) + ".");
        } else {
            player.removePotionEffect(gift.type());
            tell(player, gift.displayName() + " is switched off. You can call it back in "
                    + Durations.format(SeaGiftsStore.COOLDOWN) + ".");
        }
    }

    public void status(Player player) {
        if (!isOwner(player)) return;
        long now = clock.getAsLong();
        for (SeaGift gift : SeaGift.values()) {
            Duration wait = store.wait(player.getUniqueId(), gift, now);
            tell(player, gift.displayName() + ": " + (store.isOn(player.getUniqueId(), gift) ? "on" : "off")
                    + (wait.isZero() ? ", switch it with /seagifts " + gift.key()
                    : ", can be switched again in " + Durations.format(wait)) + ".");
        }
    }

    /** Puts every gift that is on back on the owner if anything took it off. */
    public void tick() {
        UUID id = owner.get();
        Player player = id == null ? null : server.getPlayer(id);
        if (player != null && !player.isDead()) apply(player);
    }

    private void apply(Player player) {
        for (SeaGift gift : SeaGift.values()) {
            if (store.isOn(player.getUniqueId(), gift) && !player.hasPotionEffect(gift.type())) {
                player.addPotionEffect(gift.effect());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isOwner(event.getPlayer())) apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!isOwner(player)) return;
        List<SeaGift> taken = store.switchOffForDeath(player.getUniqueId());
        if (taken.isEmpty()) return;
        tell(player, "The sea's gifts left you when you died. Call them back with "
                + String.join(" and ", taken.stream().map(g -> "/seagifts " + g.key()).toList()) + ".");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEffectCleared(EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.MILK || event.getOldEffect() == null) return;
        if (!isOwner(event.getEntity())) return;
        SeaGift.ofType(event.getOldEffect().getType())
                .filter(gift -> store.isOn(event.getEntity().getUniqueId(), gift))
                .ifPresent(gift -> event.setCancelled(true));
    }

    private static void tell(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.AQUA));
    }
}
