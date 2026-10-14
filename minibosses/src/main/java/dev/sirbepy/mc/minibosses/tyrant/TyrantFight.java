package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.GuardianTierRow;
import dev.sirbepy.mc.minibosses.registry.Tier;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Owns the Ashen Tyrant's spawn, fighter tracking, half-health blaze-summon trigger, a
 * Fire-Aspect-style burn on every hit it lands, and death loot - the fight-level wiring for Normal
 * tier only, mirroring the shape {@code sentinel.SentinelFight}'s own Phase A dispatch established
 * (see {@link BlazeSummonAbility} and {@link TyrantLoot} for why Heroic/Mythic content is out of
 * scope here). A single {@link WitherSkeleton} stands in for the whole boss: chosen over a
 * {@link org.bukkit.entity.PiglinBrute} because a Wither Skeleton is the mob that actually spawns
 * inside a real Nether Fortress (a Piglin Brute guards Bastion Remnants, a different structure),
 * is naturally fire-immune so its own "fire attacks" theme never burns itself, and already carries
 * a stone sword and a humanoid "ancient warrior" silhouette with no extra AI code needed - the
 * same "reuse what the vanilla mob already does" judgment the Sentinel dispatch applied to its own
 * Ravager choice. Fire Aspect is applied by hand on every hit the boss lands (see {@link #onDamage})
 * rather than via an enchanted weapon, since a Wither Skeleton's own natural melee already applies
 * Wither and this dispatch does not attempt to suppress that vanilla side effect - both read as
 * "a cursed fortress warrior" without conflicting.
 */
public final class TyrantFight implements Listener {

    public static final String GUARDIAN_ID = "ashen_tyrant";

    static final int FIRE_TICKS = 100; // 5 seconds of burning, a Fire-Aspect-I-ish duration

    private final GuardianRegistry guardians;
    private final GuardianAbilityRegistry abilities;
    private final DragonFighters fighters;
    private final TyrantLoot loot;
    private final Logger logger;

    private final NamespacedKey guardianKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey blazeSummonTriggeredKey;

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public TyrantFight(JavaPlugin plugin, GuardianRegistry guardians, GuardianAbilityRegistry abilities,
                        DragonFighters fighters, TyrantLoot loot, Logger logger) {
        this.guardians = guardians;
        this.abilities = abilities;
        this.fighters = fighters;
        this.loot = loot;
        this.logger = logger;
        this.guardianKey = new NamespacedKey(plugin, "guardian");
        this.tierKey = new NamespacedKey(plugin, "guardian_tier");
        this.blazeSummonTriggeredKey = new NamespacedKey(plugin, "blaze_summon_triggered");
    }

    /** Spawns a fresh Ashen Tyrant at {@code location} fought at {@code tier}; empty (and logged)
     * if the Guardian YAML has not loaded - should never happen once the file ships, but a
     * missing/malformed file must fail safe rather than NPE a summon attempt. */
    public Optional<LivingEntity> spawn(Location location, Tier tier) {
        GuardianDef def = guardians.get(GUARDIAN_ID).orElse(null);
        if (def == null) {
            logger.warning("Cannot spawn " + GUARDIAN_ID + ": its Guardian definition is not loaded.");
            return Optional.empty();
        }
        GuardianTierRow row = def.tiers().get(tier);

        WitherSkeleton tyrant = (WitherSkeleton) location.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);
        AttributeInstance maxHealth = tyrant.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(row.health());
        }
        tyrant.setHealth(row.health());
        AttributeInstance attack = tyrant.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(row.damage());
        }
        tyrant.customName(Component.text("The Ashen Tyrant", NamedTextColor.GRAY));
        tyrant.setCustomNameVisible(true);
        // A boss fight can draw players out of range mid-engagement; it must not despawn under them.
        tyrant.setPersistent(true);

        PersistentDataContainer pdc = tyrant.getPersistentDataContainer();
        pdc.set(guardianKey, PersistentDataType.STRING, def.id());
        pdc.set(tierKey, PersistentDataType.STRING, tier.name());

        BossBar bar = Bukkit.createBossBar("The Ashen Tyrant (" + tier.displayName() + ")",
                BarColor.RED, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player nearby : location.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) <= 64 * 64) {
                bar.addPlayer(nearby);
            }
        }
        bossBars.put(tyrant.getUniqueId(), bar);

        return Optional.of(tyrant);
    }

    /** True for a boss entity this class itself spawned - exposed so {@code TyrantDiscovery} can
     * tell an ordinary wild Wither Skeleton's death (its own rare-drop roll) apart from a real
     * Tyrant fight ending, since both are the same {@link EntityType#WITHER_SKELETON}. */
    public boolean isOurs(LivingEntity entity) {
        return GUARDIAN_ID.equals(entity.getPersistentDataContainer().get(guardianKey, PersistentDataType.STRING));
    }

    private Tier tierOf(LivingEntity entity) {
        String name = entity.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return name == null ? Tier.NORMAL : Tier.valueOf(name);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity boss && isOurs(boss)) {
            fighters.onDamage(event, boss);
            if (event instanceof EntityDamageByEntityEvent byEntity && boss instanceof Mob mob
                    && byEntity.getDamager() instanceof Player player) {
                // Vanilla targeting for a natural Wither Skeleton is unconditional, but forcing
                // aggro here matches the Sentinel's own convention and keeps both bosses consistent.
                mob.setTarget(player);
            }
            updateBossBar(boss);
            maybeTriggerBlazeSummon(boss, event.getFinalDamage());
            return;
        }
        // The other direction: the Tyrant landing a hit on a player gets a Fire-Aspect-style burn,
        // this dispatch's "minimal custom code" stand-in for an enchanted weapon.
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getEntity() instanceof Player victim
                && byEntity.getDamager() instanceof LivingEntity possibleBoss && isOurs(possibleBoss)) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), FIRE_TICKS));
        }
    }

    private void updateBossBar(LivingEntity boss) {
        BossBar bar = bossBars.get(boss.getUniqueId());
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (bar != null && max != null && max.getValue() > 0) {
            bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / max.getValue())));
        }
    }

    /** Fires every Normal-tier ability id the first time the Tyrant's projected health crosses
     * half its max - a PDC flag makes sure a long fight sitting at the threshold only triggers it
     * once. An ability id with no registered {@code GuardianAbility} (a Heroic/Mythic id this
     * dispatch does not implement) is skipped, never a crash - see {@code GuardianAbilityRegistry},
     * same no-crash precedent {@code sentinel.SentinelFight} already proved. */
    private void maybeTriggerBlazeSummon(LivingEntity boss, double incomingDamage) {
        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        if (pdc.has(blazeSummonTriggeredKey, PersistentDataType.BOOLEAN)) {
            return;
        }
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        double projectedHealth = boss.getHealth() - incomingDamage;
        if (projectedHealth > max.getValue() * 0.5) {
            return;
        }
        GuardianDef def = guardians.get(GUARDIAN_ID).orElse(null);
        if (def == null) {
            return;
        }
        GuardianTierRow row = def.tiers().get(tierOf(boss));
        for (String abilityId : row.abilityIds()) {
            abilities.get(abilityId).ifPresentOrElse(ability -> ability.trigger(boss),
                    () -> logger.fine("Ability '" + abilityId + "' for " + GUARDIAN_ID
                            + " is not registered yet; skipping it."));
        }
        pdc.set(blazeSummonTriggeredKey, PersistentDataType.BOOLEAN, true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (!isOurs(boss)) {
            return;
        }
        event.getDrops().clear();
        event.getDrops().addAll(loot.forTier(tierOf(boss)));

        BossBar bar = bossBars.remove(boss.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    /** Who hurt this boss this fight, for whatever reward/record code a later dispatch adds
     * (Hall of Fame, Guild contracts - explicitly not wired up by this dispatch). */
    public Set<UUID> fightersOf(LivingEntity boss) {
        return fighters.fighters(boss);
    }
}
