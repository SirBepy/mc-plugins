package dev.sirbepy.mc.minibosses.sovereign;

import dev.sirbepy.mc.minibosses.arena.SummonGate;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.summon.TierPicker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The Void Sovereign's discovery/summon hook, the {@code void_sovereign.yml} {@code summon-hook}
 * names ({@code void_sovereign_discovery}): killing a wild (ordinary, non-boss) Enderman has a
 * small chance to drop a Void Shard, and right-clicking with the Shard in the main hand opens the
 * {@link TierPicker}; picking a tier spends it to summon the Sovereign at the center of its arena,
 * and not at all until one is set (the {@link SummonGate} decides). {@link
 * SovereignFight#isOurs} tells a wild Enderman's death apart from a real Sovereign fight ending
 * (both are the same entity type), so a kill credited to the fight itself never also rolls a
 * shard. {@link SummonCooldown} mirrors {@code tyrant.SummonCooldown}/{@code
 * sentinel.SummonCooldown}'s "refuse and keep tracking" shape so the Shard can't be chain-farmed
 * into repeat summons.
 */
public final class SovereignDiscovery implements Listener {

    /** Same order of magnitude as the Tyrant's own wild-mob roll (0.02): an Enderman is a common
     * incidental kill (End travel, farms), not a dedicated hunt, so this stays rare rather than
     * flooding summons. */
    public static final double ENDERMAN_DROP_CHANCE = 0.02;
    public static final long COOLDOWN_MILLIS = 30 * 60_000L;

    private final NamespacedKey shardKey;
    private final SovereignFight fight;
    private final SummonCooldown cooldown;
    private final Random random;
    private final TierPicker picker;
    private final SummonGate gate;

    public SovereignDiscovery(JavaPlugin plugin, SovereignFight fight, SummonCooldown cooldown, Random random,
            TierPicker picker, SummonGate gate) {
        this.picker = picker;
        this.gate = gate;
        this.shardKey = new NamespacedKey(plugin, "void_shard");
        this.fight = fight;
        this.cooldown = cooldown;
        this.random = random;
    }

    public ItemStack createShard() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Void Shard", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to wake the Void Sovereign.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isShard(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler
    public void onEndermanKilled(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Enderman enderman) || fight.isOurs(enderman)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (random.nextDouble() < ENDERMAN_DROP_CHANCE) {
            killer.getInventory().addItem(createShard());
            killer.sendMessage(Component.text("A shard of pure void clings to your blade...",
                    NamedTextColor.DARK_PURPLE));
        }
    }

    @EventHandler
    public void onShardUsed(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isShard(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (refuseOnCooldown(player) || where(player).isEmpty()) {
            return;
        }
        picker.open(player, SovereignFight.GUARDIAN_ID, NamedTextColor.DARK_PURPLE, tier -> summon(player, tier));
    }

    /** Spends the shard in the main hand and summons at {@code tier}. Everything is checked again
     * here because the tier picker was open in between: the shard may have moved, another summon
     * may have started the cooldown, and a forged click must not reach a locked tier. */
    public boolean summon(Player player, Tier tier) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isShard(item) || !picker.unlocked(player.getUniqueId(), SovereignFight.GUARDIAN_ID, tier)
                || refuseOnCooldown(player)) {
            return false;
        }
        Optional<Location> at = where(player);
        if (at.isEmpty()) {
            return false;
        }
        cooldown.tryConsume(player.getUniqueId());
        item.subtract();
        fight.spawn(at.get(), tier).ifPresent(boss -> gate.risen(SovereignFight.GUARDIAN_ID, boss));
        player.sendMessage(Component.text("The void folds inward - the Void Sovereign emerges!",
                NamedTextColor.DARK_PURPLE));
        return true;
    }

    private Optional<Location> where(Player player) {
        return gate.where(player, SovereignFight.GUARDIAN_ID, "The shard goes dull. The void will not open here.",
                "The shard stays dull. Its ground has not been prepared yet.");
    }

    private boolean refuseOnCooldown(Player player) {
        long left = cooldown.remainingMillis(player.getUniqueId());
        if (left <= 0) {
            return false;
        }
        player.sendMessage(Component.text("The void is not yet still. Try again in about "
                + (left / 60_000L + 1) + " minute(s).", NamedTextColor.GRAY));
        return true;
    }
}
