package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;

import java.util.List;

/**
 * The Guardian's tree, resource (Resolve), Shield Bash and Fortress (docs/classes-revamp.md,
 * Guardian). The 33 nodes are declared here; the listeners that make them do anything live in
 * {@link GuardianListeners}, and the three keystone abilities plus Shield Bash and Fortress live in
 * {@link GuardianAbilities}. Shared in-memory state (Last Stand's own window, grit stacks, cooldowns
 * the engine doesn't track) lives in {@link GuardianState}.
 */
final class GuardianKit implements ClassKit {

    private final KitContext ctx;
    private final GuardianState state = new GuardianState();
    private final Ability second;
    private final Ability ultimate;
    private final Ability bastion;
    private final Ability oath;
    private final Ability leapSmash;

    GuardianKit(KitContext ctx) {
        this.ctx = ctx;
        this.second = GuardianAbilities.shieldBash(ctx, state);
        this.ultimate = GuardianAbilities.fortress(ctx, state);
        this.bastion = GuardianAbilities.bastion(ctx, state);
        this.oath = GuardianAbilities.oath(ctx, state);
        this.leapSmash = GuardianAbilities.leapSmash(ctx, state);
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.GUARDIAN;
    }

    @Override
    public String resourceName() {
        return "Resolve";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.BLUE;
    }

    @Override
    public String resourceGain() {
        return "+4 per mob hit taken, +3 per mob hit blocked with a shield";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Bulwark", "Protector", "Juggernaut");
    }

    @Override
    public List<Talent> talents() {
        return List.of(
                // Bulwark (0): the shield.
                Talent.node("shield_mastery", "Shield Mastery", 0, 1, 3, Material.SHIELD,
                        r -> "Blocking a mob's melee hit reflects " + (r * 10) + "% of it back"),
                Talent.node("steady", "Steady", 0, 1, 2, Material.IRON_BOOTS,
                        r -> (r == 1 ? 25 : 50) + "% less knockback while blocking"),
                Talent.node("thorned_shield", "Thorned Shield", 0, 2, 2, Material.CACTUS,
                        r -> "Mobs that hit your shield are slowed for " + r + " s"),
                Talent.node("iron_will", "Iron Will", 0, 2, 3, Material.IRON_CHESTPLATE,
                        r -> "Last Stand also gives " + r + " Absorption heart" + (r == 1 ? "" : "s")),
                Talent.node("bash_mastery", "Bash Mastery", 0, 2, 2, Material.IRON_INGOT,
                        r -> "Shield Bash deals " + (r == 1 ? 2 : 4) + " hearts"),
                Talent.node("unbreakable", "Unbreakable", 0, 3, 1, Material.DIAMOND,
                        r -> "During Last Stand, blocked hits don't wear your shield"),
                Talent.node("retaliation", "Retaliation", 0, 3, 2, Material.IRON_SWORD,
                        r -> "After blocking 3 mob hits within 5 s, the next melee hit deals +" + (r == 1 ? 20 : 40) + "%"),
                Talent.node("rooted", "Rooted", 0, 3, 2, Material.OAK_SAPLING,
                        r -> "Standing still while sneaking, you take " + (r == 1 ? 5 : 10) + "% less from mobs"),
                Talent.node("aegis", "Aegis", 0, 4, 2, Material.SPECTRAL_ARROW,
                        r -> "Blocking a mob's arrow sends it back at the shooter for " + (r == 1 ? 50 : 100) + "% of its damage"),
                Talent.node("stone_skin", "Stone Skin", 0, 4, 1, Material.STONE,
                        r -> "No knockback during Last Stand"),
                Talent.keystone("bastion", "Bastion", 0, Slot.TWO, Material.OBSIDIAN,
                        "For 6 s every mob projectile within 4 blocks of you is deflected. 45 s"),

                // Protector (1): guard your friends.
                Talent.node("guardian_angel", "Guardian Angel", 1, 1, 3, Material.FEATHER,
                        r -> "Allies within 4 blocks take " + (r * 5) + "% less from mobs; you take that part instead"),
                Talent.node("bodyguard", "Bodyguard", 1, 1, 2, Material.NAME_TAG,
                        r -> "When a mob hits an ally within 8 blocks, " + (r == 1 ? 10 : 20) + "% chance it switches to you"),
                Talent.node("intervene", "Intervene", 1, 2, 2, Material.ENDER_PEARL,
                        r -> "Shield Bash on an ally (look at them) dashes you to them, up to " + (r == 1 ? 8 : 12)
                                + " blocks, and gives them Resistance I for 3 s"),
                Talent.node("shared_stand", "Shared Stand", 1, 2, 3, Material.SHIELD,
                        r -> "Last Stand also covers allies within " + (r * 2) + " blocks at half strength"),
                Talent.node("lifeline", "Lifeline", 1, 2, 2, Material.GOLDEN_CARROT,
                        r -> "An ally within 8 blocks who drops under 3 hearts is healed " + (r == 1 ? 2 : 3) + " hearts (once per 90 s)"),
                Talent.node("sacrifice", "Sacrifice", 1, 3, 1, Material.MAGMA_CREAM,
                        r -> "Fortress also covers allies within 6 blocks"),
                Talent.node("taunting_shout", "Taunting Shout", 1, 3, 2, Material.GOAT_HORN,
                        r -> "Shield Bash makes mobs within " + (r == 1 ? 6 : 10) + " blocks target you"),
                Talent.node("shared_resolve", "Shared Resolve", 1, 3, 2, Material.LAPIS_LAZULI,
                        r -> "+" + (r == 1 ? 3 : 6) + " Resolve when an ally near you is hit"),
                Talent.node("guardian_spirit", "Guardian Spirit", 1, 4, 1, Material.TOTEM_OF_UNDYING,
                        r -> "Once per 10 min, an ally within 8 blocks who would die to a mob is saved at 1 heart, and you lose 4 hearts"),
                Talent.node("stalwart_aura", "Stalwart Aura", 1, 4, 2, Material.BEACON,
                        r -> "Allies within 6 blocks take " + (r == 1 ? 25 : 50) + "% less knockback from mobs"),
                Talent.keystone("oath", "Oath", 1, Slot.ONE, Material.ENCHANTED_GOLDEN_APPLE,
                        "Link to the nearest ally for 10 s: you take half of what mobs deal them, and both of you get Regeneration I. 60 s"),

                // Juggernaut (2): crash through.
                Talent.node("momentum", "Momentum", 2, 1, 3, Material.RABBIT_FOOT,
                        r -> "Sprinting into a mob knocks it back for " + heartsLabel(r) + " (once per 3 s)"),
                Talent.node("plated", "Plated", 2, 1, 2, Material.IRON_LEGGINGS,
                        r -> "Heavy Plate's slowness is " + (r == 1 ? "halved" : "removed") + " while you wear full iron armour or better"),
                Talent.node("shoulder_charge", "Shoulder Charge", 2, 2, 2, Material.PISTON,
                        r -> "Shield Bash dashes you " + (r == 1 ? 4 : 6) + " blocks forward first"),
                Talent.node("bulldoze", "Bulldoze", 2, 2, 3, Material.ANVIL,
                        r -> "Mobs you knock into a wall take " + r + " heart" + (r == 1 ? "" : "s")),
                Talent.node("grit", "Grit", 2, 2, 2, Material.REDSTONE,
                        r -> "Each mob hit taken gives +" + (r == 1 ? 2 : 4) + "% melee damage for 5 s, up to 5 stacks"),
                Talent.node("earthshaker", "Earthshaker", 2, 3, 1, Material.COBBLESTONE,
                        r -> "Landing from 4+ blocks deals 2 hearts to mobs within 3 blocks"),
                Talent.node("iron_fist", "Iron Fist", 2, 3, 2, Material.IRON_INGOT,
                        r -> "With a shield in your offhand, melee hits slow mobs for " + r + " s"),
                Talent.node("unyielding", "Unyielding", 2, 3, 2, Material.BLAZE_POWDER,
                        r -> "Under 30% health, Resolve gain is " + (r == 1 ? "doubled" : "tripled")),
                Talent.node("trample", "Trample", 2, 4, 2, Material.IRON_BOOTS,
                        r -> "During Fortress, walking into mobs knocks them back for " + (r + 1) + " hearts"),
                Talent.node("immovable", "Immovable", 2, 4, 1, Material.BEDROCK,
                        r -> "No knockback from mobs while sneaking"),
                Talent.keystone("leap_smash", "Leap Smash", 2, Slot.TWO, Material.SLIME_BALL,
                        "Leap at the spot you look at (up to 12 blocks); landing deals 4 hearts to mobs within 3 blocks and knocks them up. 45 s")
        );
    }

    private static String heartsLabel(int rank) {
        double hearts = rank == 1 ? 1 : rank == 2 ? 1.5 : 2;
        String number = hearts == Math.rint(hearts) ? String.valueOf((int) hearts) : String.valueOf(hearts);
        return number + (hearts == 1 ? " heart" : " hearts");
    }

    @Override
    public Ability secondAbility() {
        return second;
    }

    @Override
    public Ability ultimate() {
        return ultimate;
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "bastion" -> bastion;
            case "oath" -> oath;
            case "leap_smash" -> leapSmash;
            default -> throw new IllegalArgumentException("Guardian has no keystone " + keystone.id());
        };
    }

    @Override
    public void enable() {
        ctx.listen(new GuardianListeners(ctx, state));
        ctx.resources().addGainModifier(player -> GuardianListeners.unyieldingMultiplier(ctx, player));
    }
}
