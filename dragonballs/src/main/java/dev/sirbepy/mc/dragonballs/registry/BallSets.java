package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The 3 independent per-set registries (overworld/nether/end), plus lookup by a ball item's
 * own PDC set so a listener never has to guess which registry owns a given ball. Overworld and
 * Nether are always enabled; the End is gated by {@link #isEnabled} (only live while the server
 * allows the End and its world is loaded).
 */
public final class BallSets {

    private final Map<BallSet, BallRegistry> registries;
    private final BallItems items;
    private final Supplier<DragonBallsConfig> config;

    public BallSets(BallRegistry overworld, BallRegistry nether, BallRegistry end, BallItems items,
                     Supplier<DragonBallsConfig> config) {
        this.registries = new EnumMap<>(BallSet.class);
        registries.put(BallSet.OVERWORLD, overworld);
        registries.put(BallSet.NETHER, nether);
        registries.put(BallSet.END, end);
        this.items = items;
        this.config = config;
    }

    public BallRegistry registry(BallSet set) {
        return registries.get(set);
    }

    /** All 3, in {@link BallSet} declaration order (overworld, nether, end). */
    public List<BallRegistry> all() {
        return List.of(registries.get(BallSet.OVERWORLD), registries.get(BallSet.NETHER), registries.get(BallSet.END));
    }

    public BallItems items() {
        return items;
    }

    /** The registry that owns {@code item}, or empty if it isn't a ball at all. */
    public Optional<BallRegistry> registryFor(ItemStack item) {
        if (!items.isBall(item)) {
            return Optional.empty();
        }
        return Optional.of(registry(items.setOf(item)));
    }

    /** Overworld/Nether are always enabled. The End only ever scatters, gives out balls, or shows up
     * in status/radar-shop listings while {@code Bukkit.getAllowEnd()} is true AND its world is
     * loaded - exactly the condition under which a player could actually reach it. */
    public boolean isEnabled(BallSet set) {
        if (set != BallSet.END) {
            return true;
        }
        World world = set.world(config.get());
        return Bukkit.getAllowEnd() && world != null;
    }
}
