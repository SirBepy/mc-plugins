package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.rep.Tier;
import org.bukkit.Material;

import java.util.Set;

/**
 * One kind of Spirit Request. {@code targets} are upper-case material or entity type names, or
 * lower-case namespaced biome keys; empty means anything counts (any fish, any breeding).
 */
public record QuestDef(String id, Type type, Set<String> targets, int amount, Difficulty difficulty, Tier minTier,
                       String text, Material icon) {

    public enum Type {
        /** Hand the items to Haru from the inventory. */
        DELIVER,
        KILL,
        /** Ores broken without Silk Touch. */
        MINE,
        /** Fully grown crops broken. */
        HARVEST,
        FISH,
        BREED,
        ENCHANT,
        TRADE,
        /** Stand in one of the biomes once. */
        BIOME
    }

    public enum Difficulty {
        EASY(4, 6), MEDIUM(8, 12), HARD(15, 23);

        private final int petals;
        private final int reputation;

        Difficulty(int petals, int reputation) {
            this.petals = petals;
            this.reputation = reputation;
        }

        /** Petals grow by 1 per tier, so higher trust pays better. */
        public int petals(Tier tier) {
            return petals + tier.ordinal();
        }

        public int reputation() {
            return reputation;
        }
    }

    public boolean matches(String target) {
        return targets.isEmpty() || targets.contains(target);
    }

    /** The material handed over for {@link Type#DELIVER} requests. */
    public Material deliverMaterial() {
        return Material.valueOf(targets.iterator().next());
    }
}
