package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.generator.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves config.yml's plain structure ids ("village_plains", "fortress", ...) to Paper's
 * {@link Structure} constants via {@link Registry#STRUCTURE}, so the structure list stays
 * swappable in config.yml without a rebuild. An id the registry doesn't recognize (a typo, or a
 * renamed vanilla id) is skipped rather than failing the whole scatter.
 */
public final class StructureCatalog {

    private StructureCatalog() {
    }

    public static List<Structure> resolve(List<String> ids) {
        List<Structure> structures = new ArrayList<>();
        for (String id : ids) {
            Structure structure = Registry.STRUCTURE.get(NamespacedKey.minecraft(id));
            if (structure != null) {
                structures.add(structure);
            }
        }
        return structures;
    }
}
