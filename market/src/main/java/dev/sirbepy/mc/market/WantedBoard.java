package dev.sirbepy.mc.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.sirbepy.mc.core.item.KillStamp;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;

/**
 * Player of the Day: each market period the board at {@code wanted-board} picks a target
 * ({@code wanted.online-chance}, 80%, an online player, otherwise anyone who has ever joined), then
 * pays whoever brings in that player's head first (Joe, 2026-10-09). "Brings in" means ANY
 * genuine head of that player, a fresh PvP drop, an old one kept from before, or a cartel trophy:
 * the player-head-drops datapack's {@code fill_player_head} loot function and the cartel's
 * {@code Trophies.trophy()} both write the real player's UUID into the head's profile (Joe: "ppl can
 * also give heads of players they had before"), so matching on profile UUID catches every source.
 * One sale per period, server-wide; the target cannot sell their own head.
 *
 * <p>A head taken after the bounty was posted ({@link KillStamp}) pays full; one taken earlier, or
 * never stamped at all, pays only {@link MarketConfig#wantedOldHeadShare()} (Joe, 2026-10-09: a
 * fresh kill should beat a head somebody happened to be carrying already). Every period with a
 * bounty, claimed or not, is followed by one resting with none (Joe, 2026-10-10: "only a head every
 * second minecraft day, always"). A period that would post with fewer than
 * {@link MarketConfig#wantedMinOnline()} players online has no bounty (Joe, 2026-10-10: "if theres
 * only 3 ppl on the server, we dont do bounties"), and isn't a bounty period, so the next one tries again.
 *
 * <p>The bounty grows with the target's play time alive (Joe, 2026-10-09: "if you died recently,
 * the bounty is lower"): {@code base + per-hour * hours}, times a random factor rolled once at
 * posting (1 +- {@code spread}), capped at {@code cap}. Hours come from vanilla's
 * {@code time_since_death} statistic, which only counts while online and already holds everyone's
 * history. A fresh head pays the value of the life its kill ended, an old head the old-head share
 * of today's live value. A target who was online since posting and didn't die before the board
 * moves on is paid {@code survivor-share} of the value then.
 */
public final class WantedBoard implements Listener {

    @FunctionalInterface
    public interface Payout {
        boolean pay(OfflinePlayer player, double amount);
    }

    private final Plugin plugin;
    private final MarketLayout layout;
    private final Supplier<MarketConfig> config;
    private final Supplier<String> period;
    private final Random random;
    private final Supplier<Instant> clock;
    private final Supplier<List<Player>> onlinePlayers;
    private final Supplier<List<OfflinePlayer>> everJoined;
    // A player's time_since_death statistic in ticks, online or not.
    private final ToLongFunction<UUID> aliveTicks;
    private final Payout payout;
    // A successful sale's broadcast target. Not just plugin.getServer(): MockBukkit's ServerMock
    // has no Audience implementation (audiences() is unimplemented), so a test supplies its own.
    private final Audience broadcast;
    private final WantedStateStore stateStore;
    private final Logger logger;
    // The time alive a head's kill ended, stamped beside its KillStamp at death: the statistic is
    // reset right after PlayerDeathEvent, so the head is the only place it survives.
    private final NamespacedKey aliveKey;

    private WantedState state;
    // What the sign last showed, so the minute tick only redraws when the value moved.
    private BigDecimal shownValue;

    public WantedBoard(Plugin plugin, MarketLayout layout, Supplier<MarketConfig> config, Supplier<String> period,
                        Random random, Supplier<Instant> clock, Supplier<List<Player>> onlinePlayers,
                        Supplier<List<OfflinePlayer>> everJoined, ToLongFunction<UUID> aliveTicks, Payout payout,
                        Audience broadcast, File stateFile, Logger logger) {
        this.plugin = plugin;
        this.layout = layout;
        this.config = config;
        this.period = period;
        this.random = random;
        this.clock = clock;
        this.onlinePlayers = onlinePlayers;
        this.everJoined = everJoined;
        this.aliveTicks = aliveTicks;
        this.payout = payout;
        this.broadcast = broadcast;
        this.stateStore = new WantedStateStore(stateFile, logger);
        this.logger = logger;
        this.aliveKey = new NamespacedKey(plugin, "alive_ticks");
        this.state = stateStore.load();
    }

    /** Picks a new target if the period has changed since the last pick. Returns whether it did. */
    public boolean refresh() {
        if (state != null && period.get().equals(state.period())) {
            return false;
        }
        advance(false);
        return true;
    }

    /** Forces a normal pick right now, whatever the period: {@code /market rotate} (admin), which
     * always re-picks even mid-cooldown rather than waiting out the rest period. */
    public void rotate() {
        advance(true);
    }

    /**
     * Picks the next period's state. A period that had a bounty, claimed or not, rests the one right
     * after it (no target, cleared head, a different sign) unless {@code forceNormal} says
     * otherwise; a period with none (a rest, or too few online) picks normally. A natural period
     * change first pays a surviving target; the admin rotate ({@code forceNormal}) never does.
     */
    private void advance(boolean forceNormal) {
        String day = period.get();
        if (!forceNormal) {
            paySurvivor();
        }
        if (!forceNormal && state != null && state.hasTarget()) {
            state = WantedState.cooldown(day);
        } else {
            Target target = pick();
            state = target == null ? WantedState.none(day)
                    : WantedState.posted(day, target.id(), target.name(), Market.factor(random, config.get().wantedSpread()),
                    clock.get(), isOnline(target.id()));
        }
        shownValue = null;
        stateStore.save(state);
    }

    /** Pays a target who played since posting and never died, nor got sold: survivor-share of the value now. */
    private void paySurvivor() {
        if (state == null || !state.hasTarget() || state.claimed() || state.died() || !state.played()) {
            return;
        }
        BigDecimal reward = Market.share(currentValue(), config.get().wantedSurvivorShare());
        if (reward.signum() <= 0) {
            return;
        }
        if (!payout.pay(plugin.getServer().getOfflinePlayer(state.targetId()), reward.doubleValue())) {
            logger.warning("Wanted: could not pay " + state.targetName() + " the $" + reward + " survivor reward (no economy)");
            return;
        }
        config.get().messages().send(broadcast, "wanted-survived", Placeholder.unparsed("name", state.targetName()),
                Placeholder.unparsed("price", reward.toBigInteger().toString()));
    }

    private boolean isOnline(UUID id) {
        return onlinePlayers.get().stream().anyMatch(p -> p.getUniqueId().equals(id));
    }

    private record Target(UUID id, String name) {
    }

    /**
     * {@code online-chance} an online player (only when someone is online to roll for), otherwise
     * anyone who has ever joined; null when fewer than {@code min-online} are on, or neither pool has
     * anyone (a fresh server, or everyone banned).
     */
    private Target pick() {
        if (onlinePlayers.get().size() < config.get().wantedMinOnline()) {
            return null;
        }
        List<Player> online = onlinePlayers.get().stream().filter(p -> !p.isBanned()).toList();
        if (!online.isEmpty() && random.nextDouble() < config.get().wantedOnlineChance()) {
            Player chosen = online.get(random.nextInt(online.size()));
            return new Target(chosen.getUniqueId(), chosen.getName());
        }
        List<OfflinePlayer> pool = everJoined.get().stream()
                .filter(p -> p.hasPlayedBefore() && p.getName() != null && !p.isBanned()).toList();
        if (pool.isEmpty()) {
            return null;
        }
        OfflinePlayer chosen = pool.get(random.nextInt(pool.size()));
        return new Target(chosen.getUniqueId(), chosen.getName());
    }

    private BigDecimal valueAt(long aliveTicks) {
        MarketConfig cfg = config.get();
        return Market.value(aliveTicks, state.factor(), cfg.wantedBase(), cfg.wantedPerHour(), cfg.wantedCap());
    }

    /** The bounty right now, from the target's current time alive. */
    private BigDecimal currentValue() {
        return valueAt(aliveTicks.applyAsLong(state.targetId()));
    }

    /** Writes the target's head and the WANTED sign, like SignWriter does for a stall's [Sell] signs. */
    public void apply(World world) {
        BigDecimal value = WantedSign.apply(world, layout, state, this::currentValue, config.get().wantedOldHeadShare());
        if (value != null) {
            shownValue = value;
        }
    }

    /**
     * Once a minute: redraws the sign when the live value has moved since it was last drawn. Skips
     * an unloaded sign chunk rather than loading spawn just to repaint a number nobody is near.
     */
    public void tick() {
        if (state == null || !state.hasTarget() || state.claimed()) {
            return;
        }
        MarketLayout.WantedSpot spot = layout.wantedBoard();
        World world = plugin.getServer().getWorld(config.get().world());
        if (spot == null || spot.sign() == null || world == null
                || !world.isChunkLoaded(spot.sign().x() >> 4, spot.sign().z() >> 4)) {
            return;
        }
        if (shownValue == null || currentValue().compareTo(shownValue) != 0) {
            BigDecimal value = WantedSign.writeSign(world, layout.signBlock(), spot.sign(), state, this::currentValue,
                    config.get().wantedOldHeadShare());
            if (value != null) {
                shownValue = value;
            }
        }
    }

    /** A target who logs in after posting has played, which the survivor reward needs. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (state != null && state.hasTarget() && !state.played() && event.getPlayer().getUniqueId().equals(state.targetId())) {
            state = state.withPlayed();
            stateStore.save(state);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        MarketLayout.WantedSpot spot = layout.wantedBoard();
        Block block = event.getClickedBlock();
        Stall.Slot sign = spot == null ? null : spot.sign();
        if (sign == null || block == null || block.getX() != sign.x() || block.getY() != sign.y() || block.getZ() != sign.z()) {
            return;
        }
        event.setCancelled(true);
        sell(event.getPlayer());
    }

    /**
     * Stamps a player's own unstamped {@code player_head} drop with the kill's time, so a later
     * sale can tell a fresh kill from a head somebody already had (Joe, 2026-10-09). Evidence this
     * reaches here (javap, Paper 26.3's {@code paper-26.3.jar}): {@code ServerPlayer.die()} sets its
     * {@code deathDropItems} field to a fresh list right before calling {@code dropFromLootTable}
     * (the player-head-drops datapack's {@code fill_player_head} loot function runs through there),
     * then {@code Entity.spawnAtLocation(ServerLevel, ItemStack, Vec3, Consumer)} redirects every
     * loot-table drop into that same list instead of spawning it, and {@code die()} merges
     * {@code deathDropItems} into the list it hands {@code CraftEventFactory.callPlayerDeathEvent},
     * which is the exact list {@link PlayerDeathEvent#getDrops()} returns. So the datapack's head is
     * one of this event's drops, not a separately spawned entity; no {@code ItemSpawnEvent} fallback
     * is needed. MONITOR priority: stamps whatever the final drop list looks like once every other
     * plugin that might edit it (graves, etc.) has had its say.
     *
     * <p>The head also gets the time alive this death ended, and a death of the current target is
     * noted (no survivor reward, and a fresh head with no stamp of its own, a cartel trophy, pays
     * that life's value). {@code ServerPlayer.die()} resets {@code time_since_death} only after
     * {@code callPlayerDeathEvent} returns (javap, Paper 26.3), so here it still holds the full life.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID deadId = event.getEntity().getUniqueId();
        Instant now = clock.get();
        long streak = aliveTicks.applyAsLong(deadId);
        for (ItemStack drop : event.getDrops()) {
            if (drop.getType() != Material.PLAYER_HEAD || !(drop.getItemMeta() instanceof SkullMeta skull)) {
                continue;
            }
            PlayerProfile profile = skull.getPlayerProfile();
            if (profile == null || !deadId.equals(profile.getId()) || KillStamp.killedAt(drop).isPresent()) {
                continue;
            }
            drop.editMeta(meta -> {
                KillStamp.stamp(meta, now);
                meta.getPersistentDataContainer().set(aliveKey, PersistentDataType.LONG, streak);
            });
        }
        if (state != null && state.hasTarget() && !state.claimed() && deadId.equals(state.targetId())) {
            state = state.withDeath(streak);
            stateStore.save(state);
        }
    }

    /** One sale per period, server-wide: the first player to bring in the target's head gets paid. */
    void sell(Player player) {
        var messages = config.get().messages();
        if (state == null || !state.hasTarget()) {
            messages.send(player, state != null && state.cooldown() ? "wanted-resting" : "wanted-none");
            return;
        }
        var name = Placeholder.unparsed("name", state.targetName());
        if (state.claimed()) {
            messages.send(player, "wanted-claimed", name);
            return;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType() != Material.PLAYER_HEAD || !(stack.getItemMeta() instanceof SkullMeta skull)
                || !WantedSale.matchesTarget(skull, state)) {
            messages.send(player, "wanted-wrong-head", name);
            return;
        }
        if (player.getUniqueId().equals(state.targetId())) {
            messages.send(player, "wanted-own-head");
            return;
        }
        boolean fresh = WantedSale.isFresh(stack, state);
        BigDecimal amount = fresh ? valueAt(WantedSale.freshAliveTicks(stack, state, aliveKey, aliveTicks))
                : Market.share(currentValue(), config.get().wantedOldHeadShare());
        if (!payout.pay(player, amount.doubleValue())) {
            messages.send(player, "wanted-closed");
            return;
        }
        WantedSale.takeOneHead(player, stack);
        state = state.claim(player.getUniqueId(), player.getName());
        stateStore.save(state);
        applyIfWorldLoaded();
        config.get().messages().send(broadcast, fresh ? "wanted-sold-fresh" : "wanted-sold-old",
                Placeholder.unparsed("seller", player.getName()), name,
                Placeholder.unparsed("price", amount.toBigInteger().toString()));
    }

    /** /market's extra line: today's target and whether it has been claimed yet. */
    public List<Component> todayLines() {
        var messages = config.get().messages();
        if (state == null || !state.hasTarget()) {
            return List.of(messages.render(state != null && state.cooldown() ? "today-wanted-resting" : "today-wanted-none"));
        }
        if (state.claimed()) {
            return List.of(messages.render("today-wanted-claimed", Placeholder.unparsed("name", state.targetName()),
                    Placeholder.unparsed("seller", state.claimedByName())));
        }
        BigDecimal value = currentValue();
        BigDecimal old = Market.share(value, config.get().wantedOldHeadShare());
        return List.of(messages.render("today-wanted-open", Placeholder.unparsed("name", state.targetName()),
                Placeholder.unparsed("price", value.toBigInteger().toString()),
                Placeholder.unparsed("old", old.toBigInteger().toString())));
    }

    /** The rotation broadcast's extra line, alongside Market's own {@code messages.rotated}; silent during a cooldown. */
    public void broadcastRotated(Audience audience) {
        if (state != null && state.hasTarget()) {
            config.get().messages().send(audience, "wanted-rotated", Placeholder.unparsed("name", state.targetName()),
                    Placeholder.unparsed("price", currentValue().toBigInteger().toString()));
        }
    }

    public void applyIfWorldLoaded() {
        World world = plugin.getServer().getWorld(config.get().world());
        if (world != null) {
            apply(world);
        }
    }

    /** The current period's state, for tests. */
    WantedState state() {
        return state;
    }
}
