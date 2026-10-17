package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;

import java.util.List;

/**
 * The Ranger's tree, Focus resource and its two new abilities (docs/classes-revamp.md, Ranger).
 * The talent logic lives in helpers, one per branch plus two pieces split off Sharpshooter:
 * {@link RangerArrows} (Sharpshooter's nodes, Volley and the arrow tagging every branch shares),
 * {@link RangerShots} (Arrow Rain, Railshot), {@link RangerKills} (Ricochet, Homecoming),
 * {@link RangerSnares} (Trapper) and {@link RangerPack} (Beastmaster).
 */
final class RangerKit implements ClassKit {

    private final RangerArrows arrows;
    private final RangerShots shots;
    private final RangerKills kills;
    private final RangerSnares snares;
    private final RangerPack pack;

    RangerKit(KitContext ctx) {
        this.arrows = new RangerArrows(ctx);
        this.shots = new RangerShots(ctx);
        this.kills = new RangerKills(ctx, arrows);
        this.snares = new RangerSnares(ctx, arrows);
        this.pack = new RangerPack(ctx);
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.RANGER;
    }

    @Override
    public String resourceName() {
        return "Focus";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.GREEN;
    }

    @Override
    public String resourceGain() {
        return "+5 per arrow or trident hit on a mob, +10 more for a fully charged bow shot or any trident throw";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Sharpshooter", "Trapper", "Beastmaster");
    }

    @Override
    public List<Talent> talents() {
        return List.of(
                // Sharpshooter: precise long shots
                Talent.node("long_shot", "Long Shot", 0, 1, 3, Material.SPECTRAL_ARROW,
                        rank -> "Fully charged shots deal +" + (4 * rank) + "% to mobs more than 15 blocks away"),
                Talent.node("eagle_eye", "Eagle Eye", 0, 1, 2, Material.ENDER_EYE,
                        rank -> "Fully charged hits make the mob glow (for you only) for " + (5 * rank) + " s"),
                Talent.node("piercing_shot", "Piercing Shot", 0, 2, 2, Material.ARROW,
                        rank -> "Fully charged arrows pierce " + rank + (rank == 1 ? " mob" : " mobs")),
                Talent.node("called_shot", "Called Shot", 0, 2, 3, Material.TARGET,
                        rank -> "The first arrow on a mob at full health deals +" + (10 * rank) + "%"),
                Talent.node("recovery", "Recovery", 0, 2, 2, Material.HOPPER,
                        rank -> (20 * rank) + "% chance an arrow that hits a mob comes back to your inventory"),
                Talent.node("marked", "Marked", 0, 3, 1, Material.GLOW_INK_SAC,
                        rank -> "Glowing mobs from Eagle Eye take +10% from all your hits"),
                Talent.node("deadeye", "Deadeye", 0, 3, 3, Material.CROSSBOW,
                        rank -> "Back-to-back arrow hits on the same mob stack +5%, up to " + (rank + 2) + " stacks"),
                Talent.node("wind_reader", "Wind Reader", 0, 3, 2, Material.FEATHER,
                        rank -> "Your arrows fly " + (10 * rank) + "% faster"),
                Talent.node("ricochet", "Ricochet", 0, 4, 2, Material.TRIPWIRE_HOOK,
                        rank -> "An arrow that kills a mob fires a new arrow at the nearest mob within "
                                + (rank == 1 ? 6 : 10) + " blocks"),
                Talent.node("snipers_nest", "Sniper's Nest", 0, 4, 1, Material.SCAFFOLDING,
                        rank -> "After standing still and sneaking for 1 s, shots deal +15% to mobs"),
                Talent.node("homecoming", "Homecoming", 0, 4, 1, Material.HEART_OF_THE_SEA,
                        rank -> "Mobs your arrows or thrown tridents kill send their drops and XP straight to you"),
                Talent.keystone("railshot", "Railshot", 0, dev.sirbepy.mc.classes.talent.Slot.TWO, Material.END_ROD,
                        "One beam along a 40-block line, 6 hearts to every mob on it. 45 s"),

                // Trapper: control the ground
                Talent.node("snare", "Snare", 1, 1, 3, Material.STRING,
                        rank -> "Fleet Foot drops a snare at your feet: the first mob to step on it is rooted " + rank + " s"),
                Talent.node("barbed_arrows", "Barbed Arrows", 1, 1, 2, Material.CACTUS,
                        rank -> "Arrows slow mobs (Slowness I) for " + (2 * rank) + " s"),
                Talent.node("caltrops", "Caltrops", 1, 2, 2, Material.IRON_NUGGET,
                        rank -> "Volley arrows leave caltrops where they land: Slowness II for " + (rank == 1 ? 3 : 5)
                                + " s to mobs walking over them"),
                Talent.node("bola", "Bola", 1, 2, 3, Material.LEAD,
                        rank -> (5 * rank) + "% chance an arrow roots a mob for 1 s"),
                Talent.node("kiting", "Kiting", 1, 2, 2, Material.RABBIT_FOOT,
                        rank -> "Hitting a mob within 5 blocks with an arrow gives you Speed I for " + (2 * rank) + " s"),
                Talent.node("explosive_snare", "Explosive Snare", 1, 3, 1, Material.TNT,
                        rank -> "Snares explode when triggered: 3 hearts to mobs within 2 blocks, no block damage"),
                Talent.node("netting", "Netting", 1, 3, 2, Material.COBWEB,
                        rank -> "Rooted mobs take +" + (10 * rank) + "% arrow damage"),
                Talent.node("survivalist", "Survivalist", 1, 3, 2, Material.BONE,
                        rank -> "Killing a rooted or slowed mob gives " + (5 * rank) + " extra Focus"),
                Talent.node("minefield", "Minefield", 1, 4, 2, Material.REDSTONE,
                        rank -> "Fleet Foot drops " + (rank + 1) + " snares in a line behind you"),
                Talent.node("hunters_instinct", "Hunter's Instinct", 1, 4, 1, Material.SPYGLASS,
                        rank -> "Mobs within 16 blocks that start targeting you glow (for you) for 3 s"),
                Talent.keystone("cage_shot", "Cage Shot", 1, dev.sirbepy.mc.classes.talent.Slot.TWO, Material.IRON_BARS,
                        "An arrow that roots every mob within 4 blocks of where it lands for 4 s. 45 s"),

                // Beastmaster: fight with a pack
                Talent.node("pack_bond", "Pack Bond", 2, 1, 3, Material.BONE,
                        rank -> "Your tamed wolves deal +" + (10 * rank) + "% damage to mobs"),
                Talent.node("thick_fur", "Thick Fur", 2, 1, 2, Material.WHITE_WOOL,
                        rank -> "Your tamed wolves take " + (15 * rank) + "% less damage from mobs"),
                Talent.node("call_of_the_wild", "Call of the Wild", 2, 2, 2, Material.WOLF_SPAWN_EGG,
                        rank -> "Fleet Foot also summons " + rank + " temporary wolves for 15 s"),
                Talent.node("fetch", "Fetch", 2, 2, 2, Material.BONE_MEAL,
                        rank -> "Your wolves' kills give you " + (rank == 1 ? 3 : 5) + " Focus"),
                Talent.node("mend_pet", "Mend Pet", 2, 2, 3, Material.GOLDEN_APPLE,
                        rank -> "When you heal, your nearest wolf heals " + rank + (rank == 1 ? " heart" : " hearts")),
                Talent.node("alpha_howl", "Alpha Howl", 2, 3, 1, Material.BELL,
                        rank -> "Volley also gives your wolves Strength I for 6 s"),
                Talent.node("feral", "Feral", 2, 3, 2, Material.PHANTOM_MEMBRANE,
                        rank -> "Your wolves' bites slow mobs for " + (rank + 1) + " s"),
                Talent.node("pack_tactics", "Pack Tactics", 2, 3, 2, Material.ARROW,
                        rank -> "+" + (5 * rank) + "% arrow damage to a mob your wolf is attacking"),
                Talent.node("loyal", "Loyal", 2, 4, 1, Material.TOTEM_OF_UNDYING,
                        rank -> "A wolf of yours that would die teleports to you with 2 hearts instead (once per 60 s per wolf)"),
                Talent.node("wild_growth", "Wild Growth", 2, 4, 2, Material.BONE_BLOCK,
                        rank -> "Temporary wolves last " + (rank == 1 ? 20 : 25) + " s and are " + (20 * rank) + "% bigger"),
                Talent.keystone("spirit_bear", "Spirit Bear", 2, dev.sirbepy.mc.classes.talent.Slot.TWO, Material.POLAR_BEAR_SPAWN_EGG,
                        "Summon a polar bear that fights mobs for you for 20 s. 60 s")
        );
    }

    @Override
    public Ability secondAbility() {
        return arrows.volley();
    }

    @Override
    public Ability ultimate() {
        return shots.arrowRain();
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "railshot" -> shots.railshot();
            case "cage_shot" -> snares.cageShot();
            case "spirit_bear" -> pack.spiritBear();
            default -> throw new IllegalArgumentException("Ranger has no keystone ability for " + keystone.id());
        };
    }

    @Override
    public void enable() {
        arrows.enable();
        kills.enable();
        snares.enable();
        pack.enable();
    }
}
