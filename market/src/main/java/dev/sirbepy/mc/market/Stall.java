package dev.sirbepy.mc.market;

import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;

import java.util.List;

/** A spawn-market stall: where its [Sell] signs go and everything it might buy on a given day. */
public record Stall(String id, String name, DyeColor color, List<Slot> slots, List<Offer> buys) {

    /** A wall-sign spot; {@code facing} is the direction the sign's text faces. */
    public record Slot(int x, int y, int z, BlockFace facing) {
    }
}
