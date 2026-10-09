package dev.sirbepy.mc.merfolk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The shell ritual: only the wisher (config {@code owner}) can call a grown villager standing in
 * water to the sea by right-clicking it with a nautilus shell. The shell is spent and the villager
 * is a merfolk for good. Not inside someone else's town.
 */
public final class Conversion implements Listener {

    private final Supplier<UUID> owner;
    private final TownCheck towns;
    /** Starts looking after a fresh merfolk (body, trades). */
    private final Consumer<Villager> adopt;

    public Conversion(Supplier<UUID> owner, TownCheck towns, Consumer<Villager> adopt) {
        this.owner = owner;
        this.towns = towns;
        this.adopt = adopt;
    }

    // Before MerfolkJobs' MONITOR handler and before the trade screen opens.
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager) || Merfolk.is(villager)) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held == null || held.getType() != Material.NAUTILUS_SHELL) return;
        if (!player.getUniqueId().equals(owner.get())) {
            player.sendActionBar(Component.text("Only the one who wished for the Merfolk can call villagers to the sea.",
                    NamedTextColor.GRAY));
            return;
        }
        event.setCancelled(true);
        String problem = problem(player, villager);
        if (problem != null) {
            player.sendActionBar(Component.text(problem, NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) held.subtract();
        Merfolk.make(villager);
        adopt.accept(villager);
        villager.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, villager.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
        villager.getWorld().playSound(villager.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1f, 1.2f);
        player.sendActionBar(Component.text("The sea answers: a new merfolk.", NamedTextColor.AQUA));
    }

    private String problem(Player player, Villager villager) {
        if (!villager.isAdult()) return "Only a grown villager can answer the sea.";
        if (!villager.isInWater()) return "The villager must be standing in water.";
        if (towns.inOtherTown(player, villager.getLocation())) return "Not inside another player's town.";
        return null;
    }
}
