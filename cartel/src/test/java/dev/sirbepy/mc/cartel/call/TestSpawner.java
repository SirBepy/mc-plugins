package dev.sirbepy.mc.cartel.call;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Zombie;

/**
 * Spawns marked, named zombies without {@link Cousins#dress}'s gear and AI tweaks, several of which
 * MockBukkit doesn't implement ({@code setRemoveWhenFarAway}, {@code setCanPickupItems}).
 */
public final class TestSpawner {

    private TestSpawner() {
    }

    public static CallManager.Spawner of(Cousins cousins) {
        return (at, name) -> {
            Zombie zombie = at.getWorld().spawn(at, Zombie.class);
            cousins.mark(zombie);
            zombie.customName(Component.text(name));
            return zombie;
        };
    }
}
