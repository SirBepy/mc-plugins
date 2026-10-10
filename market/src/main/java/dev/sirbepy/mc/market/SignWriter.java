package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.List;

/** Writes Essentials [Sell] signs: {@code [Sell]} / amount / item / total price. */
final class SignWriter {

    // A sign line fits about 15 characters before it starts overflowing the texture.
    static final int LINE_LENGTH = 15;

    private SignWriter() {
    }

    static String truncate(String text) {
        return text.length() <= LINE_LENGTH ? text : text.substring(0, LINE_LENGTH);
    }

    static void write(World world, Material signBlock, Stall stall, List<Offer> offers) {
        for (int i = 0; i < stall.slots().size(); i++) {
            Stall.Slot slot = stall.slots().get(i);
            Block block = world.getBlockAt(slot.x(), slot.y(), slot.z());
            if (i >= offers.size()) {
                block.setType(Material.AIR, false);
                continue;
            }
            if (block.getType() != signBlock) {
                block.setType(signBlock, false);
            }
            Sign sign = (Sign) block.getState();
            Directional data = (Directional) sign.getBlockData();
            data.setFacing(slot.facing());
            sign.setBlockData(data);

            Offer offer = offers.get(i);
            SignSide front = sign.getSide(Side.FRONT);
            // Line 1 has to arrive dark blue: that is how Essentials recognises a finished [Sell]
            // sign (its signFormatSuccess). The other lines take the stall's dye.
            front.line(0, Component.text("[Sell]", NamedTextColor.DARK_BLUE));
            front.line(1, Component.text(String.valueOf(offer.amount())));
            front.line(2, Component.text(offer.item()));
            front.line(3, Component.text(offer.priceText()));
            front.setColor(stall.color());
            front.setGlowingText(true);
            sign.update(true, false);
        }
    }
}
