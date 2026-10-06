package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A dying player's balls never go into the normal death-drop pile: they're pulled out and dropped
 * separately as their own item entities (which {@link BallItemLifecycleListener} then marks
 * unlimited-lifetime/invulnerable/glowing via {@code ItemSpawnEvent}, same as any other ball drop).
 * A dead player can carry balls from any of the 3 sets at once, so each one is routed to its own set's
 * registry individually.
 */
public final class BallDeathListener implements Listener {

    private final BallSets sets;

    public BallDeathListener(BallSets sets) {
        this.sets = sets;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> balls = new ArrayList<>();
        event.getDrops().removeIf(drop -> {
            if (sets.items().isBall(drop)) {
                balls.add(drop);
                return true;
            }
            return false;
        });
        for (ItemStack ball : balls) {
            BallRegistry registry = sets.registryFor(ball).orElseThrow();
            int star = sets.items().starOf(ball).orElseThrow();
            boolean stone = sets.items().isStoneItem(ball);
            Item entity = event.getEntity().getWorld().dropItem(event.getEntity().getLocation(), ball);
            registry.markDropped(star, entity, stone);
        }
    }
}
