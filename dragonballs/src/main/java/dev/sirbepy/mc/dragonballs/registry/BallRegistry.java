package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.bukkit.entity.Entity;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The persistent source of truth for all 7 balls (saved to {@code balls.yml}), plus the small item
 * helpers phase 2 needs. Every ball has exactly one state at a time (invariant enforced by the
 * mark* methods always clearing the other states' fields); never block the main thread here, this
 * class performs no I/O except its own atomic save through {@link BallRegistryStore}.
 */
public final class BallRegistry {

    private final TreeMap<Integer, Ball> balls = new TreeMap<>();
    private final BallRegistryStore store;
    private final BallItems items;
    private final BallSet set;

    private String lastSummonWorld;
    private double lastSummonX;
    private double lastSummonY;
    private double lastSummonZ;
    private StoneMode stoneUntilMode;
    private long stoneUntilValue;
    private long wishCount;

    private BallScatterer scatterer;
    private PlacedBallLook look = PlacedBallLook.NONE;
    // Stars still PLACED on paper but already flying off after a wish, until their new spot lands;
    // never shown again at the old spot (memory only: a restart mid-flight just shows the old spot).
    private final Set<Integer> hidden = new HashSet<>();

    public BallRegistry(File file, Logger logger, BallItems items, BallSet set) {
        this.store = new BallRegistryStore(file, logger);
        this.items = items;
        this.set = set;
        for (int star = 1; star <= 7; star++) {
            balls.put(star, new Ball(star));
        }
        load();
    }

    /** Which of the 3 sets this registry tracks; every item/block it creates and recognizes carries
     * this set's PDC marker. */
    public BallSet set() {
        return set;
    }

    /** Set once at startup; late-bound so {@code ScatterService} can depend on this registry too. */
    public void setScatterer(BallScatterer scatterer) {
        this.scatterer = scatterer;
    }

    /** Set once at startup, before {@link #showPlaced}. */
    public void setLook(PlacedBallLook look) {
        this.look = look;
    }

    /** Shows every PLACED ball's look; called once at startup, after {@link #setLook}. */
    public void showPlaced() {
        for (Ball ball : balls.values()) {
            if (ball.state == BallState.PLACED && !hidden.contains(ball.star)) {
                Location block = blockLocation(ball);
                if (block != null) {
                    look.show(set, ball.star, block, ball.stone);
                }
            }
        }
    }

    /** The ball leaves its spot after a wish: its look goes now, its registry entry when it lands. */
    public void hidePlaced(int star) {
        Ball ball = balls.get(star);
        if (ball != null && ball.state == BallState.PLACED) {
            hidden.add(star);
            look.hide(set, star);
        }
    }

    /** Whether {@code star} is still PLACED on paper but already flying off after a wish. */
    public boolean isFlying(int star) {
        return hidden.contains(star);
    }

    public void glowPlaced(boolean on) {
        look.glow(set, on);
    }

    public boolean hasSavedState() {
        return store.exists();
    }

    // ---- phase 2 public API ----

    public List<BallView> getBalls() {
        List<BallView> views = new ArrayList<>(7);
        for (Ball ball : balls.values()) {
            views.add(ball.snapshot());
        }
        return views;
    }

    public Optional<BallView> get(int star) {
        Ball ball = balls.get(star);
        return ball == null ? Optional.empty() : Optional.of(ball.snapshot());
    }

    /**
     * Locations worth searching: every non-stone ball's block/item-entity location, plus online
     * holders' LIVE location (never their stale stored one). An offline holder contributes nothing,
     * same as a radar would find nothing for a ball tucked away in an unreachable inventory.
     */
    public List<Location> activeTargets() {
        return new ArrayList<>(activeTargetsByStar().values());
    }

    /** {@link #activeTargets()} keyed by star, for the radar's pick-a-ball tracking. */
    public Map<Integer, Location> activeTargetsByStar() {
        Map<Integer, Location> targets = new TreeMap<>();
        for (Ball ball : balls.values()) {
            if (ball.stone) {
                continue;
            }
            switch (ball.state) {
                case PLACED, DROPPED -> {
                    Location loc = ball.snapshot().lastKnownLocation();
                    if (loc != null) {
                        targets.put(ball.star, loc);
                    }
                }
                case HELD -> {
                    Player online = ball.holderId == null ? null : Bukkit.getPlayer(ball.holderId);
                    if (online != null) {
                        targets.put(ball.star, online.getLocation());
                    }
                }
            }
        }
        return targets;
    }

    public boolean isBall(ItemStack item) {
        return items.isBall(item);
    }

    public OptionalInt starOf(ItemStack item) {
        return items.starOf(item);
    }

    public ItemStack createItem(int star) {
        return items.createItem(set, star);
    }

    public BallItems items() {
        return items;
    }

    public void markPlaced(int star, Location location, boolean stone) {
        Ball ball = require(star);
        ball.state = BallState.PLACED;
        ball.stone = stone;
        setLocation(ball, location);
        ball.holderId = null;
        ball.holderName = null;
        ball.entityId = null;
        save();
        hidden.remove(star);
        look.show(set, star, location, stone);
    }

    /** {@code stone} carries over the item's own look: a stone ball stays stone while held, same as
     * placed/dropped, until the global stone-ends sweep (or its own lazy catch-up) swaps it back. */
    public void markHeld(int star, Player holder, boolean stone) {
        Ball ball = require(star);
        leavePlaced(ball);
        long now = System.currentTimeMillis();
        ball.state = BallState.HELD;
        ball.stone = stone;
        ball.holderId = holder.getUniqueId();
        ball.holderName = holder.getName();
        ball.heldSinceMillis = now;
        setLocation(ball, holder.getLocation());
        ball.entityId = null;
        save();
    }

    public void markDropped(int star, Item entity, boolean stone) {
        Ball ball = require(star);
        leavePlaced(ball);
        ball.state = BallState.DROPPED;
        ball.stone = stone;
        ball.entityId = entity.getUniqueId();
        setLocation(ball, entity.getLocation());
        ball.holderId = null;
        ball.holderName = null;
        save();
    }

    /**
     * Scatters all 7 balls around {@code center} (new world-scatter, or an admin re-roll). Returns
     * the chosen destination per star immediately (synchronous: the random target point, before
     * terrain is resolved), so phase 2 can animate a ball flying toward it; the actual block appears
     * once the async surface search/placement for that star finishes.
     */
    public Map<Integer, Location> scatterAll(Location center, boolean stone) {
        return requireScatterer().scatterAll(center, stone, this);
    }

    /** How many wishes have been granted; the hunt gets harder (see {@code config.yml}'s difficulty
     * list) as this climbs. Phase 2 bumps it once per granted wish, before its own {@code scatterAll}. */
    public long wishCount() {
        return wishCount;
    }

    public void incrementWishCount() {
        wishCount++;
        save();
    }

    // ---- scatter/task/listener support (not part of the phase 2 contract, but public for them) ----

    public void scatterOne(int star, Location center, boolean stone) {
        requireScatterer().scatterOne(star, center, stone, this);
    }

    public boolean isHeldBy(int star, UUID playerId) {
        Ball ball = balls.get(star);
        return ball != null && ball.state == BallState.HELD && playerId.equals(ball.holderId);
    }

    public boolean isPlacedAt(int star, Location location) {
        Ball ball = balls.get(star);
        return ball != null && ball.state == BallState.PLACED && sameBlock(ball, location);
    }

    public boolean isDroppedAs(int star, UUID entityId) {
        Ball ball = balls.get(star);
        return ball != null && ball.state == BallState.DROPPED && entityId.equals(ball.entityId);
    }

    /** PLACED balls whose stored block falls inside the given chunk, for the self-heal scan. */
    public List<BallView> placedBallsInChunk(String worldName, int chunkX, int chunkZ) {
        List<BallView> views = new ArrayList<>();
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        for (Ball ball : balls.values()) {
            if (ball.state != BallState.PLACED || hidden.contains(ball.star) || !worldName.equals(ball.worldName)) {
                continue;
            }
            int bx = (int) Math.floor(ball.x);
            int bz = (int) Math.floor(ball.z);
            if (bx >= minX && bx < minX + 16 && bz >= minZ && bz < minZ + 16) {
                views.add(ball.snapshot());
            }
        }
        return views;
    }

    public List<BallView> held() {
        List<BallView> views = new ArrayList<>();
        for (Ball ball : balls.values()) {
            if (ball.state == BallState.HELD) {
                views.add(ball.snapshot());
            }
        }
        return views;
    }

    public Location lastSummon() {
        var world = lastSummonWorld == null ? null : Bukkit.getWorld(lastSummonWorld);
        return world == null ? null : new Location(world, lastSummonX, lastSummonY, lastSummonZ);
    }

    public void setLastSummon(Location location) {
        lastSummonWorld = location.getWorld().getName();
        lastSummonX = location.getX();
        lastSummonY = location.getY();
        lastSummonZ = location.getZ();
        save();
    }

    public void setStoneUntil(StoneMode mode, long value) {
        stoneUntilMode = mode;
        stoneUntilValue = value;
        for (Ball ball : balls.values()) {
            ball.stone = true;
        }
        save();
    }

    /** True once the stone cooldown has elapsed (or none is pending). */
    public boolean isStoneExpired(long currentMinecraftDay, long nowMillis) {
        if (stoneUntilMode == null) {
            return true;
        }
        return switch (stoneUntilMode) {
            case MINECRAFT_DAYS -> currentMinecraftDay >= stoneUntilValue;
            case REAL_HOURS -> nowMillis >= stoneUntilValue;
        };
    }

    /** This particular ball's own instance (block/item) is still textured/flagged stone. May lag a
     * moment behind {@link #isDormant()} going false, until the stone-ends sweep/lazy catch-up runs. */
    public boolean isStone(int star) {
        Ball ball = balls.get(star);
        return ball != null && ball.stone;
    }

    /** The cooldown window itself (set together for all 7 by {@link #setStoneUntil}, cleared
     * together by {@link #clearStone}); phase 2 checks this before allowing a summon attempt. */
    public boolean isDormant() {
        return stoneUntilMode != null;
    }

    /**
     * Ends the cooldown and sweeps every already-reachable stone ball back to active: online
     * players' inventories, placed balls (their look is re-shown active), and dropped item entities
     * in loaded chunks. A ball that's offline-held or dropped in an unloaded chunk is swept lazily
     * instead, on join or chunk load, by the listeners that already run then. One sweep at the moment stone ends is
     * enough; nothing here runs on a tick loop.
     */
    public void clearStone() {
        stoneUntilMode = null;
        for (Ball ball : balls.values()) {
            boolean wasStone = ball.stone;
            ball.stone = false;
            if (!wasStone) {
                continue;
            }
            switch (ball.state) {
                case HELD -> {
                    // Live inventory swap happens below, once per online player (cheaper than per ball).
                }
                case PLACED -> {
                    Location block = blockLocation(ball);
                    if (block != null && !hidden.contains(ball.star)) {
                        look.show(set, ball.star, block, false);
                    }
                }
                case DROPPED -> swapDroppedEntityIfLoaded(ball);
            }
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            sweepInventory(player.getInventory());
        }
        save();
    }

    private void swapDroppedEntityIfLoaded(Ball ball) {
        if (ball.entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(ball.entityId);
        if (entity instanceof Item item && item.isValid()) {
            item.setItemStack(items.createItem(set, ball.star));
        }
    }

    // Armor slots are skipped: a ball can never be equipped as a helmet (blocked elsewhere), so they
    // can never hold one. The cursor (mid-click) is left to the container guard's own checks. Scoped
    // to this registry's own set: a player can carry a stone ball of another set whose own cooldown
    // hasn't ended yet, and that one must be left alone.
    private void sweepInventory(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (items.isStoneItem(current) && items.setOf(current) == set && items.starOf(current).isPresent()) {
                inventory.setItem(slot, items.createItem(set, items.starOf(current).getAsInt()));
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (items.isStoneItem(offHand) && items.setOf(offHand) == set) {
            items.starOf(offHand).ifPresent(star -> inventory.setItemInOffHand(items.createItem(set, star)));
        }
    }

    // ---- internals ----

    private Ball require(int star) {
        Ball ball = balls.get(star);
        if (ball == null) {
            throw new IllegalArgumentException("No such star: " + star);
        }
        return ball;
    }

    private BallScatterer requireScatterer() {
        if (scatterer == null) {
            throw new IllegalStateException("BallRegistry.setScatterer was never called");
        }
        return scatterer;
    }

    private void leavePlaced(Ball ball) {
        if (ball.state == BallState.PLACED) {
            hidden.remove(ball.star);
            look.hide(set, ball.star);
        }
    }

    private static Location blockLocation(Ball ball) {
        var world = ball.worldName == null ? null : Bukkit.getWorld(ball.worldName);
        return world == null ? null
                : new Location(world, Math.floor(ball.x), Math.floor(ball.y), Math.floor(ball.z));
    }

    private static void setLocation(Ball ball, Location location) {
        ball.worldName = location.getWorld().getName();
        ball.x = location.getX();
        ball.y = location.getY();
        ball.z = location.getZ();
    }

    private static boolean sameBlock(Ball ball, Location location) {
        return location.getWorld() != null && location.getWorld().getName().equals(ball.worldName)
                && location.getBlockX() == (int) Math.floor(ball.x)
                && location.getBlockY() == (int) Math.floor(ball.y)
                && location.getBlockZ() == (int) Math.floor(ball.z);
    }

    private void load() {
        BallRegistryStore.GlobalState global = store.load(balls);
        lastSummonWorld = global.lastSummonWorld();
        lastSummonX = global.lastSummonX();
        lastSummonY = global.lastSummonY();
        lastSummonZ = global.lastSummonZ();
        stoneUntilMode = global.stoneUntilMode();
        stoneUntilValue = global.stoneUntilValue();
        wishCount = global.wishCount();
    }

    private void save() {
        store.save(balls.values(), new BallRegistryStore.GlobalState(lastSummonWorld, lastSummonX, lastSummonY,
                lastSummonZ, stoneUntilMode, stoneUntilValue, wishCount));
    }
}
