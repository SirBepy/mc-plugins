package dev.sirbepy.mc.endfight.reset;

/**
 * A read-only cube of blocks as their {@code BlockData} string (e.g. {@code "minecraft:obsidian"}
 * or a full state string with properties), abstracted from {@link org.bukkit.World} so the codec
 * and the restore diff can run against a fake grid in a unit test, not just a live server.
 */
public interface BlockDataLookup {

    String blockDataAt(int x, int y, int z);
}
