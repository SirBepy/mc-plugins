package dev.sirbepy.mc.minibosses.sovereign;

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
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
 * Owns the Void Sovereign's spawn, fighter tracking, half-health void-step trigger, and death
 * loot - the fight-level wiring for Normal tier only, mirroring the shape
 * {@code sentinel.SentinelFight}/{@code tyrant.TyrantFight}'s own foundation (phase A) dispatches
 * established (see {@link VoidStepAbility} and {@link SovereignLoot} for why Heroic/Mythic content
 * is out of scope here). A single {@link Enderman} stands in for the whole boss: it is the only
 * vanilla mob that natively teleports, exactly the "teleportation + void abilities" brief the todo
 * gives this Guardian - a {@link org.bukkit.entity.Shulker} was the other candidate the todo names,
 * but has no native teleport or mobility at all (just a defensive shell and a levitation shot), a
 * worse fit for a boss whose entire identity is "it is never where you left it". This dispatch
 * does not suppress an Enderman's own vanilla teleport-on-hit/anger-on-stare behavior - the same
 * "do not fight the vanilla mob's own behavior" judgment {@code tyrant.TyrantFight} applied to a
 * Wither Skeleton's natural Wither effect, both read as "the boss is doing boss things", not a bug.
 *
 * <p>Regression note (this Guardian's own sensitivity, being End-dimension like the Ender Dragon
 * fight): this fight's state (PDC tags, via the
 * generalized {@code core.dragon.DragonFighters} - the same mechanism the other two Guardians use,
 * never anything {@code endfight}-specific) is fully independent of {@code endfight}'s own
 * End-dimension Ender Dragon fight/arena ({@code endfight.arena.ArenaRegistry}, its own in-memory
 * block-position/guard-post tracker rebuilt from its own {@code ArenaLayout}). Nothing in this
 * dispatch references {@code endfight.arena} or any other {@code endfight} class.
 */
public final class SovereignFight implements Listener {

    public static final String GUARDIAN_ID = "void_sovereign";

    private final GuardianRegistry guardians;
    private final GuardianAbilityRegistry abilities;
    private final DragonFighters fighters;
    private final SovereignLoot loot;
    private final Logger logger;

    private final NamespacedKey guardianKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey voidStepTriggeredKey;

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public SovereignFight(JavaPlugin plugin, GuardianRegistry guardians, GuardianAbilityRegistry abilities,
                           DragonFighters fighters, SovereignLoot loot, Logger logger) {
        this.guardians = guardians;
        this.abilities = abilities;
        this.fighters = fighters;
        this.loot = loot;
        this.logger = logger;
        this.guardianKey = new NamespacedKey(plugin, "guardian");
        this.tierKey = new NamespacedKey(plugin, "guardian_tier");
        this.voidStepTriggeredKey = new NamespacedKey(plugin, "void_step_triggered");
    }

    /** Spawns a fresh Void Sovereign at {@code location} fought at {@code tier}; empty (and
     * logged) if the Guardian YAML has not loaded - should never happen once the file ships, but a
     * missing/malformed file must fail safe rather than NPE a summon attempt. */
    public Optional<LivingEntity> spawn(Location location, Tier tier) {
        GuardianDef def = guardians.get(GUARDIAN_ID).orElse(null);
        if (def == null) {
            logger.warning("Cannot spawn " + GUARDIAN_ID + ": its Guardian definition is not loaded.");
            return Optional.empty();
        }
        GuardianTierRow row = def.tiers().get(tier);

        Enderman sovereign = (Enderman) location.getWorld().spawnEntity(location, EntityType.ENDERMAN);
        AttributeInstance maxHealth = sovereign.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(row.health());
        }
        sovereign.setHealth(row.health());
        AttributeInstance attack = sovereign.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(row.damage());
        }
        sovereign.customName(Component.text("The Void Sovereign", NamedTextColor.DARK_PURPLE));
        sovereign.setCustomNameVisible(true);
        // A boss fight can draw players out of range mid-engagement; it must not despawn under them.
        sovereign.setPersistent(true);

        PersistentDataContainer pdc = sovereign.getPersistentDataContainer();
        pdc.set(guardianKey, PersistentDataType.STRING, def.id());
        pdc.set(tierKey, PersistentDataType.STRING, tier.name());

        BossBar bar = Bukkit.createBossBar("The Void Sovereign (" + tier.displayName() + ")",
                BarColor.PURPLE, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player nearby : location.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) <= 64 * 64) {
                bar.addPlayer(nearby);
            }
        }
        bossBars.put(sovereign.getUniqueId(), bar);

        return Optional.of(sovereign);
    }

    /** True for a boss entity this class itself spawned - exposed so {@code SovereignDiscovery}
     * can tell an ordinary wild Enderman's death (its own rare-drop roll) apart from a real
     * Sovereign fight ending, since both are the same {@link EntityType#ENDERMAN}. */
    public boolean isOurs(LivingEntity entity) {
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
            // Vanilla Enderman targeting only engages when a player stares at it; force aggro onto
            // whoever hits it instead, the same convention sentinel.SentinelFight/tyrant.TyrantFight
            // already established for their own mobs.
            mob.setTarget(player);
        }
        updateBossBar(boss);
        maybeTriggerVoidStep(boss, event.getFinalDamage());
    }

    private void updateBossBar(LivingEntity boss) {
        BossBar bar = bossBars.get(boss.getUniqueId());
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (bar != null && max != null && max.getValue() > 0) {
            bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / max.getValue())));
        }
    }

    /** Fires every Normal-tier ability id the first time the Sovereign's projected health crosses
     * half its max - a PDC flag makes sure a long fight sitting at the threshold only triggers it
     * once. An ability id with no registered {@code GuardianAbility} (a Heroic/Mythic id this
     * dispatch does not implement) is skipped, never a crash - see {@code GuardianAbilityRegistry},
     * same no-crash precedent the other two Guardians already proved. */
    private void maybeTriggerVoidStep(LivingEntity boss, double incomingDamage) {
        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        if (pdc.has(voidStepTriggeredKey, PersistentDataType.BOOLEAN)) {
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
        pdc.set(voidStepTriggeredKey, PersistentDataType.BOOLEAN, true);
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
