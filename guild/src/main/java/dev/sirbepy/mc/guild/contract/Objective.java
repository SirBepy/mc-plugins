package dev.sirbepy.mc.guild.contract;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Set;

/**
 * One thing a contract asks for. {@code targets} are upper-case material or entity type names,
 * namespaced biome or structure keys (a trailing {@code *} matches any key with that prefix), Incendium
 * translation keys for named mobs, or {@code #HOSTILE} for any hostile mob; empty means anything counts.
 */
public record Objective(Kind kind, Set<String> targets, int amount, String text) {

    public enum Kind {
        /** Kill, by entity type (killer only). */
        HUNT,
        /** Kill a mob carrying a datapack's custom name, matched by its translation key. */
        NAMED_HUNT,
        /** Kill a boss; everyone who hurt it in its last minute is credited. */
        BOSS,
        /** Ores broken without Silk Touch. */
        MINE,
        /** Items handed in when the contract is claimed (consumed). */
        GATHER,
        /** Fully grown crops broken. */
        HARVEST,
        /** Anything caught on a rod, or the caught item's material. */
        FISH,
        BREED,
        TAME,
        TRADE,
        ENCHANT,
        /** Items taken out of a furnace, smoker or blast furnace, by result material. */
        SMELT,
        /** Stand in one of the biomes once. */
        BIOME,
        /** Stand inside one of the structures once. */
        STRUCTURE
    }

    public Objective {
        targets = Set.copyOf(targets);
    }

    /** True when any of the event's facts (its type name, key, or tags like {@code #HOSTILE}) is wanted. */
    public boolean matches(Collection<String> facts) {
        if (targets.isEmpty()) {
            return true;
        }
        for (String fact : facts) {
            for (String target : targets) {
                if (target.endsWith("*") ? fact.startsWith(target.substring(0, target.length() - 1)) : fact.equals(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The material handed in for {@link Kind#GATHER}. */
    public Material gatherMaterial() {
        return Material.valueOf(targets.iterator().next());
    }

    /** Visiting objectives finish the moment they are seen once. */
    public boolean isVisit() {
        return kind == Kind.BIOME || kind == Kind.STRUCTURE;
    }
}
