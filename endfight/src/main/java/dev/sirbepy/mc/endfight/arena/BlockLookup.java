package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;

/**
 * A read-only column of blocks, abstracted away from {@link org.bukkit.World} so
 * {@link PillarScanner} can run against a fake grid in a unit test, not just a live server.
 */
public interface BlockLookup {

    Material at(int x, int y, int z);
}
