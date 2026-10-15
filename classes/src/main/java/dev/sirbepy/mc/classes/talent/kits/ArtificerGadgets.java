package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Artificer's Tinker branch: Field Repair, Last Chance, Grapple, Armour Kit, Shock Absorber,
 * Rocket Jump, Salvage, Flashbang, Power Cells, Thrusters and the Grav Gun keystone, plus Jump Pad
 * from the Engineer branch (it rides Overclock like Field Repair, so it lives here too).
 */
final class ArtificerGadgets implements Listener {

    private final KitContext ctx;
    private final Ability golem;
    private final Ability gravGun;
    private final Map<UUID, Long> lastChanceCooldown = new HashMap<>();
    /** Players immune to the next fall after a launch (Jump Pad, Rocket Jump), cleared after ~10 s. */
    private final Set<UUID> fallImmune = new HashSet<>();
    private final Map<UUID, JumpPad> jumpPads = new HashMap<>();

    private record JumpPad(Location at, long expiresAtMillis) {
    }

    ArtificerGadgets(KitContext ctx) {
        this.ctx = ctx;
        this.golem = SimpleAbility.of("Golem", 90, p -> "builds a small iron golem that fights mobs for 30 s", this::castGolem);
        this.gravGun = SimpleAbility.of("Grav Gun", 45,
                p -> "throws the mob you look at 10 blocks, 3 hearts on landing", this::castGravGun);
    }

    void register() {
        ctx.listen(this);
    }

    Ability golemAbility() {
        return golem;
    }

    Ability gravGunAbility() {
        return gravGun;
    }

    // --- Overclock add-ons: Field Repair, Armour Kit, Jump Pad, Rocket Jump, Flashbang, Thrusters --

    void onOverclock(Player player) {
        int fieldRepair = ctx.rank(player, "field_repair");
        if (fieldRepair > 0) repair(player.getInventory().getItemInMainHand(), fieldRepair * 0.02);
        int armourKit = ctx.rank(player, "armour_kit");
        if (armourKit > 0) {
            for (ItemStack piece : player.getInventory().getArmorContents()) repair(piece, armourKit * 0.02);
        }
        if (ctx.rank(player, "jump_pad") > 0) placeJumpPad(player);
        if (ctx.rank(player, "rocket_jump") > 0) rocketJump(player);
        int flashbang = ctx.rank(player, "flashbang");
        if (flashbang > 0) {
            double radius = flashbang == 1 ? 4 : 6;
            for (LivingEntity mob : player.getLocation().getNearbyEntitiesByType(LivingEntity.class, radius)) {
                if (Mobs.hostile(mob)) mob.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            }
        }
        if (ctx.rank(player, "thrusters") > 0 && player.isGliding()) {
            Vector boost = player.getLocation().getDirection().normalize().multiply(1.5).setY(0.5);
            player.setVelocity(player.getVelocity().add(boost));
            ctx.fx().burst(player, Particle.FLAME, 15, 0.3);
            ctx.fx().sound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);
        }
    }

    private void repair(ItemStack item, double percent) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasDamage()) return;
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;
        int reduce = (int) Math.round(maxDurability * percent);
        damageable.setDamage(Math.max(0, damageable.getDamage() - reduce));
        item.setItemMeta((ItemMeta) damageable);
    }

    private void placeJumpPad(Player player) {
        long expires = ctx.now() + 10_000;
        jumpPads.put(player.getUniqueId(), new JumpPad(player.getLocation(), expires));
        ctx.fx().ring(player.getLocation(), Particle.CLOUD, 1, 10);
        ctx.later(() -> {
            JumpPad pad = jumpPads.get(player.getUniqueId());
            if (pad != null && pad.expiresAtMillis() == expires) jumpPads.remove(player.getUniqueId());
        }, 200L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (jumpPads.isEmpty()) return;
        for (Map.Entry<UUID, JumpPad> e : jumpPads.entrySet()) {
            JumpPad pad = e.getValue();
            if (ctx.now() > pad.expiresAtMillis()) continue;
            Player owner = ctx.plugin().getServer().getPlayer(e.getKey());
            if (owner == null) continue;
            launchIfOnPad(event.getPlayer(), pad);
            for (Player ally : ctx.allies(owner, 6)) launchIfOnPad(ally, pad);
            launchIfOnPad(owner, pad);
        }
    }

    private void launchIfOnPad(Player player, JumpPad pad) {
        Location loc = player.getLocation();
        if (!loc.getWorld().equals(pad.at().getWorld())) return;
        if (loc.distanceSquared(pad.at()) > 1.5) return;
        player.setVelocity(player.getVelocity().setY(1.3));
        fallImmune.add(player.getUniqueId());
        ctx.later(() -> fallImmune.remove(player.getUniqueId()), 200L);
    }

    private void rocketJump(Player player) {
        Vector boost = player.getLocation().getDirection().normalize().multiply(1.2).setY(1.0);
        player.setVelocity(player.getVelocity().add(boost));
        fallImmune.add(player.getUniqueId());
        ctx.later(() -> fallImmune.remove(player.getUniqueId()), 200L);
        ctx.fx().burst(player, Particle.CLOUD, 20, 0.3);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.9f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        UUID id = player.getUniqueId();
        if (fallImmune.remove(id)) {
            event.setCancelled(true);
            return;
        }
        if (!ctx.active(player)) return;
        int shockAbsorber = ctx.rank(player, "shock_absorber");
        if (shockAbsorber > 0 && ArtificerMining.isTool(player.getInventory().getItemInMainHand().getType())) {
            double cut = shockAbsorber == 1 ? 0.25 : 0.5;
            event.setDamage(event.getDamage() * (1 - cut));
        }
    }

    // --- Last Chance / Salvage ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player)) return;
        int lastChance = ctx.rank(player, "last_chance");
        if (lastChance <= 0) return;
        ItemMeta meta = event.getItem().getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        int maxDurability = event.getItem().getType().getMaxDurability();
        if (damageable.getDamage() + event.getDamage() < maxDurability) return;
        UUID id = player.getUniqueId();
        long cooldownMillis = (lastChance == 1 ? 10 : 5) * 60_000L;
        long now = ctx.now();
        // Long.MIN_VALUE / 2, not Long.MIN_VALUE: "now - sentinel" would overflow and wrap negative,
        // which reads as "still on cooldown" for a player who has never saved a tool before.
        if (now - lastChanceCooldown.getOrDefault(id, Long.MIN_VALUE / 2) < cooldownMillis) return;
        lastChanceCooldown.put(id, now);
        event.setCancelled(true);
        damageable.setDamage(maxDurability - 1);
        event.getItem().setItemMeta((ItemMeta) damageable);
        ctx.fx().sound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.8f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player)) return;
        int salvage = ctx.rank(player, "salvage");
        if (salvage <= 0 || ctx.random() >= salvage * 0.10) return;
        Material material = salvageMaterialFor(event.getBrokenItem().getType());
        if (material != null) player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material));
    }

    private static Material salvageMaterialFor(Material tool) {
        String name = tool.name();
        if (name.startsWith("WOODEN_")) return Material.STICK;
        if (name.startsWith("STONE_")) return Material.COBBLESTONE;
        if (name.startsWith("IRON_") || name.equals("CHAINMAIL_HELMET")) return Material.IRON_INGOT;
        if (name.startsWith("GOLDEN_")) return Material.GOLD_INGOT;
        if (name.startsWith("DIAMOND_")) return Material.DIAMOND;
        if (name.startsWith("NETHERITE_")) return Material.NETHERITE_SCRAP;
        return null;
    }

    // --- Power Cells ---------------------------------------------------------------------------

    void registerPowerCells() {
        ctx.resources().addGainModifier(player -> {
            if (!ctx.active(player)) return 1.0;
            int rank = ctx.rank(player, "power_cells");
            return rank == 1 ? 1.25 : rank == 2 ? 1.5 : 1.0;
        });
    }

    // --- Grapple ---------------------------------------------------------------------------------

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player) || event.getState() != PlayerFishEvent.State.IN_GROUND) return;
        int rank = ctx.rank(player, "grapple");
        if (rank <= 0) return;
        FishHook hook = event.getHook();
        double maxRange = rank == 1 ? 20 : 30;
        if (hook.getLocation().distance(player.getLocation()) > maxRange) return;
        Vector toward = hook.getLocation().toVector().subtract(player.getLocation().toVector());
        player.setVelocity(toward.normalize().multiply(1.8));
        ctx.fx().line(player.getLocation(), hook.getLocation(), Particle.CRIT);
    }

    // --- Golem keystone --------------------------------------------------------------------------

    private boolean castGolem(Player player) {
        IronGolem entity = ctx.summons().spawn(player, player.getLocation(), IronGolem.class, 600, g -> {
            g.setPlayerCreated(true);
        });
        BukkitTask retarget = ctx.repeat(() -> {
            if (!entity.isValid()) return;
            LivingEntity nearest = entity.getLocation().getNearbyEntitiesByType(LivingEntity.class, 12).stream()
                    .filter(Mobs::hostile)
                    .min((a, b) -> Double.compare(a.getLocation().distanceSquared(entity.getLocation()),
                            b.getLocation().distanceSquared(entity.getLocation())))
                    .orElse(null);
            if (nearest != null) entity.setTarget(nearest);
        }, 20L, 20L);
        ctx.later(retarget::cancel, 600L);
        ctx.fx().burst(player, Particle.CRIT, 20, 0.5);
        ctx.fx().sound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.8f);
        return true;
    }

    // --- Grav Gun keystone -----------------------------------------------------------------------

    private boolean castGravGun(Player player) {
        LivingEntity target = lookedAtMob(player, 8);
        if (target == null) return false;
        Vector toss = player.getLocation().getDirection().normalize().multiply(2.2).setY(0.6);
        target.setVelocity(toss);
        ctx.fx().line(player.getEyeLocation(), target.getLocation(), Particle.CLOUD);
        watchUntilLanded(target, player, 0);
        return true;
    }

    /**
     * The nearest grabbable mob roughly in front of {@code player}, within {@code maxDistance}: a
     * manual cone search rather than {@code World#rayTraceEntities}, which MockBukkit doesn't
     * implement (see the report's Deviations).
     */
    private static LivingEntity lookedAtMob(Player player, double maxDistance) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (!isGrabbable(e)) continue;
            Vector toEntity = e.getLocation().toVector().subtract(player.getEyeLocation().toVector());
            double distance = toEntity.length();
            if (distance < 1e-6 || distance > maxDistance || toEntity.normalize().dot(dir) < 0.85) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    // Hostile mobs only: shop NPCs, mannequins and other plugins' villagers are LivingEntities too,
    // and throwing them would break their stalls.
    private static boolean isGrabbable(Entity e) {
        return dev.sirbepy.mc.classes.talent.Mobs.hostile(e) && !dev.sirbepy.mc.classes.talent.Mobs.boss(e);
    }

    private void watchUntilLanded(LivingEntity target, Player caster, int elapsedTicks) {
        if (elapsedTicks >= 60 || !target.isValid()) return;
        ctx.later(() -> {
            if (!target.isValid()) return;
            if (target.isOnGround()) {
                ctx.damage(target, 6, caster);
                ctx.fx().burst(target, Particle.CLOUD, 10, 0.3);
            } else {
                watchUntilLanded(target, caster, elapsedTicks + 5);
            }
        }, 5L);
    }

    void forget(UUID player) {
        lastChanceCooldown.remove(player);
        fallImmune.remove(player);
        jumpPads.remove(player);
    }
}
