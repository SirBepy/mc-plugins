package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * What a {@link ClassKit} may use: its class's ranks (0 whenever the class isn't the player's
 * active one, so a kit's talents never leak into another class), the shared resource, cooldowns,
 * summons, effects, allies, and the scheduler.
 */
public final class KitContext {

    private final RpgClass rpgClass;
    private final Plugin plugin;
    private final Classes classes;
    private final Talents talents;
    private final Resources resources;
    private final Cooldowns cooldowns;
    private final Summons summons;
    private final CombatTracker combat;
    private final Fx fx;
    private final DoubleSupplier random;

    public KitContext(RpgClass rpgClass, Plugin plugin, Classes classes, Talents talents, Resources resources,
                      Cooldowns cooldowns, Summons summons, CombatTracker combat, Fx fx, DoubleSupplier random) {
        this.rpgClass = rpgClass;
        this.plugin = plugin;
        this.classes = classes;
        this.talents = talents;
        this.resources = resources;
        this.cooldowns = cooldowns;
        this.summons = summons;
        this.combat = combat;
        this.fx = fx;
        this.random = random;
    }

    public RpgClass rpgClass() {
        return rpgClass;
    }

    public Plugin plugin() {
        return plugin;
    }

    /** Whether this kit's class is the player's active class. Check it first in every listener. */
    public boolean active(Player player) {
        return active(player.getUniqueId());
    }

    public boolean active(UUID player) {
        return classes.active(player) == rpgClass;
    }

    /** The player's level in this kit's class. */
    public int level(Player player) {
        return classes.profile(player.getUniqueId()).level(rpgClass);
    }

    /** The talent's rank, 0 unless this kit's class is active. */
    public int rank(Player player, String talentId) {
        return active(player) ? talents.rank(player.getUniqueId(), rpgClass, talentId) : 0;
    }

    /** Adds to the player's resource, only while this kit's class is active. */
    public void gain(Player player, double amount) {
        if (active(player)) resources.gain(player, amount);
    }

    public Resources resources() {
        return resources;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    /** Takes {@code millis} off ability 1's running cooldown, or off its keystone replacement's. */
    public void shortenAbilityOne(Player player, long millis) {
        classes.shortenCooldown(player.getUniqueId(), millis);
        cooldowns.shorten(player.getUniqueId(), Slot.ONE, millis);
    }

    public Summons summons() {
        return summons;
    }

    public Fx fx() {
        return fx;
    }

    public List<Player> allies(Player player, double radius) {
        return combat.allies(player, radius);
    }

    /** Ability damage to a hostile mob, credited to {@code caster}; see {@link AbilityDamage}. */
    public boolean damage(LivingEntity target, double amount, Player caster) {
        return AbilityDamage.deal(target, amount, caster);
    }

    public void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public BukkitTask later(Runnable task, long ticks) {
        return plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
    }

    public BukkitTask repeat(Runnable task, long delay, long period) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    /** 0 (inclusive) to 1 (exclusive); tests fix it. */
    public double random() {
        return random.getAsDouble();
    }

    public long now() {
        return classes.now();
    }
}
