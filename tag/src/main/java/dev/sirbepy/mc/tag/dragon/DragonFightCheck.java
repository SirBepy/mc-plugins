package dev.sirbepy.mc.tag.dragon;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderDragon;

import java.util.UUID;

/**
 * Whether a player is currently an active Ender Dragon fighter: read off the fighter
 * list endfight's own {@link DragonFighters} already keeps on the live dragon's PDC under
 * {@code endfight:dragon-fighters}. Read-only here - tag never adds a fighter itself, it only
 * consumes the tally endfight (and guild) already maintain for fight-credit tracking, the same
 * cross-plugin-key pattern guild's {@code DragonSlayers} uses for its own read of that tally.
 */
public final class DragonFightCheck {

    private final DragonFighters fighters =
            new DragonFighters(new NamespacedKey("endfight", "dragon-fighters"), System::currentTimeMillis);

    public boolean isFighting(UUID uuid) {
        for (EnderDragon dragon : DragonFighters.liveDragonsInTheEnd()) {
            if (fighters.fighters(dragon).contains(uuid)) {
                return true;
            }
        }
        return false;
    }
}
