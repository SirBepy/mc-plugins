package dev.sirbepy.mc.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.sirbepy.mc.core.item.KillStamp;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * The pure checks a head sale turns on, split out of WantedBoard's {@code sell} so that class stays
 * the listener and orchestration, this the fresh/old and identity rules. {@code sell} itself (the
 * refusal cascade, the payout, the state claim, the re-render, the broadcast) stays on WantedBoard:
 * it is tangled with that class's own fields (state, config, payout, broadcast) in a way that
 * doesn't separate into a clean second seam the way rendering did.
 */
final class WantedSale {

    private WantedSale() {
    }

    /** A kill timestamped at or after the bounty's postedAt; a head with no stamp at all never qualifies. */
    static boolean isFresh(ItemStack stack, WantedState state) {
        if (state.postedAt() == null) {
            return false;
        }
        return KillStamp.killedAt(stack).map(at -> !at.isBefore(state.postedAt())).orElse(false);
    }

    /** The life a fresh head's kill ended: its own stamp, else the target's latest death since posting, else now. */
    static long freshAliveTicks(ItemStack stack, WantedState state, NamespacedKey aliveKey, ToLongFunction<UUID> aliveTicks) {
        Long stamped = stack.getItemMeta().getPersistentDataContainer().get(aliveKey, PersistentDataType.LONG);
        if (stamped != null) {
            return stamped;
        }
        return state.died() ? state.diedStreakTicks() : aliveTicks.applyAsLong(state.targetId());
    }

    static void takeOneHead(Player player, ItemStack stack) {
        if (stack.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        ItemStack remaining = stack.clone();
        remaining.setAmount(stack.getAmount() - 1);
        player.getInventory().setItemInMainHand(remaining);
    }

    /** Matches the target by profile UUID; only a head with no id at all falls back to the name. */
    static boolean matchesTarget(SkullMeta skull, WantedState state) {
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return false;
        }
        if (profile.getId() != null) {
            return profile.getId().equals(state.targetId());
        }
        return profile.getName() != null && profile.getName().equalsIgnoreCase(state.targetName());
    }
}
