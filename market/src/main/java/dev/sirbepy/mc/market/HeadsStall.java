package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Heads stall: 4 wall signs, each showing one of today's wanted More Mob Heads mobs, written
 * like SignWriter writes a [Sell] sign. Replaces the Head collector NPC (a Mannequin for Java
 * players, a villager for Bedrock), which existed only because vanilla spawn protection swallowed
 * block clicks but not entity clicks; spawn protection is off on live now and Tweaks' spawn guard
 * lets a sign be clicked instead, so a block-click listener reaches this stall same as the NPC did
 * (Joe, 2026-10-09: "lets remove the NPC that buys the heads, i just wanted a stall that buys heads").
 *
 * <p>Right-clicking any of the 4 signs sells whichever wanted head the player holds, not just that
 * particular sign's mob: the old NPC never distinguished either (there was only ever one of it), so
 * a shared pool of 4 keeps the exact same buying behaviour while the signs are just today's rotating
 * display.
 */
public final class HeadsStall implements Listener {

    /** Pays a player; false when no economy took the money. */
    @FunctionalInterface
    public interface Payout {
        boolean pay(Player player, double amount);
    }

    // The heads the stalls' [Sell] signs buy, so this stall can point there instead.
    private static final Set<Material> SIGN_HEADS = Set.of(Material.ZOMBIE_HEAD, Material.SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD, Material.WITHER_SKELETON_SKULL, Material.DRAGON_HEAD);
    // A sign line fits about 15 characters before it starts overflowing the texture.
    private static final int LINE_LENGTH = 15;

    private final Market market;
    private final Supplier<MarketConfig> config;
    private final Payout payout;
    private final NamespacedKey oldNpcMarker;

    public HeadsStall(Plugin plugin, Market market, Supplier<MarketConfig> config, Payout payout) {
        this.market = market;
        this.config = config;
        this.payout = payout;
        // The exact PDC key HeadCollector (removed 2026-10-09) tagged its Mannequin/villager bodies
        // with, so a server upgraded from that version can clean up the leftover NPCs.
        this.oldNpcMarker = new NamespacedKey(plugin, "head-collector");
    }

    /** Writes today's wanted mobs onto the Heads stall's signs and clears any spare spot, like SignWriter. */
    public void apply(World world) {
        MarketLayout.HeadStall stall = market.layout().headStall();
        if (stall == null) {
            return;
        }
        Material signBlock = market.layout().signBlock();
        List<MobHeads.Mob> wanted = market.wantedMobs();
        List<Stall.Slot> slots = stall.slots();
        for (int i = 0; i < slots.size(); i++) {
            Stall.Slot slot = slots.get(i);
            Block block = world.getBlockAt(slot.x(), slot.y(), slot.z());
            if (i >= wanted.size()) {
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

            MobHeads.Mob mob = wanted.get(i);
            SignSide front = sign.getSide(Side.FRONT);
            // Line 1 must not be Essentials' dark blue "[Sell]"/"[Buy]", or it would grab this sign too.
            front.line(0, Component.text(truncate(mob.name())).decorate(TextDecoration.BOLD));
            front.line(1, Component.text("head"));
            front.line(2, Component.text(truncate(priceText(mob))));
            front.line(3, Component.text("Right-click"));
            front.setColor(stall.color());
            front.setGlowingText(true);
            sign.update(true, false);
        }
    }

    private String priceText(MobHeads.Mob mob) {
        List<BigDecimal> prices = mob.heads().stream().map(market::headPrice).toList();
        BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElseThrow();
        return PriceList.range(min, max);
    }

    private static String truncate(String text) {
        return text.length() <= LINE_LENGTH ? text : text.substring(0, LINE_LENGTH);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        MarketLayout.HeadStall stall = market.layout().headStall();
        if (stall == null || !atASlot(stall, event.getClickedBlock())) {
            return;
        }
        // No sign-edit screen opens on our own signs.
        event.setCancelled(true);
        sell(event.getPlayer());
    }

    private static boolean atASlot(MarketLayout.HeadStall stall, Block block) {
        if (block == null) {
            return false;
        }
        for (Stall.Slot slot : stall.slots()) {
            if (slot.x() == block.getX() && slot.y() == block.getY() && slot.z() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    /** Buys the whole stack in the player's main hand if it is a head wanted today, and says why not otherwise. */
    void sell(Player player) {
        var messages = config.get().messages();
        var wanted = Placeholder.unparsed("mobs", wantedText());
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (SIGN_HEADS.contains(stack.getType())) {
            messages.send(player, "heads-sign-head", wanted);
            return;
        }
        MobHeads.Head head = market.heads().identify(stack);
        if (head == null) {
            messages.send(player, stack.getType() == Material.PLAYER_HEAD ? "heads-player-head" : "heads-hold", wanted);
            return;
        }
        var name = Placeholder.unparsed("head", head.name());
        BigDecimal each = market.headPrice(head);
        if (each == null) {
            messages.send(player, "heads-not-today", name, wanted);
            return;
        }
        int amount = stack.getAmount();
        BigDecimal total = each.multiply(BigDecimal.valueOf(amount));
        if (!payout.pay(player, total.doubleValue())) {
            messages.send(player, "heads-closed");
            return;
        }
        player.getInventory().setItemInMainHand(null);
        messages.send(player, "heads-sold", name, Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("total", PriceList.money(total)));
    }

    /** Today's wanted mobs as words, e.g. {@code breeze, allay and cow}. */
    String wantedText() {
        var names = market.wantedMobs().stream().map(m -> m.name().toLowerCase(Locale.ROOT)).toList();
        if (names.isEmpty()) {
            return "nothing";
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    /** Removes any HeadCollector NPC left over from before the sign-stall switch (Joe, 2026-10-09). */
    public void removeOldNpcs(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(oldNpcMarker, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(oldNpcMarker, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
    }
}
