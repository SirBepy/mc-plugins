package dev.sirbepy.mc.core.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Decides who sees an NPC's skinned Mannequin and who sees its stand-in body (a villager with a floating
 * title): Bedrock players never get the Mannequin, and when the skin comes from the server pack, neither
 * do Java players whose pack declined or failed, since they would see a missing-texture Mannequin.
 * Unknown counts as loaded: a cached pack is answered during the configuration phase, before any
 * play-phase status event, so waiting for a "loaded" event hid Haru's Mannequin from ImMijo after a
 * rejoin (2026-10-06). Register it as a listener; {@code changed} runs whenever a player's view may differ.
 */
public final class MannequinViewers implements Listener {

    private final Predicate<UUID> bedrock;
    private final Consumer<Player> changed;
    private final Set<UUID> packFailed = new HashSet<>();

    public MannequinViewers(Predicate<UUID> bedrock, Consumer<Player> changed) {
        this.bedrock = bedrock;
        this.changed = changed;
    }

    public boolean seesMannequin(Player player, boolean needsPack) {
        return !bedrock.test(player.getUniqueId()) && (!needsPack || !packFailed.contains(player.getUniqueId()));
    }

    /** Records whether the player's client failed or declined the server pack, then updates their view. */
    public void setPackFailed(Player player, boolean failed) {
        if (failed) {
            packFailed.add(player.getUniqueId());
        } else {
            packFailed.remove(player.getUniqueId());
        }
        changed.accept(player);
    }

    /** Hiding an entity from a player lasts only for their session, so it is set again on every join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getPlayer().getResourcePackStatus();
        setPackFailed(event.getPlayer(), status != null && failed(status));
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            setPackFailed(event.getPlayer(), false);
        } else if (failed(event.getStatus())) {
            setPackFailed(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packFailed.remove(event.getPlayer().getUniqueId());
    }

    private static boolean failed(PlayerResourcePackStatusEvent.Status status) {
        return switch (status) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> true;
            default -> false;
        };
    }

    /** Shows or hides {@code entity} for {@code player}; a missing entity is skipped. */
    public static void show(Plugin plugin, Player player, Entity entity, boolean visible) {
        if (entity == null) {
            return;
        }
        if (visible) {
            player.showEntity(plugin, entity);
        } else {
            player.hideEntity(plugin, entity);
        }
    }

    /** Turns {@code body}'s head and body toward {@code target}'s eyes. */
    public static void face(LivingEntity body, Player target) {
        Vector look = target.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector());
        Location facing = body.getEyeLocation().clone().setDirection(look);
        body.setRotation(facing.getYaw(), facing.getPitch());
        body.setBodyYaw(facing.getYaw());
    }
}
