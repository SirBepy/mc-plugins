package dev.sirbepy.mc.classes.talent;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Temporary helpers from abilities (wolves, the golem, the polar bear, sentries, decoys): never
 * saved to the world, no loot or XP when they die, never target a player, and gone when their time
 * is up or their owner logs out, dies, changes world or changes class, and on plugin disable.
 */
public final class Summons implements Listener {

    private final Plugin plugin;
    private final NamespacedKey ownerKey;
    private final Map<UUID, List<Entity>> byOwner = new HashMap<>();

    public Summons(Plugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "summon_owner");
    }

    /**
     * Spawns {@code type} at {@code at} for {@code owner}, lets {@code setup} dress it before it
     * enters the world, and removes it after {@code ticks}.
     */
    public <T extends Entity> T spawn(Player owner, Location at, Class<T> type, long ticks, Consumer<T> setup) {
        T entity = at.getWorld().spawn(at, type, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            setup.accept(e);
        });
        byOwner.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(entity);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> dismiss(entity), ticks);
        return entity;
    }

    /** The summon's owner, or null if {@code e} isn't a summon. */
    public UUID owner(Entity e) {
        String raw = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return raw == null ? null : UUID.fromString(raw);
    }

    public boolean isSummon(Entity e) {
        return owner(e) != null;
    }

    /** {@code owner}'s live summons. */
    public List<Entity> of(UUID owner) {
        List<Entity> list = byOwner.get(owner);
        if (list == null) return List.of();
        list.removeIf(e -> !e.isValid());
        return List.copyOf(list);
    }

    public void dismiss(Entity e) {
        if (e.isValid()) e.remove();
        UUID owner = owner(e);
        if (owner != null && byOwner.containsKey(owner)) byOwner.get(owner).remove(e);
    }

    public void dismissAll(UUID owner) {
        List<Entity> list = byOwner.remove(owner);
        if (list != null) list.forEach(e -> {
            if (e.isValid()) e.remove();
        });
    }

    public void dismissEveryone() {
        new ArrayList<>(byOwner.keySet()).forEach(this::dismissAll);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player && isSummon(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!isSummon(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dismissAll(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onOwnerDeath(PlayerDeathEvent event) {
        dismissAll(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        dismissAll(event.getPlayer().getUniqueId());
    }
}
