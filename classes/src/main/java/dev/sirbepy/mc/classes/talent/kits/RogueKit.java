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
 * The Rogue's tree (docs/classes-revamp.md, "Rogue (Energy)"): 33 nodes across Assassin (strike
 * from behind), Shadow (unseen and agile) and Venom (poison everything), plus Smoke Bomb, Death
 * Mark and the three keystones. This file only owns the tree shape and which kit object answers
 * which ability; the mechanics live in {@link RogueAbilities} (Smoke Bomb, Death Mark and the
 * keystones), {@link RogueListener} (melee, kill and ability-1 add-on talents) and
 * {@link RogueMovement} (Double Jump and Wall Climb).
 */
final class RogueKit implements ClassKit {

    private final KitContext ctx;
    private final RogueState state = new RogueState();
    private final RogueAbilities abilities;
    private final RogueListener listener;
    private final RogueMovement movement;

    RogueKit(KitContext ctx) {
        this.ctx = ctx;
        this.abilities = new RogueAbilities(ctx, state);
        this.listener = new RogueListener(ctx, state, abilities);
        this.movement = new RogueMovement(ctx, state);
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.ROGUE;
    }

    @Override
    public String resourceName() {
        return "Energy";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.WHITE;
    }

    @Override
    public String resourceGain() {
        return "+8 per melee hit while sneaking, +15 per backstab, +10 per kill";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Assassin", "Shadow", "Venom");
    }

    @Override
    public List<Talent> talents() {
        return List.of(
                // Assassin (branch 0): strike from behind.
                Talent.node("backstab", "Backstab", 0, 1, 3, Material.IRON_SWORD,
                        r -> "Melee hits on a mob from behind deal +" + (10 * r) + "%"),
                Talent.node("cutthroat", "Cutthroat", 0, 1, 2, Material.TRIPWIRE_HOOK,
                        r -> "+" + (10 * r) + "% melee damage to mobs that aren't targeting you"),
                Talent.node("ambush", "Ambush", 0, 2, 2, Material.INK_SAC,
                        r -> "The first hit out of Invisibility deals +" + (r == 1 ? 30 : 60) + "%"),
                Talent.node("shadow_strike", "Shadow Strike", 0, 2, 3, Material.ENDER_PEARL,
                        r -> "Shadow Step lands behind the mob you look at, up to " + (3 * r) + " blocks"),
                Talent.node("finisher", "Finisher", 0, 2, 2, Material.GOLDEN_SWORD,
                        r -> "Kills take " + (5 * r) + " s off Shadow Step's cooldown"),
                Talent.node("assassinate", "Assassinate", 0, 3, 1, Material.NETHERITE_SWORD,
                        r -> "A backstab on a mob that isn't targeting anyone deals double"),
                Talent.node("swift_kill", "Swift Kill", 0, 3, 2, Material.SUGAR,
                        r -> "Kills give Speed II for " + (2 * r) + " s"),
                Talent.node("precision", "Precision", 0, 3, 2, Material.SPECTRAL_ARROW,
                        r -> "Critical hits on mobs deal +" + (10 * r) + "%"),
                Talent.node("chain_kill", "Chain Kill", 0, 4, 2, Material.IRON_CHAIN,
                        r -> "A kill within 2 s after Shadow Step makes it ready again (once per " + (r == 1 ? 30 : 20) + " s)"),
                Talent.node("vanishing_act", "Vanishing Act", 0, 4, 1, Material.PHANTOM_MEMBRANE,
                        r -> "Shadow Step leaves you invisible for 2 s"),
                Talent.keystone("phantom_blades", "Phantom Blades", 0, Slot.TWO, Material.ENDER_EYE,
                        "Teleport between up to 4 mobs within 8 blocks, hitting each with your weapon. 60 s"),

                // Shadow (branch 1): unseen and agile.
                Talent.node("light_feet", "Light Feet", 1, 1, 3, Material.FEATHER,
                        r -> "No fall damage from falls under " + (2 + 2 * r) + " blocks"),
                Talent.node("quiet_steps", "Quiet Steps", 1, 1, 2, Material.BLACK_WOOL,
                        r -> "While sneaking, mobs further than " + (r == 1 ? 8 : 5) + " blocks don't notice you"),
                Talent.node("double_jump", "Double Jump", 1, 2, 2, Material.RABBIT_FOOT,
                        r -> "Press jump in mid-air to jump again, once per " + (r == 1 ? 8 : 4) + " s"),
                Talent.node("smoke_screen", "Smoke Screen", 1, 2, 3, Material.GUNPOWDER,
                        r -> "Smoke Bomb reaches " + (6 + 2 * r) + " blocks"),
                Talent.node("evasion", "Evasion", 1, 2, 2, Material.SHIELD,
                        r -> (5 * r) + "% chance to dodge a mob's melee hit completely"),
                Talent.node("wall_climb", "Wall Climb", 1, 3, 1, Material.SCAFFOLDING,
                        r -> "Sneak against a wall to climb it, up to 6 blocks"),
                Talent.node("decoy", "Decoy", 1, 3, 2, Material.ARMOR_STAND,
                        r -> "Smoke Bomb leaves a copy of you that mobs attack for " + (r == 1 ? 3 : 6) + " s"),
                Talent.node("night_stalker", "Night Stalker", 1, 3, 2, Material.COAL,
                        r -> "At night or in darkness (light level 7 or less), +" + (5 * r) + "% melee damage to mobs"),
                Talent.node("shadow_dance", "Shadow Dance", 1, 4, 2, Material.ECHO_SHARD,
                        r -> "Shadow Step holds 2 charges" + (r == 2 ? " (they recharge 25% faster)" : "")),
                Talent.node("ghost", "Ghost", 1, 4, 1, Material.GLASS,
                        r -> "Smoke Bomb also hides your armour and held items"),
                Talent.keystone("shadow_form", "Shadow Form", 1, Slot.ONE, Material.BLACK_DYE,
                        "For 4 s mobs can't target you, you move 30% faster, and your first hit ends it with +50% damage. 60 s"),

                // Venom (branch 2): poison everything.
                Talent.node("poison_blade", "Poison Blade", 2, 1, 3, Material.SPIDER_EYE,
                        r -> "Melee hits have a " + (10 * r) + "% chance to poison a mob (Poison I, 3 s)"),
                Talent.node("toxicologist", "Toxicologist", 2, 1, 2, Material.MILK_BUCKET,
                        r -> "Poison on you lasts " + (r == 1 ? "50% shorter" : "not at all")),
                Talent.node("envenom", "Envenom", 2, 2, 2, Material.FERMENTED_SPIDER_EYE,
                        r -> "Poisoned mobs take +" + (10 * r) + "% melee damage from you"),
                Talent.node("toxic_cloud", "Toxic Cloud", 2, 2, 3, Material.LINGERING_POTION,
                        r -> "Smoke Bomb's cloud poisons mobs inside for " + r + " s each second"),
                Talent.node("weakening_toxin", "Weakening Toxin", 2, 2, 2, Material.POISONOUS_POTATO,
                        r -> "Poisoned mobs deal " + (10 * r) + "% less damage"),
                Talent.node("wither_edge", "Wither Edge", 2, 3, 1, Material.WITHER_ROSE,
                        r -> "Undead (immune to poison) get Wither I instead"),
                Talent.node("contagion", "Contagion", 2, 3, 2, Material.ROTTEN_FLESH,
                        r -> "A poisoned mob that dies passes its poison to " + r + " mob" + (r > 1 ? "s" : "") + " nearby"),
                Talent.node("potent", "Potent", 2, 3, 2, Material.DRAGON_BREATH,
                        r -> "Your poison is level II" + (r == 2 ? " (lasts 2 s longer too)" : "")),
                Talent.node("plague", "Plague", 2, 4, 2, Material.NETHER_WART,
                        r -> "Death Mark also marks " + r + " nearby mob" + (r > 1 ? "s" : "") + " at half strength"),
                Talent.node("antidote", "Antidote", 2, 4, 1, Material.GOLDEN_CARROT,
                        r -> "Killing a poisoned mob heals you 1 heart"),
                Talent.keystone("viper_lunge", "Viper Lunge", 2, Slot.ONE, Material.SPLASH_POTION,
                        "Dash to the mob you look at (up to 8 blocks), Poison II and Weakness I for 6 s. 45 s"));
    }

    @Override
    public Ability secondAbility() {
        return abilities.smokeBomb();
    }

    @Override
    public Ability ultimate() {
        return abilities.deathMark();
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "phantom_blades" -> abilities.phantomBlades();
            case "shadow_form" -> abilities.shadowForm();
            case "viper_lunge" -> abilities.viperLunge();
            default -> throw new IllegalArgumentException("Rogue has no keystone ability " + keystone.id());
        };
    }

    @Override
    public void disable() {
        movement.releaseAll();
    }

    @Override
    public void enable() {
        ctx.listen(listener);
        ctx.listen(movement);
        movement.start();
    }

    KitContext ctx() {
        return ctx;
    }

    RogueState state() {
        return state;
    }
}
