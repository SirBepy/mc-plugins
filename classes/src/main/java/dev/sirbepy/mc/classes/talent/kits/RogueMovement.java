package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.KitContext;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Double Jump and Wall Climb (docs/classes-revamp.md, Rogue Shadow branch): both lean on an engine
 * trick (an airborne jump raises {@link PlayerToggleFlightEvent}, a wall-sneak raises {@link
 * PlayerToggleSneakEvent}) instead of a custom input packet. {@code allowFlight} is only ever
 * granted to a survival/adventure player with Double Jump ranked, and {@link #sweepFlight} undoes
 * it within 5 s of a reason (left the class, logged out) that has no event of its own to catch.
 */
final class RogueMovement implements Listener {

    private static final double WALL_CLIMB_BLOCKS = 6;

    private final KitContext ctx;
    private final RogueState s;
    /**
     * Marks a player whose allowFlight we turned on. It is saved with the player, so a crash that
     * skips both the quit handler and plugin disable still gets cleaned up on their next join,
     * without touching flight that Essentials /fly or creative granted.
     */
    private final NamespacedKey grantedKey;

    RogueMovement(KitContext ctx, RogueState state) {
        this.ctx = ctx;
        this.s = state;
        this.grantedKey = new NamespacedKey(ctx.plugin(), "rogue_double_jump_flight");
    }

    private void grant(Player player) {
        player.setAllowFlight(true);
        player.getPersistentDataContainer().set(grantedKey, PersistentDataType.BOOLEAN, true);
        s.flightControlled.add(player.getUniqueId());
    }

    private void release(Player player) {
        s.flightControlled.remove(player.getUniqueId());
        if (player.getPersistentDataContainer().has(grantedKey)) {
            player.getPersistentDataContainer().remove(grantedKey);
            if (survivalOrAdventure(player)) player.setAllowFlight(false);
        }
    }

    /** At plugin disable: hands back every allowFlight this kit granted. */
    void releaseAll() {
        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) release(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        release(event.getPlayer());
    }

    /** The periodic safety net; started once at enable. */
    void start() {
        ctx.repeat(this::sweepFlight, 100, 100);
    }

    // --- Double Jump ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player) || !event.isFlying()) return;
        int rank = ctx.rank(player, "double_jump");
        if (rank == 0) return;
        event.setCancelled(true);
        if (!survivalOrAdventure(player) || player.isOnGround()) return;

        UUID id = player.getUniqueId();
        long now = ctx.now();
        Long readyAt = s.doubleJumpReadyAt.get(id);
        if (readyAt != null && now < readyAt) return;

        Vector dir = player.getLocation().getDirection().setY(0);
        dir = dir.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : dir.normalize();
        player.setVelocity(dir.multiply(0.6).setY(0.7));
        s.doubleJumpReadyAt.put(id, now + (rank == 1 ? 8_000 : 4_000));
        release(player);
        ctx.fx().burst(player, Particle.CLOUD, 10, 0.3);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.5f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean eligible = ctx.active(player) && ctx.rank(player, "double_jump") > 0 && survivalOrAdventure(player);
        if (!eligible) {
            if (s.flightControlled.contains(id)) release(player);
            return;
        }
        if (!player.isOnGround()) return;
        long now = ctx.now();
        Long readyAt = s.doubleJumpReadyAt.get(id);
        if ((readyAt == null || now >= readyAt) && !player.getAllowFlight()) grant(player);
    }

    /** Clears any {@code allowFlight} we granted to a player who no longer qualifies for it. */
    void sweepFlight() {
        for (UUID id : new ArrayList<>(s.flightControlled)) {
            Player player = ctx.plugin().getServer().getPlayer(id);
            if (player == null) {
                s.flightControlled.remove(id);
                continue;
            }
            boolean eligible = ctx.active(player) && ctx.rank(player, "double_jump") > 0 && survivalOrAdventure(player);
            if (!eligible) release(player);
        }
    }

    private static boolean survivalOrAdventure(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    // --- Wall Climb -----------------------------------------------------------------------------------

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!event.isSneaking()) {
            stopClimb(id);
            return;
        }
        if (!ctx.active(player) || ctx.rank(player, "wall_climb") == 0) return;
        startClimb(player);
    }

    private void startClimb(Player player) {
        UUID id = player.getUniqueId();
        stopClimb(id);
        s.wallClimbStartY.put(id, player.getLocation().getY());
        BukkitTask task = ctx.repeat(() -> climbTick(player), 0, 2);
        s.wallClimbTask.put(id, task);
    }

    private void climbTick(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || !player.isSneaking() || !ctx.active(player) || ctx.rank(player, "wall_climb") == 0) {
            stopClimb(id);
            return;
        }
        if (!againstWall(player)) {
            s.wallClimbStartY.put(id, player.getLocation().getY());
            return;
        }
        double startY = s.wallClimbStartY.getOrDefault(id, player.getLocation().getY());
        if (player.getLocation().getY() - startY >= WALL_CLIMB_BLOCKS) return;
        player.setVelocity(player.getVelocity().setY(0.3));
    }

    private void stopClimb(UUID id) {
        BukkitTask task = s.wallClimbTask.remove(id);
        if (task != null) task.cancel();
        s.wallClimbStartY.remove(id);
    }

    // LivingEntity#getFacing() isn't implemented in MockBukkit, so the wall check uses the look
    // direction's dominant horizontal axis directly instead of snapping it to a BlockFace first.
    private static boolean againstWall(Player player) {
        Vector dir = player.getLocation().getDirection();
        double dx = Math.abs(dir.getX()) >= Math.abs(dir.getZ()) ? Math.signum(dir.getX()) : 0;
        double dz = Math.abs(dir.getZ()) > Math.abs(dir.getX()) ? Math.signum(dir.getZ()) : 0;
        boolean feet = player.getLocation().clone().add(dx, 0, dz).getBlock().getType().isSolid();
        boolean eyes = player.getEyeLocation().clone().add(dx, 0, dz).getBlock().getType().isSolid();
        return feet || eyes;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopClimb(id);
        release(event.getPlayer());
    }
}
