package dev.sirbepy.mc.minibosses.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Locks a Guardian fight inside its arena (Joe, 2026-10-21). Everyone in the box when the Guardian
 * rises, and anyone who walks or teleports in while it lives, becomes a fighter: switched to
 * Adventure so the arena can't be broken, and unable to leave it on foot, by flight, elytra, pearl,
 * chorus fruit or any command teleport. The Guardian is pulled back to the center whenever it gets
 * out. A fighter is released, with their old game mode back, when the Guardian dies, when they die
 * (on respawn), when they log off, or when the fight hits {@link #LIMIT_MILLIS} and the Guardian
 * sinks away. The old mode is kept on the player itself, so a crash mid-fight still restores it on
 * their next join. Creative and Spectator players are never pulled in, so ops can watch.
 */
public final class ArenaFights implements Listener {

    /** A fight nobody can finish (boss stuck in a wall, everyone AFK) must still let them out. */
    public static final long LIMIT_MILLIS = 60 * 60_000L;

    private record Fight(Arena arena, UUID boss, long startedAt, Set<UUID> fighters) {
    }

    private final ArenaRegistry arenas;
    private final LongSupplier now;
    private final NamespacedKey previousMode;
    private final NamespacedKey guardianKey;
    private final Map<String, Fight> active = new HashMap<>();

    public ArenaFights(Plugin plugin, ArenaRegistry arenas, LongSupplier now) {
        this.arenas = arenas;
        this.now = now;
        this.previousMode = new NamespacedKey(plugin, "arena_previous_mode");
        // The same key every Guardian fight tags its boss with.
        this.guardianKey = new NamespacedKey(plugin, "guardian");
    }

    public boolean busy(String guardianId) {
        return active.containsKey(guardianId);
    }

    public boolean isFighter(Player player) {
        return fightOf(player).isPresent();
    }

    /** Seals {@code arena} around {@code boss} and everyone already standing inside it. */
    public void start(Arena arena, LivingEntity boss) {
        Fight fight = new Fight(arena, boss.getUniqueId(), now.getAsLong(), new HashSet<>());
        active.put(arena.guardianId(), fight);
        for (Player player : arena.world().getPlayers()) {
            if (arena.contains(player.getLocation())) {
                join(fight, player);
            }
        }
    }

    private void join(Fight fight, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || !fight.fighters().add(player.getUniqueId())) {
            return;
        }
        var pdc = player.getPersistentDataContainer();
        if (!pdc.has(previousMode, PersistentDataType.STRING)) {
            pdc.set(previousMode, PersistentDataType.STRING, player.getGameMode().name());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.sendMessage(Component.text("The arena seals around you. There is no leaving until it falls.",
                NamedTextColor.DARK_RED));
    }

    /** Gives back the game mode the player had before their first fight, if one is on record. */
    private void restore(Player player) {
        var pdc = player.getPersistentDataContainer();
        String mode = pdc.get(previousMode, PersistentDataType.STRING);
        if (mode == null) {
            return;
        }
        pdc.remove(previousMode);
        try {
            player.setGameMode(GameMode.valueOf(mode));
        } catch (IllegalArgumentException e) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void end(Fight fight, String message) {
        active.remove(fight.arena().guardianId());
        for (UUID id : fight.fighters()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restore(player);
                if (message != null) {
                    player.sendMessage(Component.text(message, NamedTextColor.GRAY));
                }
            }
        }
    }

    private Optional<Fight> fightOf(Player player) {
        return active.values().stream().filter(f -> f.fighters().contains(player.getUniqueId())).findFirst();
    }

    private Optional<Fight> fightAt(Location at) {
        return active.values().stream().filter(f -> f.arena().contains(at)).findFirst();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getBlockX() == event.getFrom().getBlockX() && to.getBlockY() == event.getFrom().getBlockY()
                && to.getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }
        Optional<Fight> mine = fightOf(event.getPlayer());
        if (mine.isPresent()) {
            if (!mine.get().arena().contains(to)) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("The arena holds you in.", NamedTextColor.DARK_RED));
            }
            return;
        }
        fightAt(to).ifPresent(fight -> join(fight, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Optional<Fight> mine = fightOf(event.getPlayer());
        if (mine.isPresent()) {
            if (!mine.get().arena().contains(event.getTo())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("The arena holds you in. Finish the fight first.",
                        NamedTextColor.DARK_RED));
            }
            return;
        }
        fightAt(event.getTo()).ifPresent(fight -> join(fight, event.getPlayer()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        fightOf(event.getPlayer()).ifPresent(fight -> fight.fighters().remove(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isFighter(event.getPlayer())) {
            restore(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fightOf(event.getPlayer()).ifPresent(fight -> fight.fighters().remove(event.getPlayer().getUniqueId()));
        restore(event.getPlayer());
    }

    /** Covers a crash or restart mid-fight, which never fires a quit. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isFighter(event.getPlayer())) {
            restore(event.getPlayer());
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        active.values().stream().filter(f -> f.boss().equals(event.getEntity().getUniqueId())).findFirst()
                .ifPresent(fight -> end(fight, "The Guardian falls, and the arena lets you go."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossTeleport(EntityTeleportEvent event) {
        active.values().stream().filter(f -> f.boss().equals(event.getEntity().getUniqueId())).findFirst()
                .ifPresent(fight -> {
                    if (event.getTo() == null || !fight.arena().contains(event.getTo())) {
                        event.setCancelled(true);
                    }
                });
    }

    /** A Guardian still alive in its arena after a restart picks its fight back up. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            String id = entity.getPersistentDataContainer().get(guardianKey, PersistentDataType.STRING);
            if (id == null || busy(id) || !(entity instanceof LivingEntity boss) || boss.isDead()) {
                continue;
            }
            arenas.get(id).filter(arena -> arena.contains(boss.getLocation())).ifPresent(arena -> start(arena, boss));
        }
    }

    /** Run every second: leash each Guardian to its arena, end fights whose Guardian is gone or out of time. */
    public void tick() {
        for (Fight fight : new ArrayList<>(active.values())) {
            Entity boss = Bukkit.getEntity(fight.boss());
            if (!(boss instanceof LivingEntity living) || living.isDead()) {
                end(fight, null);
                continue;
            }
            if (now.getAsLong() - fight.startedAt() > LIMIT_MILLIS) {
                boss.remove();
                end(fight, "An hour has passed. The Guardian sinks back into its sleep, and the arena lets you go.");
                continue;
            }
            if (!fight.arena().contains(boss.getLocation())) {
                boss.teleport(fight.arena().center());
            }
        }
    }

    /** On shutdown every fighter gets their mode back now; Paper disconnects players after plugins stop. */
    public void releaseAll() {
        for (Fight fight : List.copyOf(active.values())) {
            end(fight, null);
        }
    }
}
