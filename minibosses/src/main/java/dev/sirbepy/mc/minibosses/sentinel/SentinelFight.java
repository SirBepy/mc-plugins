package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.arena.ArenaRegistry;
import dev.sirbepy.mc.minibosses.engine.BossBrain;
import dev.sirbepy.mc.minibosses.engine.EngineKeys;
import dev.sirbepy.mc.minibosses.engine.FightArea;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.GuardianRuntime;
import dev.sirbepy.mc.minibosses.engine.TestFight;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.GuardianTierRow;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.BedrockStandIn;
import dev.sirbepy.mc.minibosses.rig.RigBody;
import dev.sirbepy.mc.minibosses.rig.RigDefinition;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelAnchors;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelDefence;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelPlans;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the Overgrown Sentinel's spawn (with solo/group scaling via {@link SentinelScaling}),
 * fighter tracking, the per-tier attack kit it is started with (see {@link SentinelPlans}), the
 * hit flash of its body, the damage scaling of its Heartguard shield and stagger window, hits on
 * its anchors, and death handling - loot, the {@code minibosses} Hall of Fame, and the
 * {@code guild} {@code GuardianDefeatedEvent} contract hook. A single
 * {@link Ravager} stands in for the whole boss (no {@code ComplexEntityPart}s needed): chosen
 * over an {@link org.bukkit.entity.IronGolem} because this repo's own
 * {@code core.dragon.DragonFightersTest} already proves a Ravager works as the generalized
 * single-part-boss stand-in, and its native charge/stomp kit reads as "ground attacks" with no
 * extra AI code, where an Iron Golem's single punch and reputation-gated hostility toward players
 * would need more custom AI work than this foundation dispatch is scoped for.
 */
public final class SentinelFight implements Listener {

    public static final String GUARDIAN_ID = "overgrown_sentinel";

    /** Half the width and the height of the fight area where no arena is registered: the 30 block square the engine defaults to. */
    private static final int FALLBACK_HALF_WIDTH = 15;
    private static final int FALLBACK_HEIGHT = 20;

    private final JavaPlugin plugin;
    private final GuardianRegistry guardians;
    private final DragonFighters fighters;
    private final SentinelLoot loot;
    private final HallOfFame hallOfFame;
    private final Logger logger;
    private final GuardianRuntime runtime;
    private final ArenaRegistry arenas;
    private final Predicate<UUID> isBedrock;
    /** Empty when the rig file is missing or malformed: the Sentinel then fights as its plain Ravager. */
    private final Optional<RigDefinition> rig;

    private final NamespacedKey guardianKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey testFightKey;
    private final NamespacedKey centerKey;

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    /** Live model bodies by boss UUID, for the join refresh and the stand-in hit forwarding. */
    private final Map<UUID, RigBody> bodies = new ConcurrentHashMap<>();
    private final Map<UUID, HitFlash> flashes = new ConcurrentHashMap<>();

    /** {@code abilities} is no longer read: the Sentinel's kit is its phase plan, not Guardian-file ability ids. */
    public SentinelFight(JavaPlugin plugin, GuardianRegistry guardians, GuardianAbilityRegistry abilities,
                          DragonFighters fighters, SentinelLoot loot, HallOfFame hallOfFame, Logger logger,
                          GuardianRuntime runtime, ArenaRegistry arenas, Predicate<UUID> isBedrock) {
        this.plugin = plugin;
        this.guardians = guardians;
        this.fighters = fighters;
        this.loot = loot;
        this.hallOfFame = hallOfFame;
        this.logger = logger;
        this.runtime = runtime;
        this.arenas = arenas;
        this.isBedrock = isBedrock;
        this.rig = RigDefinition.load(GUARDIAN_ID, logger);
        this.guardianKey = new NamespacedKey(plugin, "guardian");
        this.tierKey = new NamespacedKey(plugin, "guardian_tier");
        this.testFightKey = TestFight.key(plugin);
        this.centerKey = new NamespacedKey(plugin, "spawn_center");
        runtime.register(GUARDIAN_ID, this::resume);
    }

    /** Spawns a fresh Overgrown Sentinel at {@code location} fought at {@code tier}; empty (and
     * logged) if the Guardian YAML has not loaded - should never happen once the file ships, but a
     * missing/malformed file must fail safe rather than NPE a summon attempt. */
    public Optional<LivingEntity> spawn(Location location, Tier tier) {
        GuardianDef def = guardians.get(GUARDIAN_ID).orElse(null);
        if (def == null) {
            logger.warning("Cannot spawn " + GUARDIAN_ID + ": its Guardian definition is not loaded.");
            return Optional.empty();
        }
        GuardianTierRow row = def.tiers().get(tier);

        // Fighter count is read once, here, at summon time: whoever is close enough to see the boss
        // bar is close enough to be "in the fight", the same radius used below to add them to it.
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player nearby : location.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) <= 64 * 64) {
                nearbyPlayers.add(nearby);
            }
        }
        int fighterCount = Math.max(nearbyPlayers.size(), 1);
        double health = SentinelScaling.scaledHealth(row.health(), fighterCount);
        double damage = SentinelScaling.scaledDamage(row.damage(), fighterCount);

        Ravager sentinel = (Ravager) location.getWorld().spawnEntity(location, EntityType.RAVAGER);
        AttributeInstance maxHealth = sentinel.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        sentinel.setHealth(health);
        AttributeInstance attack = sentinel.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(damage);
        }
        // Named for death messages; the nameplate itself stays hidden (the boss bar names it, and the
        // model's invisible Ravager would otherwise float a tag over nothing).
        sentinel.customName(Component.text("The Overgrown Sentinel", NamedTextColor.DARK_GREEN));
        sentinel.setCustomNameVisible(false);
        // A boss fight can draw players out of range mid-engagement; it must not despawn under them.
        sentinel.setPersistent(true);

        PersistentDataContainer pdc = sentinel.getPersistentDataContainer();
        pdc.set(guardianKey, PersistentDataType.STRING, def.id());
        pdc.set(tierKey, PersistentDataType.STRING, tier.name());
        // Kept so an arena-less fight keeps the same area after a restart.
        pdc.set(centerKey, PersistentDataType.STRING, location.getX() + "," + location.getY() + "," + location.getZ());

        BossBar bar = Bukkit.createBossBar("The Overgrown Sentinel (" + tier.displayName() + ")",
                BarColor.GREEN, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player nearby : nearbyPlayers) {
            bar.addPlayer(nearby);
        }
        bossBars.put(sentinel.getUniqueId(), bar);

        attachBody(runtime.start(sentinel, tier, SentinelPlans.forTier(tier), areaAround(location)));

        return Optional.of(sentinel);
    }

    /** The Sentinel's arena once one is registered, else the default square around where it rose. */
    private FightArea areaAround(Location center) {
        return arenas.get(GUARDIAN_ID).map(FightArea::of)
                .orElseGet(() -> FightArea.around(center, FALLBACK_HALF_WIDTH, FALLBACK_HEIGHT));
    }

    /** Dresses the fight in the model rig; a rig that fails to build leaves the plain Ravager fighting. */
    private void attachBody(BossBrain brain) {
        if (rig.isEmpty()) {
            return;
        }
        FightContext ctx = brain.context();
        SentinelAnchors.clearStale(ctx);
        // A boss saved mid-attack must not wake up with its AI still held off.
        if (ctx.boss() instanceof Mob mob) {
            ctx.effects().hold(mob, false);
        }
        try {
            RigBody body = new RigBody(rig.get(), ctx, isBedrock);
            ctx.animator(body);
            bodies.put(ctx.boss().getUniqueId(), body);
            HitFlash flash = new HitFlash(body);
            ctx.addTicker(flash);
            flashes.put(ctx.boss().getUniqueId(), flash);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not build the Sentinel's model; it fights without one", e);
        }
    }

    /** A boss found alive after a restart: same tier plan, the area it rose in, and the model rebuilt on its new brain. */
    private Optional<GuardianRuntime.Resumed> resume(LivingEntity boss, Tier tier) {
        return Optional.of(new GuardianRuntime.Resumed(SentinelPlans.forTier(tier), areaAround(storedCenter(boss)), this::attachBody));
    }

    private Location storedCenter(LivingEntity boss) {
        String raw = boss.getPersistentDataContainer().get(centerKey, PersistentDataType.STRING);
        if (raw != null) {
            String[] parts = raw.split(",");
            try {
                return new Location(boss.getWorld(), Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]));
            } catch (RuntimeException e) {
                logger.fine("Unreadable spawn centre '" + raw + "' on " + boss.getUniqueId() + "; using where it stands.");
            }
        }
        return boss.getLocation();
    }

    private boolean isOurs(LivingEntity entity) {
        return GUARDIAN_ID.equals(entity.getPersistentDataContainer().get(guardianKey, PersistentDataType.STRING));
    }

    private Tier tierOf(LivingEntity entity) {
        String name = entity.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return name == null ? Tier.NORMAL : Tier.valueOf(name);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity boss) || !isOurs(boss)) {
            return;
        }
        // Before anything reads the damage: credit, the bar and the flash all see what the boss really takes.
        double scale = runtime.get(boss.getUniqueId()).map(b -> SentinelDefence.of(b.context()).damageMultiplier()).orElse(1.0);
        if (scale != 1.0) {
            event.setDamage(event.getDamage() * scale);
        }
        fighters.onDamage(event, boss);
        if (event instanceof EntityDamageByEntityEvent byEntity && boss instanceof Mob mob
                && byEntity.getDamager() instanceof Player player) {
            // Vanilla targeting for a natural Ravager is raid-only; force aggro onto whoever hits it
            // so a solo or group fight reliably engages without needing a raid context.
            mob.setTarget(player);
        }
        updateBossBar(boss);
        HitFlash flash = flashes.get(boss.getUniqueId());
        if (flash != null) {
            flash.hit();
        }
    }

    private void updateBossBar(LivingEntity boss) {
        updateBossBar(boss, boss.getHealth());
    }

    private void updateBossBar(LivingEntity boss, double health) {
        BossBar bar = bossBars.get(boss.getUniqueId());
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (bar != null && max != null && max.getValue() > 0) {
            bar.setProgress(Math.max(0, Math.min(1, health / max.getValue())));
        }
    }

    /** Wild Regrowth heals the boss between hits; the bar shows it as it happens (the event fires before the health changes). */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity boss && isOurs(boss)) {
            updateBossBar(boss, boss.getHealth() + event.getAmount());
        }
    }

    /**
     * A hit on one of the boss's anchors never damages it: every damage event aimed at an anchor is
     * cancelled (explosions, fire, the boss's own blows), and only a player's blow or arrow counts toward
     * breaking it. Handled even when the event arrives already cancelled.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnchorDamage(EntityDamageEvent event) {
        Entity anchor = event.getEntity();
        if (!SentinelAnchors.isAnchor(anchor)) {
            return;
        }
        event.setCancelled(true);
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? playerBehind(byEntity.getDamager()) : null;
        if (attacker != null) {
            anchorsOf(anchor).ifPresent(anchors -> anchors.hit(anchor, attacker));
        }
    }

    /** Right-clicking an anchor would swap its block for the player's item. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnchorManipulate(PlayerArmorStandManipulateEvent event) {
        if (SentinelAnchors.isAnchor(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /** The anchors of the fight that owns this anchor stand, if that fight is still running. */
    private Optional<SentinelAnchors> anchorsOf(Entity anchor) {
        String owner = anchor.getPersistentDataContainer().get(EngineKeys.owner(plugin), PersistentDataType.STRING);
        if (owner == null) {
            return Optional.empty();
        }
        try {
            return runtime.get(UUID.fromString(owner)).map(brain -> SentinelAnchors.of(brain.context()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (!isOurs(boss)) {
            return;
        }
        Tier tier = tierOf(boss);
        event.getDrops().clear();
        if (TestFight.is(boss, testFightKey)) {
            // A /guardianadmin test rehearsal pays and records nothing: no loot, no XP, no Hall of Fame,
            // and no GuardianDefeatedEvent (which would credit Guild contracts, kills, tiers and fragments).
            event.setDroppedExp(0);
        } else {
            event.getDrops().addAll(loot.forTier(tier));

            Set<UUID> fighterIds = fighters.fighters(boss);
            hallOfFame.guardianKilled(GUARDIAN_ID, tier, fighterIds, boss.getWorld().getName());
            // A plain Bukkit event, not a direct call: Guild is a softdepend (plugin.yml), so this must
            // not require Guild to be installed - firing to no listener is harmless, same shape as
            // GuardianDefeatedEvent's own javadoc describes.
            Bukkit.getPluginManager().callEvent(new GuardianDefeatedEvent(GUARDIAN_ID, tier.name(), fighterIds));
        }

        BossBar bar = bossBars.remove(boss.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        bodies.remove(boss.getUniqueId());
        flashes.remove(boss.getUniqueId());
    }

    /**
     * A Bedrock player hits the stand-in they can see: the hit never lands on it, it is replayed as
     * the same player hitting the real boss, so credit, aggro and the boss bar behave as in a Java hit.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onStandInDamage(EntityDamageEvent event) {
        Optional<UUID> owner = BedrockStandIn.ownerOf(plugin, event.getEntity());
        if (owner.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        RigBody body = bodies.get(owner.get());
        // The server sweeps entities it hid from a Java client too, and the stand-in sits on the
        // boss, so a sword sweep at the boss always clips it; replaying that would hit twice.
        if (body == null || !(event instanceof EntityDamageByEntityEvent byEntity)
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        Player attacker = playerBehind(byEntity.getDamager());
        LivingEntity boss = body.boss();
        if (attacker != null && boss.isValid() && !boss.isDead()) {
            // Credited and provoked here as well as through the damage event the server fires for the
            // replayed hit: both are idempotent, and neither then depends on that event's shape.
            fighters.addFighter(boss.getPersistentDataContainer(), attacker.getUniqueId());
            if (boss instanceof Mob mob) {
                mob.setTarget(attacker);
            }
            boss.damage(byEntity.getDamage(), attacker);
        }
    }

    private static Player playerBehind(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
    }

    /** The stand-in is a prop: if it dies anyway (a command), it pays nothing. */
    @EventHandler
    public void onStandInDeath(EntityDeathEvent event) {
        if (event.getEntity().getScoreboardTags().contains(BedrockStandIn.TAG)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /** Keeps the stand-in hidden from a Java player who logs in mid-fight, and shown to a Bedrock one. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        bodies.values().removeIf(body -> !body.standIn().entity().isValid());
        for (RigBody body : bodies.values()) {
            body.playerJoined(event.getPlayer());
        }
    }

    /** Who hurt this boss this fight - also exposed for tests, since {@link #onDeath} already uses
     * it internally to feed both the Hall of Fame and the Guild's {@code GuardianDefeatedEvent}. */
    public Set<UUID> fightersOf(LivingEntity boss) {
        return fighters.fighters(boss);
    }
}
