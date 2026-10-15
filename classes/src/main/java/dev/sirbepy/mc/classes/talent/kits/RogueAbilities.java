package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Smoke Bomb, Death Mark and the three keystones (docs/classes-revamp.md, Rogue): everything built
 * as its own {@link Ability} rather than riding a melee or kill event. {@link RogueListener} calls
 * back into {@link #resolveDeathMarkFromHit} when a Death Mark target is hit before its 4 s timer.
 */
final class RogueAbilities {

    static final double HEART = 2.0;
    private static final NamedAttribute SPEED_BOOST = new NamedAttribute("rogue_shadow_form_speed");

    private final KitContext ctx;
    private final RogueState state;
    private final Ability smokeBomb;
    private final Ability deathMark;
    private final Ability phantomBlades;
    private final Ability shadowForm;
    private final Ability viperLunge;

    RogueAbilities(KitContext ctx, RogueState state) {
        this.ctx = ctx;
        this.state = state;
        this.smokeBomb = SimpleAbility.of("Smoke Bomb", 60,
                p -> "Invisibility for 4 s; mobs within " + smokeScreenRadius(p) + " blocks lose their target and get Blindness for 3 s",
                this::castSmokeBomb);
        this.deathMark = SimpleAbility.ultimate("Death Mark",
                p -> "mark the mob you look at; after 4 s or your next hit it takes 30% of its max health (5% on bosses, capped at 20 hearts)",
                this::castDeathMark);
        this.phantomBlades = SimpleAbility.of("Phantom Blades", 60,
                p -> "teleport between up to 4 mobs within 8 blocks, hitting each with your weapon",
                this::castPhantomBlades);
        this.shadowForm = SimpleAbility.of("Shadow Form", 60,
                p -> "for 4 s mobs can't target you and you move 30% faster; your first hit ends it with +50% damage",
                this::castShadowForm);
        this.viperLunge = SimpleAbility.of("Viper Lunge", 45,
                p -> "dash to the mob you look at, Poison II and Weakness I for 6 s",
                this::castViperLunge);
    }

    Ability smokeBomb() {
        return smokeBomb;
    }

    Ability deathMark() {
        return deathMark;
    }

    Ability phantomBlades() {
        return phantomBlades;
    }

    Ability shadowForm() {
        return shadowForm;
    }

    Ability viperLunge() {
        return viperLunge;
    }

    // --- Smoke Bomb --------------------------------------------------------------------------------

    private int smokeScreenRadius(Player player) {
        int rank = ctx.rank(player, "smoke_screen");
        return 6 + 2 * rank;
    }

    private boolean castSmokeBomb(Player player) {
        grantInvisibility(player, 80);
        int radius = smokeScreenRadius(player);
        UUID id = player.getUniqueId();
        long now = ctx.now();
        state.noRetargetUntil.put(id, now + 4_000);
        ctx.fx().burst(player, Particle.LARGE_SMOKE, 25, 0.6);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 0.6f);

        for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(radius)) {
            if (!Mobs.hostile(mob)) continue;
            if (mob instanceof Mob m) m.setTarget(null);
            mob.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
        }

        int decoyRank = ctx.rank(player, "decoy");
        if (decoyRank > 0) spawnDecoy(player, decoyRank == 1 ? 3 : 6);

        int toxicCloudRank = ctx.rank(player, "toxic_cloud");
        if (toxicCloudRank > 0) tickToxicCloud(player, radius, toxicCloudRank, 0);

        int ghostRank = ctx.rank(player, "ghost");
        if (ghostRank > 0) hideEquipment(player, 80);

        return true;
    }

    private void tickToxicCloud(Player player, int radius, int rank, int secondsDone) {
        if (secondsDone >= 4 || !player.isOnline()) return;
        for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(radius)) {
            RogueVenom.apply(ctx, player, mob, rank * 20L);
        }
        ctx.later(() -> tickToxicCloud(player, radius, rank, secondsDone + 1), 20);
    }

    private void spawnDecoy(Player player, int seconds) {
        var decoy = ctx.summons().spawn(player, player.getLocation(), org.bukkit.entity.ArmorStand.class, seconds * 20L, stand -> {
            stand.setInvisible(false);
            stand.getEquipment().setHelmet(player.getInventory().getHelmet());
            stand.getEquipment().setChestplate(player.getInventory().getChestplate());
            stand.getEquipment().setLeggings(player.getInventory().getLeggings());
            stand.getEquipment().setBoots(player.getInventory().getBoots());
        });
        for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(10)) {
            if (Mobs.hostile(mob) && mob instanceof Mob m) m.setTarget(decoy);
        }
    }

    private void hideEquipment(Player player, long ticks) {
        UUID id = player.getUniqueId();
        state.ghostUntil.put(id, ctx.now() + ticks * 50);
        Map<EquipmentSlot, ItemStack> hidden = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) hidden.put(slot, new ItemStack(org.bukkit.Material.AIR));
        List<Player> viewers = player.getLocation().getNearbyPlayers(48).stream().toList();
        for (Player viewer : viewers) viewer.sendEquipmentChange(player, hidden);
        ctx.later(() -> {
            if (!player.isOnline()) return;
            Map<EquipmentSlot, ItemStack> real = realEquipment(player);
            for (Player viewer : player.getLocation().getNearbyPlayers(48)) viewer.sendEquipmentChange(player, real);
        }, ticks);
    }

    private static Map<EquipmentSlot, ItemStack> realEquipment(Player player) {
        var inv = player.getInventory();
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        real.put(EquipmentSlot.HAND, orAir(inv.getItemInMainHand()));
        real.put(EquipmentSlot.OFF_HAND, orAir(inv.getItemInOffHand()));
        real.put(EquipmentSlot.HEAD, orAir(inv.getHelmet()));
        real.put(EquipmentSlot.CHEST, orAir(inv.getChestplate()));
        real.put(EquipmentSlot.LEGS, orAir(inv.getLeggings()));
        real.put(EquipmentSlot.FEET, orAir(inv.getBoots()));
        return real;
    }

    /** An empty armour/hand slot is null; {@code sendEquipmentChange} requires a real ItemStack. */
    private static ItemStack orAir(ItemStack item) {
        return item == null ? new ItemStack(org.bukkit.Material.AIR) : item;
    }

    /** Arms Ambush's one-shot window and grants real Invisibility, shared by Smoke Bomb and Vanishing Act. */
    void grantInvisibility(Player player, long ticks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int) ticks, 0));
        UUID id = player.getUniqueId();
        if (ctx.rank(player, "ambush") > 0) {
            state.ambushReady.add(id);
            ctx.later(() -> state.ambushReady.remove(id), ticks + 40);
        }
    }

    // --- Death Mark ----------------------------------------------------------------------------------

    private boolean castDeathMark(Player player) {
        LivingEntity target = RogueTargeting.lookedAtMob(player, 20);
        if (target == null) return false;
        UUID id = player.getUniqueId();
        state.deathMarkTarget.put(id, target);
        ctx.fx().burst(target, Particle.SOUL, 15, 0.4);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.2f);
        BukkitTask task = ctx.later(() -> resolveDeathMark(player, target), 80);
        state.deathMarkTask.put(id, task);
        return true;
    }

    /** Called from {@link RogueListener} when the player's own hit lands on the marked mob first. */
    void resolveDeathMarkFromHit(Player player, LivingEntity victim) {
        UUID id = player.getUniqueId();
        LivingEntity marked = state.deathMarkTarget.get(id);
        if (marked == null || marked != victim) return;
        BukkitTask task = state.deathMarkTask.remove(id);
        if (task != null) task.cancel();
        state.deathMarkTarget.remove(id);
        detonate(player, marked);
    }

    private void resolveDeathMark(Player player, LivingEntity target) {
        UUID id = player.getUniqueId();
        state.deathMarkTarget.remove(id);
        state.deathMarkTask.remove(id);
        if (!target.isValid() || target.isDead()) return;
        detonate(player, target);
    }

    private void detonate(Player player, LivingEntity target) {
        ctx.damage(target, deathMarkAmount(target), player);
        ctx.fx().burst(target, Particle.SOUL_FIRE_FLAME, 20, 0.5);
        ctx.fx().sound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f);

        int plague = ctx.rank(player, "plague");
        if (plague > 0) {
            int done = 0;
            for (LivingEntity mob : target.getLocation().getNearbyLivingEntities(6)) {
                if (done >= plague || mob == target || !Mobs.hostile(mob)) continue;
                ctx.damage(mob, deathMarkAmount(mob) / 2, player);
                done++;
            }
        }
    }

    /** 30% of max health (5% on bosses), never more than 20 hearts. */
    static double deathMarkAmount(LivingEntity mob) {
        var attribute = mob.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attribute == null ? 20 : attribute.getValue();
        double percent = Mobs.boss(mob) ? 0.05 : 0.30;
        return Math.min(40, percent * maxHealth);
    }

    // --- Phantom Blades keystone (replaces Smoke Bomb) ------------------------------------------------

    private boolean castPhantomBlades(Player player) {
        Location origin = player.getLocation();
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity mob : origin.getNearbyLivingEntities(8)) {
            if (Mobs.hostile(mob)) targets.add(mob);
        }
        targets.sort(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(origin)));
        if (targets.isEmpty()) return false;
        double weaponDamage = weaponDamage(player);
        int limit = Math.min(4, targets.size());
        for (int i = 0; i < limit; i++) {
            LivingEntity mob = targets.get(i);
            int delay = i * 4;
            ctx.later(() -> {
                Location spot = RogueTargeting.adjacentTowards(mob, origin, 1.2);
                if (spot != null) player.teleport(spot);
                ctx.damage(mob, weaponDamage, player);
                ctx.fx().burst(mob, Particle.SMOKE, 10, 0.3);
                ctx.fx().sound(mob.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
            }, delay);
        }
        return true;
    }

    // --- Shadow Form keystone (replaces Shadow Step) --------------------------------------------------

    private boolean castShadowForm(Player player) {
        UUID id = player.getUniqueId();
        long now = ctx.now();
        state.noRetargetUntil.put(id, now + 4_000);
        state.shadowFormUntil.put(id, now + 4_000);
        state.shadowFormHitPending.add(id);
        SPEED_BOOST.apply(player, 0.3);
        ctx.fx().burst(player, Particle.SMOKE, 20, 0.4);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        ctx.later(() -> endShadowForm(player), 80);
        return true;
    }

    /** Clears Shadow Form naturally if the ending hit never landed. */
    void endShadowForm(Player player) {
        UUID id = player.getUniqueId();
        if (!state.shadowFormHitPending.remove(id)) return;
        state.shadowFormUntil.remove(id);
        SPEED_BOOST.remove(player);
    }

    /** Called from {@link RogueListener} the moment Shadow Form's owed hit lands. */
    void consumeShadowForm(Player player) {
        UUID id = player.getUniqueId();
        state.shadowFormHitPending.remove(id);
        state.shadowFormUntil.remove(id);
        SPEED_BOOST.remove(player);
    }

    // --- Viper Lunge keystone (replaces Shadow Step) ---------------------------------------------------

    private boolean castViperLunge(Player player) {
        LivingEntity target = RogueTargeting.lookedAtMob(player, 8);
        if (target == null) return false;
        Location spot = RogueTargeting.adjacentTowards(target, player.getLocation(), 1.2);
        if (spot != null) player.teleport(spot);
        RogueVenom.applyFixed(ctx, player, target, PotionEffectType.POISON, 1, 120);
        RogueVenom.applyFixed(ctx, player, target, PotionEffectType.WEAKNESS, 0, 120);
        ctx.fx().line(player.getLocation(), target.getLocation(), Particle.ITEM_SLIME);
        ctx.fx().sound(target.getLocation(), Sound.ENTITY_SPIDER_HURT, 1f, 0.8f);
        return true;
    }

    static double weaponDamage(Player player) {
        var attribute = player.getAttribute(Attribute.ATTACK_DAMAGE);
        return attribute == null ? 1.0 : attribute.getValue();
    }

    /** A named, removable attribute modifier so Shadow Form's +30% speed is exact, not a potion level. */
    private static final class NamedAttribute {
        private final String key;

        NamedAttribute(String key) {
            this.key = key;
        }

        void apply(Player player, double scalar) {
            var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute == null) return;
            remove(player);
            attribute.addTransientModifier(new AttributeModifier(
                    new org.bukkit.NamespacedKey("classes", key), scalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }

        void remove(Player player) {
            var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute == null) return;
            var key2 = new org.bukkit.NamespacedKey("classes", key);
            attribute.getModifiers().stream()
                    .filter(m -> m.getKey().equals(key2))
                    .findFirst()
                    .ifPresent(attribute::removeModifier);
        }
    }
}
