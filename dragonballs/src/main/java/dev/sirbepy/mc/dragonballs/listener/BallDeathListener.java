package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
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
 */
public final class BallDeathListener implements Listener {

    private final BallRegistry registry;

    public BallDeathListener(BallRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> balls = new ArrayList<>();
        event.getDrops().removeIf(drop -> {
            if (registry.isBall(drop)) {
                balls.add(drop);
                return true;
            }
            return false;
        });
        for (ItemStack ball : balls) {
            int star = registry.starOf(ball).orElseThrow();
            boolean stone = registry.items().isStoneItem(ball);
            Item entity = event.getEntity().getWorld().dropItem(event.getEntity().getLocation(), ball);
            registry.markDropped(star, entity, stone);
        }
    }
}
