package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Artificer's tree, resource (Charge), Sentry and Overdrive (docs/classes-revamp.md, Artificer).
 * This file holds the tree shape, the resource gain and the ability 1/ultimate add-on dispatch; the
 * mining, sentry and gadget mechanics live in {@link ArtificerMining}, {@link ArtificerSentry} and
 * {@link ArtificerGadgets} to keep this file under the split threshold.
 */
final class ArtificerKit implements ClassKit, Listener {

    // Charge gain base sizes (docs/classes-revamp.md, Resource bar table).
    static final double ORE_CHARGE = 2;
    static final double SENTRY_KILL_CHARGE = 5;
    static final int BLOCKS_PER_CHARGE_TICK = 10;

    private final KitContext ctx;
    private final ArtificerMining mining;
    private final ArtificerSentry sentry;
    private final ArtificerGadgets gadgets;
    private final Ability ultimate;

    /** Blocks broken with a tool since the last Charge tick, per player; resets every 10 blocks. */
    private final Map<UUID, Integer> toolBreakCount = new HashMap<>();

    ArtificerKit(KitContext ctx) {
        this.ctx = ctx;
        this.mining = new ArtificerMining(ctx);
        this.sentry = new ArtificerSentry(ctx, this::onSentryKill);
        this.gadgets = new ArtificerGadgets(ctx);
        this.ultimate = SimpleAbility.ultimate("Overdrive",
                p -> "Haste III, Speed II and Resistance I for 15 s; your Sentry fires twice as fast",
                this::castOverdrive);
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.ARTIFICER;
    }

    @Override
    public String resourceName() {
        return "Charge";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.YELLOW;
    }

    @Override
    public String resourceGain() {
        return "+2 per ore mined without Silk Touch, +1 per 10 blocks broken with a tool, +5 per Sentry kill";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Miner", "Engineer", "Tinker");
    }

    @Override
    public List<Talent> talents() {
        List<Talent> out = new java.util.ArrayList<>();
        out.addAll(minerBranch());
        out.addAll(engineerBranch());
        out.addAll(tinkerBranch());
        return out;
    }

    private List<Talent> minerBranch() {
        int b = 0;
        return List.of(
                Talent.node("vein_sense", "Vein Sense", b, 1, 3, Material.GLOW_INK_SAC,
                        r -> "Mining an ore makes same-type ores within " + new int[]{3, 5, 7}[r - 1] + " blocks glow (for you) for 5 s"),
                Talent.node("prospector", "Prospector", b, 1, 2, Material.RAW_GOLD,
                        r -> "+" + r + " extra Charge per ore"),
                Talent.node("vein_miner", "Vein Miner", b, 2, 3, Material.DIAMOND_PICKAXE,
                        r -> "Mining an ore while sneaking also mines up to " + new int[]{4, 8, 12}[r - 1]
                                + " connected ores of the same type (each costs durability)"),
                Talent.node("smelter", "Smelter", b, 2, 2, Material.FURNACE,
                        r -> (r * 10) + "% chance raw iron, gold or copper drops as an ingot"),
                Talent.node("magnet", "Magnet", b, 2, 2, Material.LODESTONE,
                        r -> "Drops from blocks you break fly to you from " + (r == 1 ? 4 : 8) + " blocks"),
                Talent.node("tunneler", "Tunneler", b, 3, 1, Material.NETHERITE_PICKAXE,
                        r -> "During Overclock, your pickaxe mines the block under the one you break too"),
                Talent.node("deep_pockets", "Deep Pockets", b, 3, 2, Material.CHEST,
                        r -> (r * 5) + "% chance an ore drops one extra item (stacks with Fortune)"),
                Talent.node("lucky_strike", "Lucky Strike", b, 3, 2, Material.RABBIT_FOOT,
                        r -> r + "% chance mining stone or deepslate also drops coal, raw iron or raw copper"),
                Talent.node("seismic", "Seismic", b, 4, 2, Material.TNT,
                        r -> "During Overdrive, pickaxes mine a 3x3 face" + (r == 2 ? " (shovels too)" : "")),
                Talent.node("geode_finder", "Geode Finder", b, 4, 1, Material.AMETHYST_CLUSTER,
                        r -> "Vein Sense also shows diamond ore and ancient debris within 8 blocks (once per 30 s)"),
                Talent.keystone("drill", "Drill", b, Slot.TWO, Material.OBSERVER,
                        "Place a drill that mines a 3x3 tunnel 8 blocks forward over 10 s, dropping everything. 90 s"));
    }

    private List<Talent> engineerBranch() {
        int b = 1;
        return List.of(
                Talent.node("calibrated", "Calibrated", b, 1, 3, Material.REDSTONE,
                        r -> "Sentry deals +" + (r * 10) + "%"),
                Talent.node("reinforced", "Reinforced", b, 1, 2, Material.IRON_BLOCK,
                        r -> "Sentry lasts " + (r == 1 ? 5 : 10) + " s longer"),
                Talent.node("multishot", "Multishot", b, 2, 2, Material.CROSSBOW,
                        r -> "Sentry fires " + (r + 1) + " arrows per shot"),
                Talent.node("tesla_coil", "Tesla Coil", b, 2, 3, Material.LIGHTNING_ROD,
                        r -> "Every 3 s Sentry zaps a mob for 1 heart, chaining to " + r + " more"),
                Talent.node("range_finder", "Range Finder", b, 2, 2, Material.SPYGLASS,
                        r -> "Sentry range +" + (r * 3) + " blocks"),
                Talent.node("jump_pad", "Jump Pad", b, 3, 1, Material.SLIME_BLOCK,
                        r -> "Overclock also places a jump pad at your feet for 10 s: it launches you and allies 8 blocks up, no fall damage on landing"),
                Talent.node("frost_rounds", "Frost Rounds", b, 3, 2, Material.SNOWBALL,
                        r -> "Sentry arrows slow mobs for " + r + " s"),
                Talent.node("repair_drone", "Repair Drone", b, 3, 2, Material.ANVIL,
                        r -> "Sentry heals you " + (r == 1 ? "0.5" : "1") + " heart every 5 s while you are within 6 blocks"),
                Talent.node("mortar", "Mortar", b, 4, 2, Material.FIRE_CHARGE,
                        r -> "Every 5 s Sentry lobs a shell, " + (r + 1) + " hearts to mobs within 2 blocks"),
                Talent.node("rapid_deploy", "Rapid Deploy", b, 4, 1, Material.CLOCK,
                        r -> "Sentry's cooldown is 15 s shorter"),
                Talent.keystone("golem", "Golem", b, Slot.ONE, Material.CARVED_PUMPKIN,
                        "Build a small iron golem that fights mobs for you for 30 s. 90 s"));
    }

    private List<Talent> tinkerBranch() {
        int b = 2;
        return List.of(
                Talent.node("field_repair", "Field Repair", b, 1, 3, Material.EXPERIENCE_BOTTLE,
                        r -> "Overclock repairs your held item by " + (r * 2) + "% of its max durability"),
                Talent.node("last_chance", "Last Chance", b, 1, 2, Material.TOTEM_OF_UNDYING,
                        r -> "When a tool would break, it is saved at 1 durability instead (once per " + (r == 1 ? 10 : 5) + " min)"),
                Talent.node("grapple", "Grapple", b, 2, 2, Material.FISHING_ROD,
                        r -> "A fishing rod hooked into a block pulls you to it, up to " + (r == 1 ? 20 : 30) + " blocks"),
                Talent.node("armour_kit", "Armour Kit", b, 2, 3, Material.SHIELD,
                        r -> "Overclock also repairs your armour by " + (r * 2) + "%"),
                Talent.node("shock_absorber", "Shock Absorber", b, 2, 2, Material.SLIME_BALL,
                        r -> (r == 1 ? 25 : 50) + "% less fall damage while holding a tool"),
                Talent.node("rocket_jump", "Rocket Jump", b, 3, 1, Material.FIREWORK_ROCKET,
                        r -> "Overclock launches you 6 blocks forward and up, no fall damage on landing"),
                Talent.node("salvage", "Salvage", b, 3, 2, Material.IRON_NUGGET,
                        r -> (r * 10) + "% chance a tool or armour piece that breaks drops one of its materials"),
                Talent.node("flashbang", "Flashbang", b, 3, 2, Material.GLOWSTONE,
                        r -> "Overclock blinds mobs within " + (r == 1 ? 4 : 6) + " blocks for 3 s"),
                Talent.node("power_cells", "Power Cells", b, 4, 2, Material.REDSTONE_BLOCK,
                        r -> "+" + (r == 1 ? 25 : 50) + "% Charge gain"),
                Talent.node("thrusters", "Thrusters", b, 4, 1, Material.ELYTRA,
                        r -> "While gliding with an elytra, Overclock gives a firework-strength boost"),
                Talent.keystone("grav_gun", "Grav Gun", b, Slot.TWO, Material.PISTON,
                        "Pick up the mob you look at (not a boss, villager or tamed pet) and throw it 10 blocks, 3 hearts on landing. 45 s"));
    }

    @Override
    public Ability secondAbility() {
        return sentry.ability();
    }

    @Override
    public Ability ultimate() {
        return ultimate;
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "drill" -> mining.drillAbility();
            case "golem" -> gadgets.golemAbility();
            case "grav_gun" -> gadgets.gravGunAbility();
            default -> throw new IllegalArgumentException("Artificer has no keystone ability " + keystone.id());
        };
    }

    @Override
    public void enable() {
        ctx.listen(this);
        mining.register();
        sentry.register();
        gadgets.register();
        gadgets.registerPowerCells();
        // Rapid Deploy shortens the Sentry/Drill slot's cooldown; the engine still enforces the 30 s floor.
        ctx.cooldowns().addModifier((player, slot, seconds) -> {
            if (slot != Slot.TWO || !ctx.active(player)) return seconds;
            return ctx.rank(player, "rapid_deploy") > 0 ? seconds - 15 : seconds;
        });
    }

    /** Charge for an ore break, with Prospector's bonus; the block-break counter for the flat tool rule. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (ArtificerMining.isOre(event.getBlock().getType())) {
            if (!ArtificerMining.hasSilkTouch(tool)) {
                int prospector = ctx.rank(player, "prospector");
                ctx.gain(player, ORE_CHARGE + prospector);
            }
        } else if (ArtificerMining.isTool(tool.getType())) {
            UUID id = player.getUniqueId();
            int count = toolBreakCount.merge(id, 1, Integer::sum);
            if (count >= BLOCKS_PER_CHARGE_TICK) {
                toolBreakCount.put(id, count - BLOCKS_PER_CHARGE_TICK);
                ctx.gain(player, 1);
            }
        }
    }

    private void onSentryKill(Player owner) {
        ctx.gain(owner, SENTRY_KILL_CHARGE);
    }

    private boolean castOverdrive(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 300, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 0));
        sentry.startOverdrive(player, 300);
        ctx.fx().burst(player, Particle.ELECTRIC_SPARK, 20, 0.6);
        ctx.fx().sound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        return true;
    }

    /** Ability 1 (Overclock) and ultimate add-ons fire off the shared {@link ClassAbilityEvent}. */
    @EventHandler
    public void onAbility(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.ARTIFICER || !ctx.active(event.player())) return;
        if (event.slot() == Slot.ONE && !event.keystone()) {
            mining.onOverclock(event.player());
            gadgets.onOverclock(event.player());
        } else if (event.slot() == Slot.ULTIMATE) {
            mining.onOverdrive(event.player());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        toolBreakCount.remove(id);
        mining.forget(id);
        sentry.forget(id);
        gadgets.forget(id);
    }
}
