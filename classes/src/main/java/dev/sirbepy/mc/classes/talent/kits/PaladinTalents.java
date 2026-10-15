package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import org.bukkit.Material;

import java.util.List;

/**
 * The Paladin's 33 nodes (docs/classes-revamp.md, "Paladin (Faith)"), split out of {@link
 * PaladinKit} purely to keep that file under the split threshold: this is data only, every number
 * and effect it describes is read and applied by {@code PaladinKit} and {@code PaladinListener}.
 */
final class PaladinTalents {

    private PaladinTalents() {
    }

    static List<Talent> all() {
        return List.of(
                // Light (branch 0): heal the group.
                Talent.node("radiance", "Radiance", 0, 1, 3, Material.GLOWSTONE,
                        r -> "Any heal you receive also heals allies within 4 blocks " + (25 * r) + "% of it"),
                Talent.node("gentle_touch", "Gentle Touch", 0, 1, 2, Material.GOLDEN_CARROT,
                        r -> "Holy Light heals " + r + " heart" + (r > 1 ? "s" : "") + " more"),
                Talent.node("beacon", "Beacon", 0, 2, 2, Material.BEACON,
                        r -> "Holy Light leaves a glowing zone for " + (r == 1 ? 6 : 10)
                                + " s that heals allies inside 0.5 heart every 2 s"),
                Talent.node("purify", "Purify", 0, 2, 3, Material.MILK_BUCKET,
                        r -> "Holy Light removes " + (r == 3 ? "all" : String.valueOf(r)) + " bad effect" + (r == 1 ? "" : "s")),
                Talent.node("sanctuary", "Sanctuary", 0, 2, 2, Material.VINE,
                        r -> "Allies you heal take " + (5 * r) + "% less from mobs for 4 s"),
                Talent.node("lay_on_hands", "Lay on Hands", 0, 3, 1, Material.GOLDEN_APPLE,
                        r -> "Holy Light heals double on an ally under 4 hearts"),
                Talent.node("aura_of_light", "Aura of Light", 0, 3, 2, Material.GLOW_BERRIES,
                        r -> "Allies within 6 blocks heal 0.5 heart every " + (r == 1 ? 10 : 6) + " s"),
                Talent.node("divine_favour", "Divine Favour", 0, 3, 2, Material.NETHER_STAR,
                        r -> "+" + (r == 1 ? 50 : 100) + "% Faith from healing"),
                Talent.node("guardian_light", "Guardian Light", 0, 4, 1, Material.TOTEM_OF_UNDYING,
                        r -> "Once per 30 min, an ally within 8 blocks who would die to a mob is saved at 2 hearts"),
                Talent.node("halo", "Halo", 0, 4, 2, Material.GOLD_INGOT,
                        r -> "Judgement heals allies " + (2 * r) + " hearts more"),
                Talent.keystone("lightwell", "Lightwell", 0, Slot.TWO, Material.CONDUIT,
                        "Plant a well for 12 s that heals allies within 5 blocks 1 heart every 2 s. 75 s"),

                // Crusader (branch 1): smite the undead.
                Talent.node("undead_bane", "Undead Bane", 1, 1, 3, Material.GOLDEN_SWORD,
                        r -> "+" + (5 * r) + "% melee damage to undead"),
                Talent.node("holy_weapon", "Holy Weapon", 1, 1, 2, Material.BLAZE_POWDER,
                        r -> "Smite deals " + r + " heart" + (r > 1 ? "s" : "") + " more"),
                Talent.node("consecrate", "Consecrate", 1, 2, 2, Material.GOLD_BLOCK,
                        r -> "Smite leaves holy ground for " + (r == 1 ? 4 : 6) + " s that burns undead for 1 heart a second"),
                Talent.node("zeal", "Zeal", 1, 2, 3, Material.CLOCK,
                        r -> "Melee kills on undead take " + (5 * r) + "% off Smite's cooldown"),
                Talent.node("righteous_fury", "Righteous Fury", 1, 2, 2, Material.FIRE_CHARGE,
                        r -> "A mob hit by Smite takes +" + (r == 1 ? 10 : 20) + "% melee damage from you for 5 s"),
                Talent.node("chain_smite", "Chain Smite", 1, 3, 1, Material.LEAD,
                        r -> "Smite jumps to 2 more mobs at half damage"),
                Talent.node("exorcist", "Exorcist", 1, 3, 2, Material.SOUL_TORCH,
                        r -> "Undead hit by Smite burn for " + (r == 1 ? 3 : 6) + " s"),
                Talent.node("sun_blade", "Sun Blade", 1, 3, 2, Material.SUNFLOWER,
                        r -> "Outdoors in daylight, +" + (r == 1 ? 5 : 10) + "% melee damage to undead"),
                Talent.node("hammer_of_justice", "Hammer of Justice", 1, 4, 2, Material.GOLDEN_AXE,
                        r -> "Smite stuns for " + r + " s"),
                Talent.node("crusade", "Crusade", 1, 4, 1, Material.WHITE_BANNER,
                        r -> "Judgement reaches 14 blocks"),
                Talent.keystone("divine_storm", "Divine Storm", 1, Slot.ONE, Material.LIGHTNING_ROD,
                        "Lightning (visual only, no fire) strikes up to 4 mobs within 8 blocks for 3 hearts each, "
                                + "double on undead. 60 s"),

                // Martyr (branch 2): pay with your own health.
                Talent.node("self_sacrifice", "Self-Sacrifice", 2, 1, 3, Material.GHAST_TEAR,
                        r -> "Holy Light costs you 1 heart but heals others " + r + " heart" + (r > 1 ? "s" : "") + " more"),
                Talent.node("endurance", "Endurance", 2, 1, 2, Material.IRON_CHESTPLATE,
                        r -> "Merciful Hand is " + (r == 1 ? "halved" : "gone") + " while you are under half health"),
                Talent.node("shield_of_faith", "Shield of Faith", 2, 2, 2, Material.SHIELD,
                        r -> "Holy Light gives you " + (2 * r) + " Absorption hearts"),
                Talent.node("penance", "Penance", 2, 2, 3, Material.MAGMA_CREAM,
                        r -> "+" + r + " Faith per heart of mob damage you take"),
                Talent.node("blood_oath", "Blood Oath", 2, 2, 2, Material.REDSTONE,
                        r -> "Under 30% health, Smite heals you " + (r == 1 ? 2 : 3) + " hearts"),
                Talent.node("divine_bubble", "Divine Bubble", 2, 3, 1, Material.END_CRYSTAL,
                        r -> "Once per 20 min, a mob hit that would kill you leaves you at 1 heart, "
                                + "and mobs can't hurt you for 3 s"),
                Talent.node("martyrdom", "Martyrdom", 2, 3, 2, Material.SOUL_LANTERN,
                        r -> "When you die, allies within 8 blocks heal " + (4 * r) + " hearts"),
                Talent.node("ember_of_hope", "Ember of Hope", 2, 3, 2, Material.BLAZE_POWDER,
                        r -> "Under 4 hearts you heal 0.5 heart every " + (r == 1 ? 4 : 2) + " s"),
                Talent.node("last_rites", "Last Rites", 2, 4, 2, Material.WITHER_ROSE,
                        r -> "Judgement deals " + (2 * r) + " hearts more while you are under half health"),
                Talent.node("unbroken", "Unbroken", 2, 4, 1, Material.MILK_BUCKET,
                        r -> "You are immune to poison and wither"),
                Talent.keystone("ascension", "Ascension", 2, Slot.TWO, Material.NETHER_STAR,
                        "Give up half your current health; allies within 8 blocks heal twice that and get "
                                + "Resistance I for 5 s. 60 s"));
    }
}
