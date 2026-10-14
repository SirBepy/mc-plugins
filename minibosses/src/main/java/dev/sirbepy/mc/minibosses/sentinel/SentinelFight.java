package dev.sirbepy.mc.minibosses.sentinel;

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
import org.bukkit.entity.Ravager;
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
 * Owns the Overgrown Sentinel's spawn, fighter tracking, half-health defensive trigger and death
 * loot - the fight-level wiring for Normal tier only (see {@link RootAbility} and
 * {@link SentinelLoot} for why Heroic/Mythic content is out of scope here). A single
 * {@link Ravager} stands in for the whole boss (no {@code ComplexEntityPart}s needed): chosen
 * over an {@link org.bukkit.entity.IronGolem} because this repo's own
 * {@code core.dragon.DragonFightersTest} already proves a Ravager works as the generalized
 * single-part-boss stand-in, and its native charge/stomp kit reads as "ground attacks" with no
 * extra AI code, where an Iron Golem's single punch and reputation-gated hostility toward players
 * would need more custom AI work than this foundation dispatch is scoped for.
 */
public final class SentinelFight implements Listener {

    public static final String GUARDIAN_ID = "overgrown_sentinel";

    private final GuardianRegistry guardians;
    private final GuardianAbilityRegistry abilities;
    private final DragonFighters fighters;
    private final SentinelLoot loot;
    private final Logger logger;

    private final NamespacedKey guardianKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey rootTriggeredKey;

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public SentinelFight(JavaPlugin plugin, GuardianRegistry guardians, GuardianAbilityRegistry abilities,
                          DragonFighters fighters, SentinelLoot loot, Logger logger) {
        this.guardians = guardians;
        this.abilities = abilities;
        this.fighters = fighters;
        this.loot = loot;
        this.logger = logger;
        this.guardianKey = new NamespacedKey(plugin, "guardian");
        this.tierKey = new NamespacedKey(plugin, "guardian_tier");
        this.rootTriggeredKey = new NamespacedKey(plugin, "root_triggered");
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

        Ravager sentinel = (Ravager) location.getWorld().spawnEntity(location, EntityType.RAVAGER);
        AttributeInstance maxHealth = sentinel.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(row.health());
        }
        sentinel.setHealth(row.health());
        AttributeInstance attack = sentinel.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(row.damage());
        }
        sentinel.customName(Component.text("The Overgrown Sentinel", NamedTextColor.DARK_GREEN));
        sentinel.setCustomNameVisible(true);
        // A boss fight can draw players out of range mid-engagement; it must not despawn under them.
        sentinel.setPersistent(true);

        PersistentDataContainer pdc = sentinel.getPersistentDataContainer();
        pdc.set(guardianKey, PersistentDataType.STRING, def.id());
        pdc.set(tierKey, PersistentDataType.STRING, tier.name());

        BossBar bar = Bukkit.createBossBar("The Overgrown Sentinel (" + tier.displayName() + ")",
                BarColor.GREEN, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player nearby : location.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) <= 64 * 64) {
                bar.addPlayer(nearby);
            }
        }
        bossBars.put(sentinel.getUniqueId(), bar);

        return Optional.of(sentinel);
    }

    private boolean isOurs(LivingEntity entity) {
        return GUARDIAN_ID.equals(entity.getPersistentDataContainer().get(guardianKey, PersistentDataType.STRING));
    }

    private Tier tierOf(LivingEntity entity) {
        String name = entity.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return name == null ? Tier.NORMAL : Tier.valueOf(name);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss) || !isOurs(boss)) {
            return;
        }
        fighters.onDamage(event, boss);
        if (event instanceof EntityDamageByEntityEvent byEntity && boss instanceof Mob mob
                && byEntity.getDamager() instanceof Player player) {
            // Vanilla targeting for a natural Ravager is raid-only; force aggro onto whoever hits it
            // so a solo or group fight reliably engages without needing a raid context.
            mob.setTarget(player);
        }
        updateBossBar(boss);
        maybeTriggerRoot(boss, event.getFinalDamage());
    }

    private void updateBossBar(LivingEntity boss) {
        BossBar bar = bossBars.get(boss.getUniqueId());
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (bar != null && max != null && max.getValue() > 0) {
            bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / max.getValue())));
        }
    }

    /** Fires every Normal-tier ability id the first time the Sentinel's projected health crosses
     * half its max - a PDC flag makes sure a long fight sitting at the threshold only triggers it
     * once. An ability id with no registered {@code GuardianAbility} (a Heroic/Mythic id this
     * dispatch does not implement) is skipped, never a crash - see {@code GuardianAbilityRegistry}. */
    private void maybeTriggerRoot(LivingEntity boss, double incomingDamage) {
        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        if (pdc.has(rootTriggeredKey, PersistentDataType.BOOLEAN)) {
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
        pdc.set(rootTriggeredKey, PersistentDataType.BOOLEAN, true);
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
