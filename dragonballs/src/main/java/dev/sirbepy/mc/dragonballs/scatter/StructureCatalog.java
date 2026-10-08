package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.generator.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves config.yml's structure ids to Paper's {@link Structure} entries via
 * {@link Registry#STRUCTURE}, so the structure lists stay swappable in config.yml without a rebuild.
 * A bare id ("fortress") is a vanilla one; datapack structures carry their namespace
 * ("incendium:piglin_village"). An id the registry doesn't know (a typo, or a datapack that isn't
 * installed) is skipped rather than failing the whole scatter.
 */
public final class StructureCatalog {

    private StructureCatalog() {
    }

    public static List<Structure> resolve(List<String> ids) {
        List<Structure> structures = new ArrayList<>();
        for (String id : ids) {
            NamespacedKey key = NamespacedKey.fromString(id);
            Structure structure = key == null ? null : Registry.STRUCTURE.get(key);
            if (structure != null) {
                structures.add(structure);
            }
        }
        return structures;
    }
}
