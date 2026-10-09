package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The drop/pickup half of a ball's lifecycle: DROPPED while it's a free-standing item, HELD once a
 * player has it. Also the one place that rejects a stray duplicate ball item (one the owning set's
 * registry doesn't actually have DROPPED under this entity), per the dupe-protection invariant. The
 * "found" broadcast names the set for Nether/End; the overworld wording is unchanged.
 */
public final class BallPickupListener implements Listener {

    private final BallSets sets;
    private final HeldBallCheck heldBallCheck;
    private final FoundAnnouncer announcer;
    // Who last dropped a given ball and when, for the self-dropped announce suppression. Also fed
    // by HeldBallCheck.recordSelfDrop when it silently adopts a stray for its former holder, so that
    // holder picking the adoption back up is treated the same as picking up their own drop.
    private final RecentSelfAction recentlyDropped = new RecentSelfAction();

    public BallPickupListener(BallSets sets, Supplier<DragonBallsConfig> config, HeldBallCheck heldBallCheck) {
        this(sets, heldBallCheck, (finder, set, star) -> announceFound(config.get(), finder, set, star));
    }

    BallPickupListener(BallSets sets, HeldBallCheck heldBallCheck, FoundAnnouncer announcer) {
        this.sets = sets;
        this.heldBallCheck = heldBallCheck;
        this.announcer = announcer;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        sets.items().starOf(stack).ifPresent(star -> {
            BallRegistry registry = sets.registryFor(stack).orElseThrow();
            boolean stone = sets.items().isStoneItem(stack);
            registry.markDropped(star, event.getItemDrop(), stone);
            recentlyDropped.record(registry.set(), star, event.getPlayer().getUniqueId());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        sets.items().starOf(stack).ifPresent(star -> {
            BallRegistry registry = sets.registryFor(stack).orElseThrow();
            if (!(event.getEntity() instanceof Player player)) {
                // Hoppers, allays, foxes, minecart hoppers: balls never leave player hands this way.
                event.setCancelled(true);
                return;
            }
            // A ball its online holder no longer carries left their inventory without an event (the
            // graves datapack respawns whatever sat in a slot it refills as a brand-new entity), so this
            // untracked entity IS that ball, not a dupe.
            UUID formerHolder = heldBallCheck.isAdrift(registry, star)
                    ? registry.get(star).orElseThrow().holderId() : null;
            if (formerHolder == null && !registry.isDroppedAs(star, itemEntity.getUniqueId())) {
                // Registry disagrees about where this star actually is: a stray dupe (creative,
                // duplication glitch). Deny the pickup and delete the impostor rather than hand it out.
                event.setCancelled(true);
                itemEntity.remove();
                return;
            }
            boolean stone = sets.items().isStoneItem(stack);
            registry.markHeld(star, player, stone);
            if (!player.getUniqueId().equals(formerHolder) && !recentlyDropped.isRecent(registry.set(), star, player.getUniqueId())) {
                announcer.announce(player, registry.set(), star);
            }
        });
    }

    /** The server-wide "found" line, worded per set; also used for a floating ball hit off its spot. */
    static void announceFound(DragonBallsConfig config, Player finder, BallSet set, int star) {
        config.messages().send(finder.getServer(), foundMessageKey(set),
                Placeholder.unparsed("player", finder.getName()),
                Placeholder.unparsed("star", String.valueOf(star)));
    }

    private static String foundMessageKey(BallSet set) {
        return switch (set) {
            case OVERWORLD -> "found";
            case NETHER -> "found-nether";
            case END -> "found-end";
        };
    }

    /** Lets {@link HeldBallCheck} count a stray it silently adopts for its former holder as that
     * holder's own drop, so that holder picking it back up doesn't get a "found" broadcast either,
     * the same as a real {@link PlayerDropItemEvent} would suppress. */
    void recordSelfDrop(BallSet set, int star, UUID holder) {
        recentlyDropped.record(set, star, holder);
    }
}
