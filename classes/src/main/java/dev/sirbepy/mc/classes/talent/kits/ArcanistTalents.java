package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import org.bukkit.Material;

import java.util.List;

/**
 * The Arcanist's 33 nodes (docs/classes-revamp.md, Arcanist): Pyromancer (branch 0), Frost
 * (branch 1), Chronomancer (branch 2), 2/3/3/2/1 nodes per tier. Split out of {@link ArcanistKit}
 * only because the tree declaration alone is long; the numbers here are presentation strings for
 * the tree menu, {@link ArcanistKit} holds the matching logic constants separately.
 */
final class ArcanistTalents {

    private ArcanistTalents() {
    }

    static List<Talent> build() {
        return List.of(
                // Pyromancer (branch 0): fire.
                Talent.node("kindle", "Kindle", 0, 1, 3, Material.FLINT_AND_STEEL,
                        r -> "Arcane Bolt sets mobs on fire for " + new int[]{2, 4, 6}[r - 1] + " s"),
                Talent.node("ember_skin", "Ember Skin", 0, 1, 2, Material.MAGMA_CREAM,
                        r -> "You take " + pct(new double[]{0.25, 0.50}[r - 1]) + " less fire damage"),
                Talent.node("fireball", "Fireball", 0, 2, 2, Material.FIRE_CHARGE,
                        r -> "Arcane Bolt bursts on impact for " + pct(new double[]{0.5, 1.0}[r - 1])
                                + " of its damage to mobs within 2 blocks"),
                Talent.node("heat_wave", "Heat Wave", 0, 2, 3, Material.BLAZE_POWDER,
                        r -> "Mend also deals " + hearts(new double[]{1, 1.5, 2}[r - 1]) + " to mobs within 3 blocks"),
                Talent.node("combust", "Combust", 0, 2, 2, Material.COAL,
                        r -> "Burning mobs take " + pct(new double[]{0.10, 0.20}[r - 1]) + " more from your abilities"),
                Talent.node("flame_trail", "Flame Trail", 0, 3, 1, Material.CAMPFIRE,
                        r -> "For 5 s after Arcane Bolt, mobs crossing your path catch fire"),
                Talent.node("inferno", "Inferno", 0, 3, 3, Material.MAGMA_BLOCK,
                        r -> "Burning mobs you kill burst for " + hearts(new double[]{1, 1.5, 2}[r - 1]) + " to mobs nearby"),
                Talent.node("pyre", "Pyre", 0, 3, 2, Material.NETHERRACK,
                        r -> "Meteor leaves burning ground for " + new int[]{3, 6}[r - 1] + " s"),
                Talent.node("phoenix", "Phoenix", 0, 4, 1, Material.FEATHER,
                        r -> "Mend heals 1 extra heart per burning mob within 6 blocks (up to 3)"),
                Talent.node("spreading_flame", "Spreading Flame", 0, 4, 2, Material.TORCH,
                        r -> "Fire from Arcane Bolt jumps to " + new int[]{1, 2}[r - 1] + " nearby mobs"),
                Talent.keystone("flamestrike", "Flamestrike", 0, Slot.ONE, Material.BLAZE_ROD,
                        "A column of fire on the block you look at (up to 20 blocks), 5 hearts and 6 s of fire "
                                + "to mobs within 3 blocks. 45 s"),

                // Frost (branch 1): slow and freeze.
                Talent.node("chill", "Chill", 1, 1, 3, Material.SNOWBALL,
                        r -> "Arcane Bolt slows mobs (Slowness I) for " + new int[]{2, 3, 4}[r - 1] + " s"),
                Talent.node("ice_armour", "Ice Armour", 1, 1, 2, Material.PACKED_ICE,
                        r -> "Mend also gives " + new int[]{1, 2}[r - 1] + " Absorption hearts"),
                Talent.node("frost_nova", "Frost Nova", 1, 2, 2, Material.BLUE_ICE,
                        r -> "Mend freezes mobs within " + new int[]{3, 5}[r - 1] + " blocks in place for 2 s"),
                Talent.node("shatter", "Shatter", 1, 2, 3, Material.ICE,
                        r -> "Slowed or frozen mobs take " + pct(new double[]{0.05, 0.10, 0.15}[r - 1]) + " more from your abilities"),
                Talent.node("brittle", "Brittle", 1, 2, 2, Material.FROSTED_ICE,
                        r -> "Mobs you slow also deal " + pct(new double[]{0.10, 0.20}[r - 1]) + " less damage"),
                Talent.node("ice_lance", "Ice Lance", 1, 3, 1, Material.ARROW,
                        r -> "Arcane Bolt passes through up to 3 mobs"),
                Talent.node("permafrost", "Permafrost", 1, 3, 2, Material.LAPIS_LAZULI,
                        r -> "Your slows last " + pct(new double[]{0.5, 1.0}[r - 1]) + " longer"),
                Talent.node("glacial_ward", "Glacial Ward", 1, 3, 3, Material.SHIELD,
                        r -> "A mob that hits you has a " + pct(new double[]{0.10, 0.20, 0.30}[r - 1]) + " chance to freeze for 1 s"),
                Talent.node("hailstorm", "Hailstorm", 1, 4, 2, Material.SNOW_BLOCK,
                        r -> "Meteor also slows mobs within 6 blocks for " + new int[]{4, 8}[r - 1] + " s"),
                Talent.node("cold_snap", "Cold Snap", 1, 4, 1, Material.POWDER_SNOW_BUCKET,
                        r -> "Killing a slowed mob with Arcane Bolt takes 10 s off its cooldown"),
                Talent.keystone("ice_tomb", "Ice Tomb", 1, Slot.ONE, Material.BLUE_ICE,
                        "Encase yourself for 3 s: mobs can't hurt you (player damage halved), heal 4 hearts, "
                                + "then Frost Nova. 60 s"),

                // Chronomancer (branch 2): time and movement.
                Talent.node("quick_mind", "Quick Mind", 2, 1, 3, Material.CLOCK,
                        r -> "Abilities 1 and 2 cool down " + pct(new double[]{0.03, 0.06, 0.09}[r - 1]) + " faster (never under 30 s)"),
                Talent.node("accelerate", "Accelerate", 2, 1, 2, Material.GLOWSTONE_DUST,
                        r -> pct(new double[]{0.25, 0.50}[r - 1]) + " Arcana gain"),
                Talent.node("blink", "Blink", 2, 2, 2, Material.ENDER_PEARL,
                        r -> "Mend also teleports you " + new int[]{4, 8}[r - 1] + " blocks forward (never into blocks)"),
                Talent.node("echo", "Echo", 2, 2, 3, Material.ECHO_SHARD,
                        r -> pct(new double[]{0.05, 0.10, 0.15}[r - 1]) + " chance Arcane Bolt fires a free second bolt"),
                Talent.node("slow_time", "Slow Time", 2, 2, 2, Material.REDSTONE_TORCH,
                        r -> "Mend slows mobs within 4 blocks (Slowness II) for " + new int[]{2, 4}[r - 1] + " s"),
                Talent.node("recall", "Recall", 2, 3, 1, Material.COMPASS,
                        r -> "Pressing Arcane Bolt's key again within 5 s returns you to where you cast it"),
                Talent.node("temporal_shield", "Temporal Shield", 2, 3, 2, Material.SHIELD,
                        r -> "After Mend, the next mob hit within 5 s is reduced " + pct(new double[]{0.30, 0.60}[r - 1])),
                Talent.node("borrowed_time", "Borrowed Time", 2, 3, 2, Material.EXPERIENCE_BOTTLE,
                        r -> "Every " + new int[]{10, 5}[r - 1] + " orb XP points you pick up take 1 s off Arcane Bolt's cooldown"),
                Talent.node("overcharge", "Overcharge", 2, 4, 2, Material.GLOWSTONE,
                        r -> "Meteor refunds " + new int[]{20, 40}[r - 1] + " Arcana"),
                Talent.node("quickening", "Quickening", 2, 4, 1, Material.SUGAR,
                        r -> "At full Arcana you have Speed I and Haste I"),
                Talent.keystone("rewind", "Rewind", 2, Slot.ONE, Material.CLOCK,
                        "Return to the health and position you had 5 s ago. 60 s")
        );
    }

    private static String pct(double fraction) {
        double value = fraction * 100;
        return (value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value)) + "%";
    }

    private static String hearts(double health) {
        return (health == Math.rint(health) ? String.valueOf((int) health) : String.valueOf(health))
                + (health == 1 ? " heart" : " hearts");
    }
}
