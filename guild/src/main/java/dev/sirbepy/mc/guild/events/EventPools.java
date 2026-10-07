package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/** The server-wide bounties and Grand Quests the Guild draws from. Secret pools: docs/guild.md. */
public final class EventPools {

    /** One server-wide bounty; the first eligible adventurer to finish and claim it wins it. */
    public record Bounty(String id, String title, Material icon, Rank minRank, Objective objective, int coins, int reputation) {
    }

    /** A week-long goal for the whole server; everyone who pulls their weight shares the reward. */
    public record GrandQuest(String id, String title, Material icon, Kind kind, Set<String> targets, int goal) {
        public Objective objective() {
            return new Objective(kind, targets, goal, title);
        }
    }

    static final Set<String> ORES = Set.of("COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
            "IRON_ORE", "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "NETHER_GOLD_ORE", "REDSTONE_ORE",
            "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
            "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "NETHER_QUARTZ_ORE", "ANCIENT_DEBRIS");

    public static final List<Bounty> BOUNTIES = List.of(
            bounty("bn_evoker", "Wanted: the Evoker of the Dark Woods", Material.TOTEM_OF_UNDYING, Rank.C, Kind.HUNT, Set.of("EVOKER"), 1, 220, 40),
            bounty("bn_wither_captains", "Wanted: Wither Skeleton captains", Material.WITHER_SKELETON_SKULL, Rank.C, Kind.HUNT, Set.of("WITHER_SKELETON"), 6, 200, 35),
            bounty("bn_ravager", "Wanted: the Ravager", Material.SADDLE, Rank.B, Kind.HUNT, Set.of("RAVAGER"), 1, 250, 40),
            bounty("bn_ghasts", "Wanted: three Ghasts over the lava sea", Material.GHAST_TEAR, Rank.C, Kind.HUNT, Set.of("GHAST"), 3, 200, 35),
            bounty("bn_breezes", "Wanted: Breezes loose in the Chambers", Material.BREEZE_ROD, Rank.C, Kind.HUNT, Set.of("BREEZE"), 5, 220, 35),
            bounty("bn_brutes", "Wanted: Piglin Brute warlords", Material.GOLDEN_AXE, Rank.B, Kind.HUNT, Set.of("PIGLIN_BRUTE"), 3, 240, 40),
            bounty("bn_sentries", "Wanted: the Reactor's sentries", Material.REDSTONE_LAMP, Rank.B, Kind.NAMED_HUNT, Set.of("incendium.mob.reactor.sentry.name"), 2, 260, 45),
            bounty("bn_elder", "Wanted: an Elder Guardian", Material.SPONGE, Rank.A, Kind.BOSS, Set.of("ELDER_GUARDIAN"), 1, 300, 50),
            bounty("bn_coven", "Wanted: a witches' coven", Material.GLASS_BOTTLE, Rank.C, Kind.HUNT, Set.of("WITCH"), 4, 180, 30),
            bounty("bn_phantoms", "Wanted: the phantom flock", Material.PHANTOM_MEMBRANE, Rank.C, Kind.HUNT, Set.of("PHANTOM"), 6, 200, 35),
            bounty("bn_inquisitors", "Wanted: Sanctum Inquisitors", Material.SOUL_LANTERN, Rank.B, Kind.NAMED_HUNT, Set.of("incendium.mob.sanctum.inquisitor.name"), 2, 260, 45),
            bounty("bn_drowned", "Wanted: the drowned of the shipwreck coast", Material.TRIDENT, Rank.C, Kind.HUNT, Set.of("DROWNED"), 15, 180, 30));

    public static final List<GrandQuest> GRAND_QUESTS = List.of(
            new GrandQuest("gq_hunt", "The Great Hunt: defeat hostile mobs", Material.IRON_SWORD, Kind.HUNT, Set.of("#HOSTILE"), 1000),
            new GrandQuest("gq_harvest", "The Great Harvest: harvest ripe crops", Material.HAY_BLOCK, Kind.HARVEST, Set.of(), 2000),
            new GrandQuest("gq_ore", "The Ore Rush: mine ore", Material.IRON_PICKAXE, Kind.MINE, ORES, 800),
            new GrandQuest("gq_catch", "The Long Catch: catch anything while fishing", Material.FISHING_ROD, Kind.FISH, Set.of(), 300),
            new GrandQuest("gq_herd", "The Great Herd: breed animals", Material.WHEAT, Kind.BREED, Set.of(), 250));

    private EventPools() {
    }

    public static Bounty bounty(String id) {
        return BOUNTIES.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    }

    public static GrandQuest grandQuest(String id) {
        return GRAND_QUESTS.stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);
    }

    private static Bounty bounty(String id, String title, Material icon, Rank minRank, Kind kind, Set<String> targets,
                                 int amount, int coins, int reputation) {
        return new Bounty(id, title, icon, minRank, new Objective(kind, targets, amount, title), coins, reputation);
    }
}
