package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.registry.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

/**
 * The Overgrown Sentinel's discovery/summon hook, the {@code overgrown_sentinel_discovery}
 * {@code summon-hook} its YAML names: breaking a Moss Block has a small chance to drop a Sentinel
 * Seed, and right-clicking with the seed in the main hand spends it to summon a Normal-tier
 * Sentinel at the player's own feet - no built arena exists yet (ImMijo is building one himself,
 * a separate follow-up concern), so this never references a fixed location. {@link SummonCooldown}
 * mirrors {@code guild.listener.FarmGuard}'s "refuse and keep tracking" shape so the seed can't be
 * chain-farmed into repeat summons.
 */
public final class SentinelDiscovery implements Listener {

    /** Rare enough that a moss-covered build isn't spammed with seeds, common enough to be found
     * without a dedicated farming loop - same order of magnitude as other rare-drop rates in this
     * repo's nature-flavored content. */
    public static final double MOSS_DROP_CHANCE = 0.005;
    public static final long COOLDOWN_MILLIS = 30 * 60_000L;

    private final NamespacedKey seedKey;
    private final SentinelFight fight;
    private final SummonCooldown cooldown;
    private final Random random;

    public SentinelDiscovery(JavaPlugin plugin, SentinelFight fight, SummonCooldown cooldown, Random random) {
        this.seedKey = new NamespacedKey(plugin, "sentinel_seed");
        this.fight = fight;
        this.cooldown = cooldown;
        this.random = random;
    }

    public ItemStack createSeed() {
        ItemStack item = new ItemStack(Material.OAK_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Sentinel Seed", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to wake the Overgrown Sentinel.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(seedKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSeed(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(seedKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler
    public void onMossBroken(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.MOSS_BLOCK || event.isCancelled()) {
            return;
        }
        if (random.nextDouble() < MOSS_DROP_CHANCE) {
            event.getPlayer().getInventory().addItem(createSeed());
            event.getPlayer().sendMessage(Component.text("You feel something stir beneath the moss...",
                    NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onSeedUsed(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isSeed(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!cooldown.tryConsume(player.getUniqueId())) {
            long minutesLeft = (cooldown.remainingMillis(player.getUniqueId()) / 60_000L) + 1;
            player.sendMessage(Component.text("The Sentinel is still resting. Try again in about "
                    + minutesLeft + " minute(s).", NamedTextColor.GRAY));
            return;
        }
        item.subtract();
        fight.spawn(player.getLocation(), Tier.NORMAL);
        player.sendMessage(Component.text("The ground trembles - the Overgrown Sentinel rises!",
                NamedTextColor.DARK_GREEN));
    }
}
