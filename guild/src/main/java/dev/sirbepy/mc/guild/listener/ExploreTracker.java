package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Biome and structure visits, checked every couple of seconds for members who have one open, rather
 * than on every step. "Inside a structure" means inside one of its pieces' boxes, so a mineshaft's
 * huge outer box doesn't count from the surface above it.
 */
public final class ExploreTracker {

    private final Guild guild;

    public ExploreTracker(Guild guild) {
        this.guild = guild;
    }

    public void tick(Iterable<? extends Player> players) {
        for (Player player : players) {
            Member member = guild.store().member(player.getUniqueId());
            if (member == null) {
                continue;
            }
            boolean biome = wants(member, Kind.BIOME);
            boolean structure = wants(member, Kind.STRUCTURE);
            if (biome) {
                guild.progress(player, Kind.BIOME, Set.of(biomeKey(player.getLocation())), 1);
            }
            if (structure) {
                List<String> inside = structuresAt(player.getLocation());
                if (!inside.isEmpty()) {
                    guild.progress(player, Kind.STRUCTURE, inside, 1);
                }
            }
        }
    }

    static boolean wants(Member member, Kind kind) {
        for (ActiveContract active : member.openContracts()) {
            ContractDef def = active.def();
            if (def == null) {
                continue;
            }
            for (int i = 0; i < def.objectives().size(); i++) {
                Objective o = def.objectives().get(i);
                if (o.kind() == kind && active.progress(i) < o.amount()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String biomeKey(Location at) {
        return at.getWorld().getBiome(at).getKey().toString();
    }

    public static List<String> structuresAt(Location at) {
        World world = at.getWorld();
        List<String> keys = new ArrayList<>();
        for (GeneratedStructure generated : world.getStructures(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            if (!generated.getBoundingBox().clone().expand(1).contains(at.toVector())) {
                continue;
            }
            for (StructurePiece piece : generated.getPieces()) {
                if (piece.getBoundingBox().clone().expand(1).contains(at.toVector())) {
                    NamespacedKey key = key(generated.getStructure());
                    if (key != null) {
                        keys.add(key.toString());
                    }
                    break;
                }
            }
        }
        return keys;
    }

    /** Every structure touching the spot's chunk with its outer box, for {@code /guildadmin probe}. */
    public static List<String> structuresInChunk(Location at) {
        List<String> found = new ArrayList<>();
        for (GeneratedStructure generated : at.getWorld().getStructures(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            found.add(key(generated.getStructure()) + " " + generated.getBoundingBox() + " (" + generated.getPieces().size() + " pieces)");
        }
        return found;
    }

    private static NamespacedKey key(Structure structure) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE).getKey(structure);
    }
}
