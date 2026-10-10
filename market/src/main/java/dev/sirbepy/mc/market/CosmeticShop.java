package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * Sells Cosmetic Tokens: right-click the sign, pay the price, get one {@link CosmeticToken}. What
 * the token later does is all in {@code /cosmetic} ({@link CosmeticRequests}); this class only
 * ever sells the token itself.
 */
public final class CosmeticShop implements Listener {

    @FunctionalInterface
    public interface Charge {
        /** False when there is no economy, the balance is short, or the withdrawal itself failed. */
        boolean charge(Player player, double amount);
    }

    @FunctionalInterface
    public interface Balance {
        /** The player's balance, or null when no economy is registered. */
        Double balance(Player player);
    }

    private final MarketLayout layout;
    private final Supplier<MarketConfig> config;
    private final CosmeticToken token;
    private final Charge charge;
    private final Balance balance;

    public CosmeticShop(MarketLayout layout, Supplier<MarketConfig> config, CosmeticToken token, Charge charge, Balance balance) {
        this.layout = layout;
        this.config = config;
        this.token = token;
        this.charge = charge;
        this.balance = balance;
    }

    public void apply(World world) {
        MarketLayout.Shop shop = layout.shops().get("cosmetics");
        if (shop == null || shop.signs().isEmpty()) {
            return;
        }
        Stall.Slot slot = shop.signs().get(0);
        Block block = world.getBlockAt(slot.x(), slot.y(), slot.z());
        Material signBlock = layout.signBlock();
        if (block.getType() != signBlock) {
            block.setType(signBlock, false);
        }
        Sign sign = (Sign) block.getState();
        Directional data = (Directional) sign.getBlockData();
        data.setFacing(slot.facing());
        sign.setBlockData(data);

        SignSide front = sign.getSide(Side.FRONT);
        front.line(0, Component.text("Cosmetic").decorate(TextDecoration.BOLD));
        front.line(1, Component.text("Token"));
        front.line(2, Component.text(SignWriter.truncate(PriceList.wholeDollars(config.get().cosmeticsPrice()))));
        front.line(3, Component.text("Right-click"));
        front.setColor(shop.color());
        front.setGlowingText(true);
        sign.update(true, false);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        MarketLayout.Shop shop = layout.shops().get("cosmetics");
        Stall.Slot sign = shop == null || shop.signs().isEmpty() ? null : shop.signs().get(0);
        Block block = event.getClickedBlock();
        if (sign == null || block == null || block.getX() != sign.x() || block.getY() != sign.y() || block.getZ() != sign.z()) {
            return;
        }
        event.setCancelled(true);
        buy(event.getPlayer());
    }

    /** Sells the player one Cosmetic Token. */
    void buy(Player player) {
        var messages = config.get().messages();
        BigDecimal price = config.get().cosmeticsPrice();
        if (!charge.charge(player, price.doubleValue())) {
            Double bal = balance.balance(player);
            if (bal == null) {
                messages.send(player, "cosmetics-closed");
            } else {
                messages.send(player, "cosmetics-poor", Placeholder.unparsed("price", PriceList.wholeDollars(price)),
                        Placeholder.unparsed("balance", PriceList.wholeDollars(BigDecimal.valueOf(bal))));
            }
            return;
        }
        token.giveTo(player);
        messages.send(player, "cosmetics-sold");
    }

}
