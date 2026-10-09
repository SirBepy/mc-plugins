package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.endfight.tier.Tier;
import org.bukkit.entity.Player;

/**
 * Whether {@code player} has already taken a given tier's hoard this week, for the altar menu's
 * lore line. The reward builder implements this once the Dragon Hoard spec lands; until then
 * {@link #NONE_TAKEN} always says "not taken", which is correct wherever rewards are disabled or
 * not yet wired up.
 */
public interface WeeklyHoardInfo {

    boolean takenThisWeek(Player player, Tier tier);

    WeeklyHoardInfo NONE_TAKEN = (player, tier) -> false;
}
