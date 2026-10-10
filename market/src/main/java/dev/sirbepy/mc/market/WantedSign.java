package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * Draws the Wanted board's head and WANTED sign, split out of WantedBoard so that class stays the
 * listener and orchestration, this the rendering. {@code currentValue} stays a lazy
 * {@link Supplier} rather than a plain {@code BigDecimal}: it must only ever be evaluated on the
 * open-bounty branch below, same as before the split, since evaluating it with no target would
 * hand the alive-ticks lookup a null uuid.
 */
final class WantedSign {

    private WantedSign() {
    }

    /** Writes the target's head and the WANTED sign, like SignWriter does for a stall's [Sell]
     * signs. Returns the live value now shown on the open-bounty line, or null when the sign drew
     * something else (no spot configured, resting, or claimed), so the caller's {@code shownValue}
     * should stay as it was. */
    static BigDecimal apply(World world, MarketLayout layout, WantedState state, Supplier<BigDecimal> currentValue,
                             double oldHeadShare) {
        MarketLayout.WantedSpot spot = layout.wantedBoard();
        if (spot == null) {
            return null;
        }
        writeHead(world, spot.head(), state);
        return writeSign(world, layout.signBlock(), spot.sign(), state, currentValue, oldHeadShare);
    }

    static void writeHead(World world, Stall.Slot headSpot, WantedState state) {
        if (headSpot == null) {
            return;
        }
        Block block = world.getBlockAt(headSpot.x(), headSpot.y(), headSpot.z());
        if (!state.hasTarget()) {
            // Nothing to show today (nobody qualified, or the board is resting post-sale); same as
            // SignWriter clearing an unused sell-stall slot.
            block.setType(Material.AIR, false);
            return;
        }
        if (block.getType() != Material.PLAYER_WALL_HEAD) {
            block.setType(Material.PLAYER_WALL_HEAD, false);
        }
        // The facing must be set on the state's own block data before update(), same as
        // writeSign/SignWriter: a facing set on the live Block first is overwritten by update()
        // pushing the state's (still-default) block data back onto the block.
        Skull skull = (Skull) block.getState();
        Directional data = (Directional) skull.getBlockData();
        data.setFacing(headSpot.facing());
        skull.setBlockData(data);
        // An offline target may not resolve a skin (no live session-server lookup), which is fine:
        // the id+name alone is enough for matching a turned-in head, same as a cartel trophy's profile.
        skull.setPlayerProfile(Bukkit.createProfile(state.targetId(), state.targetName()));
        skull.update(true, false);
    }

    /** Returns the live value shown on the open-bounty line, or null when the resting/claimed
     * lines were drawn instead (nothing for the caller's {@code shownValue} to remember). */
    static BigDecimal writeSign(World world, Material signBlock, Stall.Slot signSpot, WantedState state,
                                 Supplier<BigDecimal> currentValue, double oldHeadShare) {
        if (signSpot == null) {
            return null;
        }
        Block block = world.getBlockAt(signSpot.x(), signSpot.y(), signSpot.z());
        if (block.getType() != signBlock) {
            block.setType(signBlock, false);
        }
        Sign sign = (Sign) block.getState();
        Directional data = (Directional) sign.getBlockData();
        data.setFacing(signSpot.facing());
        sign.setBlockData(data);

        SignSide front = sign.getSide(Side.FRONT);
        front.line(0, Component.text("WANTED", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        BigDecimal shown = null;
        if (!state.hasTarget()) {
            front.line(1, Component.text(state.cooldown() ? "Next bounty" : "No bounty"));
            front.line(2, Component.text(state.cooldown() ? "tomorrow" : "today"));
            front.line(3, Component.empty());
        } else if (state.claimed()) {
            front.line(1, Component.text(SignWriter.truncate(state.targetName())));
            front.line(2, Component.text("Claimed by"));
            front.line(3, Component.text(SignWriter.truncate(state.claimedByName())));
        } else {
            BigDecimal value = currentValue.get();
            BigDecimal old = Market.share(value, oldHeadShare);
            front.line(1, Component.text(SignWriter.truncate(state.targetName())));
            front.line(2, Component.text(SignWriter.truncate("Fresh: $" + value.toBigInteger())));
            front.line(3, Component.text(SignWriter.truncate("Old: $" + old.toBigInteger())));
            shown = value;
        }
        sign.update(true, false);
        return shown;
    }
}
