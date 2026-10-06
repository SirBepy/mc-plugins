package dev.sirbepy.mc.haru.npc;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Nothing in the world can hurt, burn, convert (lightning witch, zombie villager), leash or re-skill
 * Haru. Anything that slips past (a /kill) is undone by {@link HaruNpc#ensure} re-creating him.
 */
public final class HaruProtectionListener implements Listener {

    private final HaruNpc npc;

    public HaruProtectionListener(HaruNpc npc) {
        this.npc = npc;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransform(EntityTransformEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (npc.isHaru(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        npc.removeStrayCopies(event.getEntities());
        if (event.getEntities().stream().anyMatch(npc::isHaru)) {
            npc.applyVisibilityToAll();
        }
    }

    /** Hiding an entity from a player lasts only for their session, so it is set again on every join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getPlayer().getResourcePackStatus();
        npc.setPackFailed(event.getPlayer(), status != null && failed(status));
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            npc.setPackFailed(event.getPlayer(), false);
        } else if (failed(event.getStatus())) {
            npc.setPackFailed(event.getPlayer(), true);
        }
    }

    private static boolean failed(PlayerResourcePackStatusEvent.Status status) {
        return switch (status) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> true;
            default -> false;
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        npc.setPackFailed(event.getPlayer(), false);
    }
}
