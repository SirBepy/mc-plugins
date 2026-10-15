package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Every listener the Guardian kit needs: damage taken and dealt (shield blocking, Fortress, ally
 * protections, Grit, Momentum...), Last Stand's extras, knockback cancelling and Plated's periodic
 * armour check. Registered once as a single {@link Listener} in {@link GuardianKit#enable}.
 */
final class GuardianListeners implements Listener {

    /** Same key {@code ClassPassives} uses for Heavy Plate; reconstructed since that field is
     * package-private in a different package. {@link NamespacedKey} compares by value, not identity. */
    private static final NamespacedKey HEAVY_PLATE_KEY = new NamespacedKey("classes", "heavy_plate");

    private final KitContext ctx;
    private final GuardianState state;

    GuardianListeners(KitContext ctx, GuardianState state) {
        this.ctx = ctx;
        this.state = state;
        ctx.repeat(this::checkPlated, 20, 20);
        ctx.repeat(this::checkKnockbackResistance, 20, 20);
    }

    // Ability 1 (Last Stand): Iron Will, Shared Stand. Unbreakable and Stone Skin read the window
    // this sets, through isLastStandActive.
    @EventHandler
    public void onAbility(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.GUARDIAN || event.slot() != Slot.ONE || event.keystone()) return;
        Player player = event.player();
        if (!ctx.active(player)) return;
        int level = ctx.level(player);
        int seconds = RpgClass.GUARDIAN.abilitySeconds(level);
        long until = ctx.now() + seconds * 1000L;
        state.lastStandUntil.put(player.getUniqueId(), until);

        int ironWill = ctx.rank(player, "iron_will");
        if (ironWill > 0) {
            player.setAbsorptionAmount(player.getAbsorptionAmount() + GuardianGeometry.hearts(ironWill));
            ctx.fx().burst(player, Particle.ENCHANT, 12, 0.4);
        }

        int sharedStandRank = ctx.rank(player, "shared_stand");
        if (sharedStandRank > 0) {
            double radius = sharedStandRank * 2;
            double percent = RpgClass.lastStandPercent(level) / 100 / 2;
            for (Player ally : ctx.allies(player, radius)) {
                state.sharedStand.put(ally.getUniqueId(), new GuardianState.SharedStand(percent, until));
            }
        }
    }

    // Damage taken: shield blocking, Fortress, Rooted, ally protections, Lifeline, Guardian Spirit.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress() || !(event.getEntity() instanceof LivingEntity)) return;
        Entity rawSource = event.getDamager();
        Entity source = rawSource instanceof Projectile proj && proj.getShooter() instanceof Entity shooter ? shooter : rawSource;

        if (event.getEntity() instanceof Player victim) {
            applyDamageTakenByPlayer(event, victim, rawSource, source);
        }
        if (source instanceof Player attacker && event.getEntity() instanceof LivingEntity mobVictim && Mobs.hostile(mobVictim)) {
            applyDamageDealtByPlayer(event, attacker, mobVictim);
        }
    }

    private void applyDamageTakenByPlayer(EntityDamageByEntityEvent event, Player victim, Entity rawSource, Entity source) {
        boolean fromMob = source instanceof LivingEntity && Mobs.hostile(source);
        boolean fromPlayer = source instanceof Player && source != victim;
        if (!fromMob && !fromPlayer) return;
        boolean ranged = rawSource instanceof Projectile;
        boolean victimIsGuardian = ctx.active(victim);
        double reduction = 0;

        if (victimIsGuardian) {
            if (fromMob) reduction += mobDefenseForGuardian(event, victim, source, rawSource.getLocation(), ranged);
            reduction += fortressReductionFor(state.fortressUntil, victim, fromPlayer);
        } else {
            reduction += fortressReductionFor(state.fortressAllyUntil, victim, fromPlayer);
            GuardianState.SharedStand shared = state.sharedStand.get(victim.getUniqueId());
            if (shared != null && shared.until() > ctx.now()) reduction += shared.percent();
            if (fromMob) applyAllyProtections(event, victim, source);
        }
        if (reduction > 0) event.setDamage(Math.max(0, event.getDamage() * (1 - Math.min(reduction, 1))));
    }

    private double mobDefenseForGuardian(EntityDamageByEntityEvent event, Player guardian, Entity source, Location attackerLoc, boolean ranged) {
        UUID id = guardian.getUniqueId();
        if (ctx.rank(guardian, "grit") > 0) {
            GuardianState.pushStack(state.gritStacks, id, ctx.now() + 5000, 5);
        }
        boolean blocked = GuardianGeometry.isBlocked(guardian, attackerLoc);
        if (blocked) {
            ctx.gain(guardian, 3);
            if (!ranged) {
                int mastery = ctx.rank(guardian, "shield_mastery");
                if (mastery > 0 && source instanceof LivingEntity living) {
                    ctx.damage(living, event.getDamage() * mastery * 0.10, guardian);
                }
                int thorned = ctx.rank(guardian, "thorned_shield");
                if (thorned > 0 && source instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, thorned * 20, 0));
                }
                GuardianState.pushStack(state.blockedHits, id, ctx.now() + 5000, 10);
                if (GuardianState.countActive(state.blockedHits, id, ctx.now()) >= 3 && ctx.rank(guardian, "retaliation") > 0) {
                    state.retaliationReadyUntil.put(id, ctx.now() + 5000);
                }
            } else {
                int aegis = ctx.rank(guardian, "aegis");
                if (aegis > 0 && source instanceof LivingEntity shooter) {
                    ctx.damage(shooter, event.getDamage() * (aegis == 1 ? 0.5 : 1.0), guardian);
                }
            }
        } else {
            ctx.gain(guardian, 4);
        }
        int rootedRank = ctx.rank(guardian, "rooted");
        if (rootedRank > 0 && GuardianGeometry.isStandingStill(guardian)) {
            return rootedRank == 1 ? 0.05 : 0.10;
        }
        return 0;
    }

    private double fortressReductionFor(Map<UUID, Long> window, Player player, boolean fromPlayer) {
        Long until = window.get(player.getUniqueId());
        return until != null && until > ctx.now() ? GuardianGeometry.fortressReduction(fromPlayer) : 0;
    }

    /** Guardian Angel, Bodyguard, Shared Resolve, Oath and the near-death saves, all gated through
     * {@link KitContext#allies}, which already drops a player the Guardian has fought in PvP recently. */
    private void applyAllyProtections(EntityDamageByEntityEvent event, Player ally, Entity source) {
        for (Entity e : ally.getLocation().getWorld().getNearbyEntities(ally.getLocation(), 8, 8, 8)) {
            if (!(e instanceof Player guardian) || guardian == ally || !ctx.active(guardian)) continue;
            if (!ctx.allies(guardian, 8).contains(ally)) continue;
            double distance = guardian.getLocation().distance(ally.getLocation());

            int gaRank = ctx.rank(guardian, "guardian_angel");
            if (gaRank > 0 && distance <= 4) {
                double redirect = event.getDamage() * gaRank * 0.05;
                event.setDamage(Math.max(0, event.getDamage() - redirect));
                redirectToGuardian(guardian, redirect);
            }
            int bgRank = ctx.rank(guardian, "bodyguard");
            if (bgRank > 0 && source instanceof Mob mob && !Mobs.boss(mob) && ctx.random() < (bgRank == 1 ? 0.10 : 0.20)) {
                mob.setTarget(guardian);
            }
            int srRank = ctx.rank(guardian, "shared_resolve");
            if (srRank > 0) ctx.gain(guardian, srRank == 1 ? 3 : 6);
        }

        Player oathGuardian = findOathGuardianFor(ally.getUniqueId());
        if (oathGuardian != null) {
            double redirect = event.getDamage() * 0.5;
            event.setDamage(Math.max(0, event.getDamage() - redirect));
            redirectToGuardian(oathGuardian, redirect);
        }

        applyLifelineAndGuardianSpirit(event, ally);
    }

    private void applyLifelineAndGuardianSpirit(EntityDamageByEntityEvent event, Player ally) {
        double resultingHealth = ally.getHealth() - event.getDamage();
        for (Entity e : ally.getLocation().getWorld().getNearbyEntities(ally.getLocation(), 8, 8, 8)) {
            if (!(e instanceof Player guardian) || guardian == ally || !ctx.active(guardian)) continue;
            if (!ctx.allies(guardian, 8).contains(ally)) continue;
            UUID gid = guardian.getUniqueId();

            if (resultingHealth <= 0 && ctx.rank(guardian, "guardian_spirit") > 0
                    && ctx.now() >= state.guardianSpiritReadyAt.getOrDefault(gid, 0L)) {
                event.setDamage(0);
                ally.setHealth(2);
                state.guardianSpiritReadyAt.put(gid, ctx.now() + 600_000L);
                double cost = Math.min(8, Math.max(0, guardian.getHealth() - 2));
                redirectToGuardian(guardian, cost);
                ctx.fx().burst(ally, Particle.TOTEM_OF_UNDYING, 20, 0.4);
                return;
            }
            if (resultingHealth > 0 && resultingHealth < 6 && ctx.rank(guardian, "lifeline") > 0
                    && ctx.now() >= state.lifelineReadyAt.getOrDefault(gid, 0L)) {
                int rank = ctx.rank(guardian, "lifeline");
                double heal = GuardianGeometry.hearts(rank == 1 ? 2 : 3);
                ally.setHealth(Math.min(ally.getHealth() + heal, maxHealth(ally)));
                state.lifelineReadyAt.put(gid, ctx.now() + 90_000L);
                ctx.fx().burst(ally, Particle.HAPPY_VILLAGER, 10, 0.4);
            }
        }
    }

    private Player findOathGuardianFor(UUID allyId) {
        long now = ctx.now();
        for (Map.Entry<UUID, GuardianState.OathLink> entry : state.oathLink.entrySet()) {
            if (entry.getValue().until() > now && entry.getValue().ally().equals(allyId)) {
                Player guardian = Bukkit.getPlayer(entry.getKey());
                if (guardian != null && guardian.isOnline()) return guardian;
            }
        }
        return null;
    }

    /** Applies redirected damage with a plain hit, guarded so it can never recurse into itself. */
    private void redirectToGuardian(Player guardian, double amount) {
        if (amount <= 0 || state.redirecting) return;
        state.redirecting = true;
        try {
            guardian.damage(amount);
        } finally {
            state.redirecting = false;
        }
    }

    // Damage dealt: Grit, Iron Fist, Momentum, Retaliation's stored bonus.
    private void applyDamageDealtByPlayer(EntityDamageByEntityEvent event, Player attacker, LivingEntity mobVictim) {
        if (!ctx.active(attacker)) return;
        boolean melee = event.getCause() == DamageCause.ENTITY_ATTACK || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK;
        if (!melee) return;
        UUID id = attacker.getUniqueId();

        int gritRank = ctx.rank(attacker, "grit");
        if (gritRank > 0) {
            int stacks = GuardianState.countActive(state.gritStacks, id, ctx.now());
            double percent = GuardianGeometry.gritPercent(gritRank, stacks);
            if (percent > 0) event.setDamage(event.getDamage() * (1 + percent / 100));
        }

        int ironFistRank = ctx.rank(attacker, "iron_fist");
        if (ironFistRank > 0 && attacker.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
            mobVictim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ironFistRank * 20, 0));
        }

        int momentumRank = ctx.rank(attacker, "momentum");
        if (momentumRank > 0 && attacker.isSprinting() && ctx.now() >= state.momentumReadyAt.getOrDefault(id, 0L)) {
            double extra = GuardianGeometry.hearts(momentumRank == 1 ? 1 : momentumRank == 2 ? 1.5 : 2);
            ctx.damage(mobVictim, extra, attacker);
            Vector push = mobVictim.getLocation().toVector().subtract(attacker.getLocation().toVector());
            push.setY(0.2);
            if (push.lengthSquared() > 1e-6) mobVictim.setVelocity(push.normalize().multiply(1.2));
            state.momentumReadyAt.put(id, ctx.now() + 3000L);
        }

        Long retaliationUntil = state.retaliationReadyUntil.get(id);
        if (retaliationUntil != null && retaliationUntil > ctx.now()) {
            int rank = ctx.rank(attacker, "retaliation");
            if (rank > 0) {
                event.setDamage(event.getDamage() * (1 + (rank == 1 ? 0.20 : 0.40)));
                state.retaliationReadyUntil.remove(id);
            }
        }
    }

    // Steady, Stone Skin, Immovable, Stalwart Aura, Fortress's "no knockback": the only Bukkit event
    // for cancelling knockback (EntityKnockbackByEntityEvent) is deprecated for removal, and in this
    // Paper build registering a listener for it throws inside Paper's own deprecation-warning path
    // (NPE on PluginDescriptionFile#getAuthors() with MockBukkit's plugin). KNOCKBACK_RESISTANCE is
    // the non-event route Minecraft itself uses for the same effect, checked on the same cadence as
    // Plated. It resists knockback from any source, not only mobs, which is broader than the spec's
    // wording but keeps the feature working.
    static final NamespacedKey KNOCKBACK_RESIST_KEY = new NamespacedKey("classes", "guardian_knockback_resist");

    private void checkKnockbackResistance() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            AttributeInstance kb = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb == null) continue;
            double resist = desiredKnockbackResistance(player);
            AttributeModifier existing = kb.getModifier(KNOCKBACK_RESIST_KEY);
            if (existing != null && existing.getAmount() == resist) continue;
            if (existing != null) kb.removeModifier(KNOCKBACK_RESIST_KEY);
            if (resist > 0) {
                kb.addTransientModifier(new AttributeModifier(KNOCKBACK_RESIST_KEY, resist, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
    }

    /** Package-visible so the rule for each case can be unit tested without the attribute plumbing. */
    double desiredKnockbackResistance(Player player) {
        if (state.fortressUntil.getOrDefault(player.getUniqueId(), 0L) > ctx.now()) return 1.0;
        if (state.fortressAllyUntil.getOrDefault(player.getUniqueId(), 0L) > ctx.now()) return 1.0;
        if (ctx.active(player)) {
            if (player.isBlocking() && ctx.rank(player, "stone_skin") > 0 && isLastStandActive(player)) return 1.0;
            if (player.isSneaking() && ctx.rank(player, "immovable") > 0) return 1.0;
            if (player.isBlocking() && ctx.rank(player, "steady") > 0) {
                return ctx.rank(player, "steady") == 1 ? 0.25 : 0.5;
            }
            return 0;
        }
        double best = 0;
        for (Entity e : player.getLocation().getWorld().getNearbyEntities(player.getLocation(), 6, 6, 6)) {
            if (e instanceof Player guardian && ctx.active(guardian) && ctx.allies(guardian, 6).contains(player)) {
                int rank = ctx.rank(guardian, "stalwart_aura");
                if (rank > 0) best = Math.max(best, rank == 1 ? 0.25 : 0.5);
            }
        }
        return best;
    }

    boolean isLastStandActive(Player player) {
        Long until = state.lastStandUntil.get(player.getUniqueId());
        return until != null && until > ctx.now();
    }

    // Unbreakable: Last Stand's blocked hits don't wear the shield.
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player) || event.getItem().getType() != Material.SHIELD) return;
        if (ctx.rank(player, "unbreakable") > 0 && isLastStandActive(player)) {
            event.setCancelled(true);
        }
    }

    // Earthshaker: landing from 4+ blocks; also where the kit's own fall-damage immunity (Leap
    // Smash, Shoulder Charge, Intervene) is spent.
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.FALL || !(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        if (consumeFallImmunity(player)) {
            event.setCancelled(true);
            return;
        }
        if (ctx.rank(player, "earthshaker") > 0 && player.getFallDistance() >= 4) {
            for (Entity e : player.getLocation().getWorld().getNearbyEntities(player.getLocation(), 3, 3, 3)) {
                if (e instanceof LivingEntity target && Mobs.hostile(target)) {
                    ctx.damage(target, GuardianGeometry.hearts(2), player);
                }
            }
            ctx.fx().ring(player.getLocation(), Particle.BLOCK_CRUMBLE, 1, 16);
        }
    }

    private boolean consumeFallImmunity(Player player) {
        UUID id = player.getUniqueId();
        Long until = state.fallImmuneUntil.get(id);
        if (until != null && until > ctx.now()) {
            state.fallImmuneUntil.remove(id);
            return true;
        }
        return false;
    }

    // Plated: halves or removes Heavy Plate's slowness while wearing full iron armour or better.
    // Armour changes have no single Bukkit event that covers every path, so this is a cheap
    // per-active-Guardian check (docs/classes-revamp.md's load note allows up to every 5 ticks; 20 is plenty).
    private void checkPlated() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ctx.active(player)) continue;
            AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed == null) continue;
            int rank = ctx.rank(player, "plated");
            boolean reduceOrRemove = rank > 0 && wearsFullIronOrBetter(player);
            double wanted = reduceOrRemove
                    ? (rank == 1 ? -RpgClass.HEAVY_PLATE_SPEED / 200 : 0)
                    : -RpgClass.HEAVY_PLATE_SPEED / 100;
            AttributeModifier existing = speed.getModifier(HEAVY_PLATE_KEY);
            if (existing != null && existing.getAmount() == wanted) continue;
            if (existing != null) speed.removeModifier(HEAVY_PLATE_KEY);
            if (wanted != 0) {
                speed.addTransientModifier(new AttributeModifier(HEAVY_PLATE_KEY, wanted, AttributeModifier.Operation.ADD_SCALAR));
            }
        }
    }

    static boolean wearsFullIronOrBetter(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length < 4) return false;
        for (ItemStack piece : armor) {
            if (piece == null || !isIronOrBetter(piece.getType())) return false;
        }
        return true;
    }

    private static boolean isIronOrBetter(Material type) {
        String name = type.name();
        return name.startsWith("IRON_") || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_");
    }

    static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    /** Unyielding's gain modifier: 2x/3x Resolve under 30% health. Wired in {@link GuardianKit#enable}. */
    static double unyieldingMultiplier(KitContext ctx, Player player) {
        if (!ctx.active(player)) return 1.0;
        int rank = ctx.rank(player, "unyielding");
        if (rank == 0) return 1.0;
        if (player.getHealth() >= maxHealth(player) * 0.30) return 1.0;
        return rank == 1 ? 2.0 : 3.0;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        state.forget(event.getPlayer().getUniqueId());
    }
}
