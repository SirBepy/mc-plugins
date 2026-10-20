package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.minibosses.arena.SummonGate;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.summon.TierPicker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
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
 * The Ashen Tyrant's discovery/summon hook, the {@code ashen_tyrant.yml} {@code summon-hook}
 * names ({@code ashen_tyrant_discovery}): killing a wild (ordinary, non-boss) Wither Skeleton -
 * the mob that actually spawns inside a real Nether Fortress - has a small chance to drop an Ashen
 * Ember, and right-clicking with the Ember in the main hand opens the {@link TierPicker}; picking a
 * tier spends it to summon the Tyrant at the center of its arena, and not at all until one is set
 * (the {@link SummonGate} decides). {@link TyrantFight#isOurs} tells a wild Wither
 * Skeleton's death apart from a real Tyrant fight ending (both are the same entity type), so a kill
 * credited to the fight itself never also rolls a seed. {@link SummonCooldown} mirrors
 * {@code sentinel.SummonCooldown}'s "refuse and keep tracking" shape so the Ember can't be
 * chain-farmed into repeat summons.
 */
public final class TyrantDiscovery implements Listener {

    /** Slightly more generous than the Sentinel's Moss Block roll (0.005): a wild Wither Skeleton
     * kill is a deliberate, sought-out event (you have to go find and fight one in a fortress),
     * not an incidental block break, so a slightly higher rate still keeps it rare without being
     * frustrating to farm for on purpose. */
    public static final double WITHER_SKELETON_DROP_CHANCE = 0.02;
    public static final long COOLDOWN_MILLIS = 30 * 60_000L;

    private final NamespacedKey emberKey;
    private final TyrantFight fight;
    private final SummonCooldown cooldown;
    private final Random random;
    private final TierPicker picker;
    private final SummonGate gate;

    public TyrantDiscovery(JavaPlugin plugin, TyrantFight fight, SummonCooldown cooldown, Random random,
            TierPicker picker, SummonGate gate) {
        this.picker = picker;
        this.gate = gate;
        this.emberKey = new NamespacedKey(plugin, "ashen_ember");
        this.fight = fight;
        this.cooldown = cooldown;
        this.random = random;
    }

    public ItemStack createEmber() {
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ashen Ember", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to wake the Ashen Tyrant.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(emberKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isEmber(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(emberKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler
    public void onWitherSkeletonKilled(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton skeleton) || fight.isOurs(skeleton)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (random.nextDouble() < WITHER_SKELETON_DROP_CHANCE) {
            killer.getInventory().addItem(createEmber());
            killer.sendMessage(Component.text("An ember clings to your blade, still warm with ancient rage...",
                    NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEmberUsed(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isEmber(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (refuseOnCooldown(player) || where(player).isEmpty()) {
            return;
        }
        picker.open(player, TyrantFight.GUARDIAN_ID, NamedTextColor.RED, tier -> summon(player, tier));
    }

    /** Spends the ember in the main hand and summons at {@code tier}. Everything is checked again
     * here because the tier picker was open in between: the ember may have moved, another summon
     * may have started the cooldown, and a forged click must not reach a locked tier. */
    public boolean summon(Player player, Tier tier) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isEmber(item) || !picker.unlocked(player.getUniqueId(), TyrantFight.GUARDIAN_ID, tier)
                || refuseOnCooldown(player)) {
            return false;
        }
        Optional<Location> at = where(player);
        if (at.isEmpty()) {
            return false;
        }
        cooldown.tryConsume(player.getUniqueId());
        item.subtract();
        fight.spawn(at.get(), tier).ifPresent(boss -> gate.risen(TyrantFight.GUARDIAN_ID, boss));
        player.sendMessage(Component.text("The ashes rise - the Ashen Tyrant awakens!",
                NamedTextColor.RED));
        return true;
    }

    private Optional<Location> where(Player player) {
        return gate.where(player, TyrantFight.GUARDIAN_ID, "The ember gutters out. This is not his ground.",
                "The ember stays cold. His ground has not been prepared yet.");
    }

    private boolean refuseOnCooldown(Player player) {
        long left = cooldown.remainingMillis(player.getUniqueId());
        if (left <= 0) {
            return false;
        }
        player.sendMessage(Component.text("The Tyrant still smolders, resting. Try again in about "
                + (left / 60_000L + 1) + " minute(s).", NamedTextColor.GRAY));
        return true;
    }
}
