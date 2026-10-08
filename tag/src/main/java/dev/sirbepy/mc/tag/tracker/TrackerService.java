package dev.sirbepy.mc.tag.tracker;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.config.TrackerConfig;
import dev.sirbepy.mc.tag.logic.Playability;
import dev.sirbepy.mc.tag.logic.TrackerTargets;
import dev.sirbepy.mc.tag.logic.TrackerTargets.Candidate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * IT's tracker compass: a PDC-marked compass {@link TrackerListener} keeps inside IT's own
 * inventory, plus the per-IT choice of who it points at.
 *
 * <p>Pointing uses {@link Player#setCompassTarget} (the client-side spawn a plain compass points
 * to) rather than a lodestone position on the item: rewriting an item component every update
 * makes the held compass dip and re-equip on the client each time, and a moving target would
 * change it constantly. The item itself only changes when it has to spin (target in another
 * realm, AFK, or nobody), via a lodestone tracker with no target.
 */
public final class TrackerService {

    // Slots 0-8 are the hotbar, 9-35 main storage: a full inventory gives up its last storage slot.
    private static final int BUMP_SLOT = 35;
    // The client sends a use-on-block and then a use-in-air for one right-click on a block, which
    // would otherwise cycle twice.
    private static final long CYCLE_DEBOUNCE_MILLIS = 250;

    private final NamespacedKey marker;
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, Long> lastCycle = new HashMap<>();

    public TrackerService(JavaPlugin plugin) {
        this.marker = new NamespacedKey(plugin, "tag-tracker");
    }

    public boolean isTracker(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BOOLEAN);
    }

    public ItemStack build(TrackerConfig config) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Messages.mini().deserialize(config.itemName));
        if (!config.itemLore.isBlank()) {
            meta.lore(List.of(Messages.mini().deserialize(config.itemLore).decoration(TextDecoration.ITALIC, false)));
        }
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Gives {@code player} a tracker unless they already carry one (cursor included). */
    public void give(Player player, TrackerConfig config) {
        if (has(player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (inventory.addItem(build(config)).isEmpty()) {
            return;
        }
        bump(player, inventory.getItem(BUMP_SLOT));
        inventory.setItem(BUMP_SLOT, build(config));
    }

    private boolean has(Player player) {
        if (isTracker(player.getItemOnCursor())) {
            return true;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTracker(item)) {
                return true;
            }
        }
        return false;
    }

    private void bump(Player player, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        player.getEnderChest().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
    }

    /** Takes every tracker off {@code player} (inventory, cursor, ender chest) and resets their compass. */
    public void revoke(Player player) {
        purge(player);
        if (isTracker(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        purge(player.getEnderChest());
        forget(player.getUniqueId());
        player.setCompassTarget(Bukkit.getWorlds().getFirst().getSpawnLocation());
    }

    /** Deletes trackers from {@code player}'s inventory: a non-IT player must never hold one. */
    public void purge(Player player) {
        purge(player.getInventory());
    }

    private void purge(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTracker(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }

    public void forget(UUID it) {
        targets.remove(it);
        lastCycle.remove(it);
    }

    /**
     * Every trackable candidate for IT's compass: Bukkit-side wiring only (who's online, nearby,
     * vanished, AFK, opted-out). The ordering/eligibility rules stay in {@link TrackerTargets},
     * same split as {@code AfkService}'s own candidate gathering for the idle-pass check.
     */
    public List<Candidate> candidatesFor(Player it, Predicate<UUID> isAfk, Predicate<UUID> isOptedOut) {
        Location here = it.getLocation();
        return TrackerTargets.trackable(Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(it.getUniqueId()))
                // Vanished players (Essentials /vanish hides them from IT) must not leak through the tracker.
                .filter(it::canSee)
                .map(p -> {
                    boolean sameWorld = p.getWorld().equals(it.getWorld());
                    return new Candidate(p.getUniqueId(), p.getName(), sameWorld,
                            sameWorld ? p.getLocation().distance(here) : 0.0, isAfk.test(p.getUniqueId()),
                            isOptedOut.test(p.getUniqueId()), Playability.playable(p));
                })
                .toList());
    }

    /** Gives IT the tracker, or takes it away when {@code config.enabled} is off (e.g. after a reload). */
    public void sync(Player it, TrackerConfig config) {
        if (config.enabled) {
            give(it, config);
        } else {
            revoke(it);
        }
    }

    /** Re-gives a tracker lost to death or {@code /clear}, re-points it and refreshes the action bar. */
    public void tick(Player it, TrackerConfig config, Predicate<UUID> isAfk, Predicate<UUID> isOptedOut) {
        give(it, config);
        update(it, candidatesFor(it, isAfk, isOptedOut), config);
    }

    /** Right-click handling: moves to the next candidate and refreshes the action bar when it moved. */
    public void cycleAndUpdate(Player it, TrackerConfig config, Predicate<UUID> isAfk, Predicate<UUID> isOptedOut) {
        List<Candidate> trackable = candidatesFor(it, isAfk, isOptedOut);
        if (cycle(it, trackable, System.currentTimeMillis())) {
            update(it, trackable, config);
        }
    }

    /** Right-click: moves to the next trackable player. Returns false when debounced. */
    public boolean cycle(Player it, List<Candidate> trackable, long now) {
        Long last = lastCycle.get(it.getUniqueId());
        if (last != null && now - last < CYCLE_DEBOUNCE_MILLIS) {
            return false;
        }
        lastCycle.put(it.getUniqueId(), now);
        UUID current = TrackerTargets.resolve(trackable, targets.get(it.getUniqueId()));
        UUID next = TrackerTargets.next(trackable, current);
        if (next == null) {
            targets.remove(it.getUniqueId());
        } else {
            targets.put(it.getUniqueId(), next);
        }
        return true;
    }

    /** Points the compass and, while it's in either hand, shows who it points at on the action bar. */
    public void update(Player it, List<Candidate> trackable, TrackerConfig config) {
        UUID targetId = TrackerTargets.resolve(trackable, targets.get(it.getUniqueId()));
        if (targetId == null) {
            targets.remove(it.getUniqueId());
        } else {
            targets.put(it.getUniqueId(), targetId);
        }
        Candidate target = trackable.stream().filter(c -> c.uuid().equals(targetId)).findFirst().orElse(null);
        Player targetPlayer = target == null ? null : Bukkit.getPlayer(target.uuid());
        boolean points = target != null && targetPlayer != null && target.sameWorld() && !target.afk();

        if (points) {
            it.setCompassTarget(targetPlayer.getLocation());
        }
        setSpinning(it.getInventory(), !points);

        if (holding(it)) {
            it.sendActionBar(readout(target, config));
        }
    }

    private Component readout(Candidate target, TrackerConfig config) {
        if (target == null) {
            return Messages.mini().deserialize(config.readoutNobody);
        }
        String template = target.afk() ? config.readoutAfk
                : !target.sameWorld() ? config.readoutOtherRealm
                : config.readoutDistance;
        return Messages.mini().deserialize(template,
                Placeholder.unparsed("player", target.name()),
                Placeholder.unparsed("distance", String.valueOf(Math.round(target.distance()))));
    }

    private boolean holding(Player player) {
        return isTracker(player.getInventory().getItemInMainHand()) || isTracker(player.getInventory().getItemInOffHand());
    }

    /**
     * Only touches an item whose mode actually differs, so a steady state never rewrites the
     * stack (see the class note on re-equip dips).
     */
    private void setSpinning(PlayerInventory inventory, boolean spinning) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isTracker(item) || item.hasData(DataComponentTypes.LODESTONE_TRACKER) == spinning) {
                continue;
            }
            if (spinning) {
                // A lodestone tracker with no target is vanilla's "lodestone destroyed" compass: it spins.
                item.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker((Location) null, false));
            } else {
                item.unsetData(DataComponentTypes.LODESTONE_TRACKER);
            }
            inventory.setItem(i, item);
        }
    }
}
