package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.contract.ContractDef.Type;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Secret: the Promotion Trials, one per rank above E (docs/guild.md). A player sees a trial only once
 * they qualify for its rank, so no menu ever lists them ahead of time.
 */
public final class Trials {

    /** Days a started trial stays open before it lapses. */
    public static final int DAYS = 3;
    public static final int S_DAYS = 7;
    /** Hours after a lapse or failure before the trial can be taken again. */
    public static final int RETRY_HOURS = 24;

    public static final List<ContractDef> ALL = List.of(
            trial("trial_d", Rank.D, "Proof of Steel", Material.IRON_SWORD, true,
                    ContractPool.o(Kind.HUNT, Set.of("#HOSTILE"), 25, "Defeat 25 hostile mobs"),
                    ContractPool.o(Kind.GATHER, Set.of("IRON_SWORD"), 1, "Hand the examiner an Iron Sword")),
            trial("trial_c", Rank.C, "The Delve", Material.CHISELED_STONE_BRICKS, true,
                    ContractPool.o(Kind.STRUCTURE, ContractPool.CRYPTS, 1, "Descend into a crypt or a dungeon"),
                    ContractPool.o(Kind.MINE, Set.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"), 3, "Mine 3 Diamond Ore")),
            trial("trial_b", Rank.B, "Trial by Fire", Material.BLAZE_ROD, true,
                    ContractPool.o(Kind.HUNT, Set.of("BLAZE"), 8, "Defeat 8 Blazes"),
                    ContractPool.o(Kind.HUNT, Set.of("WITHER_SKELETON"), 3, "Defeat 3 Wither Skeletons"),
                    ContractPool.o(Kind.STRUCTURE, union(ContractPool.BASTIONS, Set.of("incendium:*")), 1,
                            "Stand inside a Bastion or any Incendium stronghold")),
            trial("trial_a", Rank.A, "The Drowned Throne", Material.PRISMARINE_CRYSTALS, false,
                    ContractPool.o(Kind.BOSS, Set.of("ELDER_GUARDIAN"), 1, "Defeat an Elder Guardian (shared with all who fight it)"),
                    ContractPool.o(Kind.STRUCTURE, Set.of("minecraft:monument"), 1, "Stand inside an Ocean Monument")),
            trial("trial_s", Rank.S, "The Legend", Material.NETHER_STAR, false,
                    ContractPool.o(Kind.BOSS, Set.of("WITHER"), 1, "Defeat the Wither (shared with all who fight it)"),
                    ContractPool.o(Kind.BOSS, Set.of("WARDEN"), 1, "Defeat a Warden (shared with all who fight it)"),
                    ContractPool.o(Kind.STRUCTURE, Set.of("incendium:forbidden_castle"), 1, "Stand inside the Forbidden Castle")));

    private Trials() {
    }

    /** The trial that promotes to {@code rank}, or null for E. */
    public static ContractDef forRank(Rank rank) {
        return ALL.stream().filter(t -> t.rank() == rank).findFirst().orElse(null);
    }

    public static int days(Rank rank) {
        return rank == Rank.S ? S_DAYS : DAYS;
    }

    private static ContractDef trial(String id, Rank rank, String title, Material icon, boolean noDeath, Objective... objectives) {
        return new ContractDef(id, Type.TRIAL, rank, 1.0, title, icon, List.of(objectives), noDeath);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }
}
