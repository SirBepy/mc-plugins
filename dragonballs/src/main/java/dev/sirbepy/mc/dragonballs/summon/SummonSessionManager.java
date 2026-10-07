package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.effects.SummonScene;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import dev.sirbepy.mc.dragonballs.summon.wish.WishJsonWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Owns the single, server-wide active wish session (never more than one at a time, regardless of
 * which of the 3 sets it summoned), from the moment a dragon is chosen through to the final
 * scatter. Main-thread only throughout: the trigger listener, the chat listener's scheduled hop,
 * {@code /shenron}, and the session ticker all run on the main thread, so nothing here is
 * synchronized.
 *
 * <p>{@link #tryStart} is the single entry point that both the real choice-of-dragon UI and tests
 * call: it re-validates every trigger condition itself (the 60s dialog/form/chat choice could have
 * gone stale), which is what makes the UI layer safe to leave untested.
 */
public final class SummonSessionManager {

    private final Logger logger;
    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    private final Supplier<SummonConfig> summonConfig;
    private final SummonEffects effects;
    private final CurseStore curses;
    private final File wishesFile;
    private final LongSupplier clock;
    private final Random idRandom;

    private SummonSession session;

    public SummonSessionManager(Logger logger, BallSets sets, Supplier<DragonBallsConfig> config,
                                 Supplier<SummonConfig> summonConfig, SummonEffects effects, CurseStore curses,
                                 File wishesFile) {
        this(logger, sets, config, summonConfig, effects, curses, wishesFile, System::currentTimeMillis, new Random());
    }

    /** Test-only constructor: injects the clock (idle/offline timeouts) and the session-id RNG. */
    SummonSessionManager(Logger logger, BallSets sets, Supplier<DragonBallsConfig> config,
                          Supplier<SummonConfig> summonConfig, SummonEffects effects, CurseStore curses,
                          File wishesFile, LongSupplier clock, Random idRandom) {
        this.logger = logger;
        this.sets = sets;
        this.config = config;
        this.summonConfig = summonConfig;
        this.effects = effects;
        this.curses = curses;
        this.wishesFile = wishesFile;
        this.clock = clock;
        this.idRandom = idRandom;
    }

    // ---- read-only state, for the trigger listener and tests ----

    public boolean hasActiveSession() {
        return session != null;
    }

    public boolean isSummoner(UUID playerId) {
        return session != null && session.summonerId.equals(playerId);
    }

    /** Readiness of {@code set}'s own 7 balls; {@code SESSION_ACTIVE} fires regardless of which set
     * is active, since only one session ever runs server-wide. */
    public SummonTrigger.Readiness evaluate(UUID playerId, Location clicked, BallSet set) {
        BallRegistry registry = sets.registry(set);
        return SummonTrigger.evaluate(registry.getBalls(), registry.isDormant(), session != null,
                curses.isWishBanned(playerId), clicked, summonConfig.get().radius());
    }

    public int wishesLeft() {
        return session == null ? 0 : session.wishesLeft;
    }

    public DragonType activeDragon() {
        return session == null ? null : session.dragon;
    }

    public UUID activeSummoner() {
        return session == null ? null : session.summonerId;
    }

    public String statusText() {
        SummonSession s = session;
        if (s == null) {
            return "No dragon is summoned.";
        }
        long idleSeconds = (clock.getAsLong() - s.lastActivityMillis) / 1000;
        return "session=" + s.id + " set=" + s.set.id() + " summoner=" + s.summonerName + " dragon=" + s.dragon.displayName()
                + " wishesLeft=" + s.wishesLeft + " idleSeconds=" + idleSeconds;
    }

    // ---- starting a session: the isolated "choice made" callback ----

    /** Re-checks every trigger condition, then starts the session if they all still hold.
     * @return true if a session was started. */
    public boolean tryStart(Player summoner, DragonType dragon, Location clicked, BallSet set) {
        if (evaluate(summoner.getUniqueId(), clicked, set) != SummonTrigger.Readiness.READY) {
            return false;
        }
        BallRegistry registry = sets.registry(set);
        List<BallView> balls = registry.getBalls();
        World world = clicked.getWorld();
        Map<Integer, Location> origins = new LinkedHashMap<>();
        for (BallView ball : balls) {
            origins.put(ball.star(), new Location(world, ball.x(), ball.y(), ball.z()));
        }
        double[] avg = SummonTrigger.averagePosition(balls);
        Location center = new Location(world, avg[0], avg[1], avg[2]);

        SummonScene scene = new SummonScene(summoner.getUniqueId(), summoner.getName(), dragon, set, center, origins);
        session = new SummonSession(randomId(), summoner.getUniqueId(), summoner.getName(), dragon, set, scene,
                clock.getAsLong());

        effects.onSummon(scene);
        Bukkit.broadcast(Component.text(summoner.getName() + " has summoned " + dragon.displayName() + "!", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        String opening = dragon == DragonType.SHENRON
                ? "I am the Eternal Dragon. State your wish. I shall grant one."
                : "I am the Eternal Dragon. State your wish. I shall grant three.";
        speak(session, opening);
        return true;
    }

    // ---- summoner chat capture ----

    /** Called (already on the main thread) with the summoner's raw message; a no-op if the session
     * ended between the async chat event and this scheduled hop. */
    public void recordWish(Player player, String rawMessage) {
        SummonSession s = session;
        if (s == null || !s.summonerId.equals(player.getUniqueId())) {
            return;
        }
        String message = truncate(rawMessage, SummonConfig.MAX_WISH_MESSAGE_CHARS);
        Bukkit.broadcast(Component.text(player.getName() + " to " + s.dragon.displayName() + ": ", NamedTextColor.YELLOW)
                .append(Component.text(message, NamedTextColor.WHITE)));
        logger.info(wishLogLine(s, player.getName(), player.getUniqueId(), message));
        appendLine(WishJsonWriter.wishLine(Instant.now(), s.id, s.dragon.displayName(), s.wishesLeft,
                player.getName(), player.getUniqueId(), message, s.set.id()));
        s.lastActivityMillis = clock.getAsLong();
    }

    /** The console line Player Claude judges from; the set is in it because a Nether or End dragon
     * grants bigger wishes than an Overworld one (docs/dragonballs.md, Guide values). */
    static String wishLogLine(SummonSession s, String playerName, UUID playerId, String message) {
        return "[DragonBalls] WISH " + s.id + " " + s.dragon.displayName() + " " + s.set.id() + " " + s.wishesLeft + " "
                + playerName + " (" + playerId + "): " + message;
    }

    static String truncate(String message, int max) {
        return message.length() <= max ? message : message.substring(0, max) + " (truncated)";
    }

    // ---- /shenron, the judge's interface ----

    /** @return an error message for the sender, or null on success. */
    public String say(String text) {
        SummonSession s = session;
        if (s == null) {
            return "No dragon is summoned.";
        }
        if (text == null || text.isBlank()) {
            return "Say what?";
        }
        speak(s, text);
        s.judgeActed = true;
        s.lastActivityMillis = clock.getAsLong();
        return null;
    }

    public String grant(int count, String note) {
        SummonSession s = session;
        if (s == null) {
            return "No dragon is summoned.";
        }
        WishAccounting.GrantResult result = WishAccounting.grant(s.wishesLeft, count);
        if (!result.accepted()) {
            return result.error();
        }
        s.wishesLeft = result.remainingAfter();
        s.grantedAny = true;
        s.judgeActed = true;
        s.lastActivityMillis = clock.getAsLong();
        logger.info("[DragonBalls] GRANTED " + s.id + " " + count + " " + note);
        appendLine(WishJsonWriter.grantedLine(Instant.now(), s.id, count, note, s.set.id()));
        if (result.sessionEnds()) {
            endSession(false);
        }
        return null;
    }

    public String dismiss(String text) {
        SummonSession s = session;
        if (s == null) {
            return "No dragon is summoned.";
        }
        if (text != null && !text.isBlank()) {
            speak(s, text);
        }
        endSession(false);
        return null;
    }

    // ---- timeouts, driven by a repeating main-thread task (never a per-tick scan) ----

    public void tick() {
        SummonSession s = session;
        if (s == null) {
            return;
        }
        long now = clock.getAsLong();
        SummonConfig cfg = summonConfig.get();

        if (!s.consideringAnnounced && !s.judgeActed
                && now - s.startMillis >= SummonConfig.CONSIDERING_ANNOUNCE_SECONDS * 1000L) {
            s.consideringAnnounced = true;
            speak(s, "...the dragon is considering your words.");
        }

        Player summoner = Bukkit.getPlayer(s.summonerId);
        if (summoner == null) {
            if (s.offlineSinceMillis == 0) {
                s.offlineSinceMillis = now;
            }
            if (now - s.offlineSinceMillis >= cfg.summonerOfflineSeconds() * 1000L) {
                expireSession();
                return;
            }
        } else {
            s.offlineSinceMillis = 0;
        }

        if (now - s.lastActivityMillis >= cfg.idleTimeoutSeconds() * 1000L) {
            expireSession();
        }
    }

    private void expireSession() {
        SummonSession s = session;
        if (s == null) {
            return;
        }
        speak(s, "I grow weary. Your remaining wishes are lost.");
        endSession(false);
    }

    // ---- plugin disable ----

    /** Ends any active session immediately (same physical cleanup as a normal end), calling {@code
     * effects.shutdown()} instead of {@code onEnd}, and skipping the "balls scatter" chat broadcast
     * since the server is already going down. */
    public void shutdown() {
        if (session == null) {
            effects.shutdown();
            return;
        }
        endSession(true);
    }

    // ---- shared end-of-session plumbing ----

    private void endSession(boolean disabling) {
        SummonSession s = session;
        if (s == null) {
            return;
        }
        BallRegistry registry = sets.registry(s.set);
        removeHeadBlocks(s.scene.ballOrigins());
        if (s.grantedAny) {
            registry.incrementWishCount();
        }
        Location center = s.scene.center();
        registry.setLastSummon(center);
        applyStoneUntil(registry, s.set);
        Map<Integer, Location> destinations = registry.scatterAll(center, true);
        if (disabling) {
            effects.shutdown();
        } else {
            effects.onEnd(s.scene, destinations);
            Bukkit.broadcast(Component.text("The Dragon Balls scatter across the world!", NamedTextColor.GOLD));
        }
        session = null;
    }

    private void applyStoneUntil(BallRegistry registry, BallSet set) {
        DragonBallsConfig cfg = config.get();
        if (cfg.stoneMode() == StoneMode.MINECRAFT_DAYS) {
            World world = set.world(cfg);
            long currentDay = world == null ? 0 : Math.floorDiv(world.getFullTime(), 24000L);
            registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, currentDay + cfg.stoneDays());
        } else {
            registry.setStoneUntil(StoneMode.REAL_HOURS, clock.getAsLong() + Duration.ofHours(cfg.stoneRealHours()).toMillis());
        }
    }

    private void removeHeadBlocks(Map<Integer, Location> origins) {
        for (Location loc : origins.values()) {
            if (loc.getBlock().getState() instanceof Skull) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    private void speak(SummonSession s, String text) {
        Bukkit.broadcast(DragonSpeech.line(s.dragon, text));
        logger.info("[DragonBalls] DRAGON " + s.id + ": " + text);
        effects.onSpeak(s.scene, text);
    }

    private void appendLine(String line) {
        try {
            WishJsonWriter.append(wishesFile, line);
        } catch (IOException e) {
            logger.warning("Failed to append to wishes.jsonl: " + e.getMessage());
        }
    }

    private String randomId() {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder id = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            id.append(alphabet.charAt(idRandom.nextInt(alphabet.length())));
        }
        return id.toString();
    }
}
