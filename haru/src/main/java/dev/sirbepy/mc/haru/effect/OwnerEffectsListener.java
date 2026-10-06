package dev.sirbepy.mc.haru.effect;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.shop.catalog.SecretStock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Puts the owner's blessings back after a rejoin or respawn, keeps phantoms away while warded, and
 * shuts Haru's shop for a day when the owner dies (the dragon's price, Joe's balance spec 2026-10-06).
 */
public final class OwnerEffectsListener implements Listener {

    public static final long CLOSED_MILLIS = 24 * 60 * 60 * 1000L;

    private final HaruContext ctx;

    public OwnerEffectsListener(HaruContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reapply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Effects added during the respawn event itself are wiped when the new player entity spawns.
        ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), () -> reapply(player));
    }

    private void reapply(Player player) {
        if (!ctx.isOwner(player) || !player.isOnline()) {
            return;
        }
        ctx.blessings().reapply(player, ctx.ownerState());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (ctx.isOwner(event.getEntity())) {
            ctx.ownerState().setClosedUntil(ctx.clock().nowMillis() + CLOSED_MILLIS);
            ctx.store().save();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhantom(PhantomPreSpawnEvent event) {
        if (event.getSpawningEntity() instanceof Player player && ctx.isOwner(player)
                && ctx.ownerState().charmActive(SecretStock.WARD, ctx.clock().nowMillis())) {
            event.setCancelled(true);
        }
    }
}
