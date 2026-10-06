package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * A ball sitting in the world as an item entity never despawns, never merges with another stack
 * (keeping the registry's one-entity-per-star invariant simple), and is immune to fire/lava/
 * explosion/cactus damage. If it's destroyed anyway (the void, an explosion that removes it outright,
 * a plugin, whatever), it's re-scattered rather than lost for good.
 */
public final class BallItemLifecycleListener implements Listener {

    // graves-v4.1.2's own pickup selector requires the EXACT vanilla death-drop NBT
    // ({Age:0s,PickupDelay:40s} or {Age:1s,PickupDelay:39s} - see
    // data/graves/function/creation/items/pick_up_items_with_age_0.mcfunction and
    // .../pick_up_items.mcfunction in server/datapacks/graves-v4.1.2.zip). 30 never matches either, so
    // graves leaves every ball drop alone, death-drop or not. It must stay above 0: at 0 the player who
    // pressed Q picks the ball straight back up the same tick, so a ball could never be dropped.
    static final int PICKUP_DELAY = 30;

    // Causes that are a normal, already-handled part of a ball's life, not a loss worth reacting to.
    private static final Set<EntityRemoveEvent.Cause> IGNORED_REMOVALS = EnumSet.of(
            EntityRemoveEvent.Cause.PICKUP, EntityRemoveEvent.Cause.PLAYER_QUIT,
            EntityRemoveEvent.Cause.UNLOAD, EntityRemoveEvent.Cause.PLUGIN,
            EntityRemoveEvent.Cause.TRANSFORMATION, EntityRemoveEvent.Cause.DISCARD,
            EntityRemoveEvent.Cause.DROP
    );

    private final BallRegistry registry;

    public BallItemLifecycleListener(BallRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!registry.isBall(item.getItemStack())) {
            return;
        }
        item.setUnlimitedLifetime(true);
        item.setInvulnerable(true);
        item.setGlowing(true);
        item.setPickupDelay(PICKUP_DELAY);
        item.setCanMobPickup(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (registry.isBall(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (registry.isBall(event.getEntity().getItemStack()) || registry.isBall(event.getTarget().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (IGNORED_REMOVALS.contains(event.getCause()) || !(event.getEntity() instanceof Item item)) {
            return;
        }
        registry.starOf(item.getItemStack()).ifPresent(star -> {
            if (registry.isDroppedAs(star, item.getUniqueId())) {
                var lastSummon = registry.lastSummon();
                if (lastSummon != null) {
                    registry.scatterOne(star, lastSummon, false);
                }
            }
        });
    }
}
