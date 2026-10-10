package dev.sirbepy.mc.tweaks.seagifts;

import dev.sirbepy.mc.core.Durations;
import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Porunga's sea gifts (ItsLogiks, session jam0yw, and SirBepy, session ay4gtv, both 2026-10-08): permanent
 * Dolphin's Grace and Conduit Power on each owner, each switched on and off by that owner with
 * {@code /seagifts}, at most once per real hour; death switches both off until he calls them back. Every
 * owner has his own switches and hours. While a gift is on the plugin keeps it on him: on join, every
 * second (so a totem or {@code /effect clear} can't strip it for good) and by refusing milk's clear outright.
 */
public final class SeaGifts implements Listener {

    private final Server server;
    private final SeaGiftsStore store;
    private final Supplier<Set<UUID>> owners;
    private final LongSupplier clock;
    private final Messages messages;

    public SeaGifts(Server server, SeaGiftsStore store, Supplier<Set<UUID>> owners, LongSupplier clock, Messages messages) {
        this.server = server;
        this.store = store;
        this.owners = owners;
        this.clock = clock;
        this.messages = messages;
    }

    public boolean isOwner(Entity entity) {
        return entity instanceof Player && owners.get().contains(entity.getUniqueId());
    }

    public void flip(Player player, SeaGift gift) {
        if (!isOwner(player)) return;
        SeaGiftsStore.Flip flip = store.flip(player.getUniqueId(), gift, clock.getAsLong());
        if (flip.refused()) {
            messages.send(player, "seagifts-flip-refused", Placeholder.unparsed("gift", gift.displayName()),
                    Placeholder.unparsed("remaining", Durations.format(flip.remaining())));
            return;
        }
        if (flip.on()) {
            player.addPotionEffect(gift.effect());
            messages.send(player, "seagifts-flip-on", Placeholder.unparsed("gift", gift.displayName()),
                    Placeholder.unparsed("cooldown", Durations.format(SeaGiftsStore.COOLDOWN)));
        } else {
            player.removePotionEffect(gift.type());
            messages.send(player, "seagifts-flip-off", Placeholder.unparsed("gift", gift.displayName()),
                    Placeholder.unparsed("cooldown", Durations.format(SeaGiftsStore.COOLDOWN)));
        }
    }

    public void status(Player player) {
        if (!isOwner(player)) return;
        long now = clock.getAsLong();
        for (SeaGift gift : SeaGift.values()) {
            Duration wait = store.wait(player.getUniqueId(), gift, now);
            String state = store.isOn(player.getUniqueId(), gift) ? "on" : "off";
            if (wait.isZero()) {
                messages.send(player, "seagifts-status-ready", Placeholder.unparsed("gift", gift.displayName()),
                        Placeholder.unparsed("state", state), Placeholder.unparsed("key", gift.key()));
            } else {
                messages.send(player, "seagifts-status-cooldown", Placeholder.unparsed("gift", gift.displayName()),
                        Placeholder.unparsed("state", state), Placeholder.unparsed("wait", Durations.format(wait)));
            }
        }
    }

    /** Puts every gift that is on back on each online owner if anything took it off. */
    public void tick() {
        for (UUID id : owners.get()) {
            Player player = server.getPlayer(id);
            if (player != null && !player.isDead()) apply(player);
        }
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
        messages.send(player, "seagifts-gifts-left", Placeholder.unparsed("commands",
                String.join(" and ", taken.stream().map(g -> "/seagifts " + g.key()).toList())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEffectCleared(EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.MILK || event.getOldEffect() == null) return;
        if (!isOwner(event.getEntity())) return;
        SeaGift.ofType(event.getOldEffect().getType())
                .filter(gift -> store.isOn(event.getEntity().getUniqueId(), gift))
                .ifPresent(gift -> event.setCancelled(true));
    }
}
