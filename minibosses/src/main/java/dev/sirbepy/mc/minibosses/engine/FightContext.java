package dev.sirbepy.mc.minibosses.engine;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Everything an attack needs to know about the fight it is part of: the boss, its tier and area,
 * who is fighting, a random source, the cleanup registry, and the helpers that hit players on the
 * boss's behalf. One per live boss, shared by its {@link BossBrain} and every attack it runs.
 */
public final class FightContext {

    private final Plugin plugin;
    private final LivingEntity boss;
    private final Tier tier;
    private final FightArea area;
    private final Random rng;
    private final FightEntities entities;
    private final FightEffects effects;
    private final Predicate<Player> onGround;
    private final List<FightTicker> tickers = new ArrayList<>();
    private final Map<Class<?>, Object> state = new HashMap<>();
    private Animator animator = Animator.NONE;
    private int phase;

    /**
     * {@code onGround} is a seam: the live check reads the client-reported flag, which a test cannot
     * drive, so {@link Telegraph#grounded()} strikes ask this instead of the player directly.
     */
    public FightContext(Plugin plugin, LivingEntity boss, Tier tier, FightArea area, Random rng,
            FightEntities entities, FightEffects effects, Predicate<Player> onGround) {
        this.plugin = plugin;
        this.boss = boss;
        this.tier = tier;
        this.area = area;
        this.rng = rng;
        this.entities = entities;
        this.effects = effects;
        this.onGround = onGround;
    }

    public Plugin plugin() {
        return plugin;
    }

    public LivingEntity boss() {
        return boss;
    }

    public Tier tier() {
        return tier;
    }

    public FightArea area() {
        return area;
    }

    public Random rng() {
        return rng;
    }

    public FightEntities entities() {
        return entities;
    }

    public FightEffects effects() {
        return effects;
    }

    public Animator animator() {
        return animator;
    }

    /** Set once the boss's model rig exists; until then poses go nowhere. */
    public void animator(Animator animator) {
        this.animator = animator == null ? Animator.NONE : animator;
    }

    /**
     * Keeps {@code ticker} running for the rest of the fight, whatever attack is or is not in
     * progress, until it reports done or the fight ends.
     */
    public void addTicker(FightTicker ticker) {
        tickers.add(ticker);
    }

    /**
     * One per-fight object of {@code type}, made by {@code init} on first use. How a hazard that
     * several attacks feed (thorn patches) finds the single copy this fight owns, without the
     * attacks themselves holding any per-fight state.
     */
    public <T> T state(Class<T> type, Function<FightContext, T> init) {
        return type.cast(state.computeIfAbsent(type, key -> init.apply(this)));
    }

    /** Advances every ticker, dropping the ones that are done. Called by the brain once per tick. */
    void tickTickers() {
        for (FightTicker ticker : new ArrayList<>(tickers)) {
            if (ticker.tick()) {
                tickers.remove(ticker);
                ticker.close();
            }
        }
    }

    /** Closes every ticker and forgets the per-fight state: the fight is over. */
    void endTickers() {
        for (FightTicker ticker : new ArrayList<>(tickers)) {
            ticker.close();
        }
        tickers.clear();
        state.clear();
    }

    /** Index of the phase the fight is in now (0 is the opening phase). */
    public int phase() {
        return phase;
    }

    void phase(int phase) {
        this.phase = phase;
    }

    public boolean isGrounded(Player player) {
        return onGround.test(player);
    }

    /** Boss health as a 0 to 1 fraction of its max; 1 if the boss has no max-health attribute. */
    public double healthFraction() {
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || max.getValue() <= 0) {
            return 1.0;
        }
        return Math.max(0, Math.min(1, boss.getHealth() / max.getValue()));
    }

    /** Online, alive, in Survival or Adventure, and inside the fight area. */
    public boolean isEligible(Player player) {
        GameMode mode = player.getGameMode();
        return player.isOnline() && !player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && area.contains(player.getLocation());
    }

    /** Everyone the boss is fighting right now. */
    public List<Player> fighters() {
        return area.world().getPlayers().stream().filter(this::isEligible).toList();
    }

    public void damage(Player target, double amount) {
        damage(target, amount, DamageType.MOB_ATTACK);
    }

    /**
     * Hurts {@code target} as the boss, so the death message names it and plugins reading the damage
     * event see the Guardian, not an anonymous source.
     */
    public void damage(Player target, double amount, DamageType type) {
        effects.hurt(target, amount, sourceOf(type));
    }

    /** The damage source {@link #damage} uses: the boss as both the direct and the causing entity. */
    public DamageSource sourceOf(DamageType type) {
        return DamageSource.builder(type).withCausingEntity(boss).withDirectEntity(boss).build();
    }
}
