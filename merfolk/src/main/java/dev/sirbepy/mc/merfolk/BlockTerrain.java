package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.WaterPath.Cell;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/** A world's blocks as {@link WaterPath} cells. Unloaded chunks count as solid, so a search never loads one. */
public final class BlockTerrain implements WaterPath.Terrain {

    /** Plants that grow in water and have no collision: swum through like water. */
    static final Set<Material> SEA_PLANTS = EnumSet.of(Material.KELP, Material.KELP_PLANT, Material.SEAGRASS,
            Material.TALL_SEAGRASS);
    /** Passable but harmful or trapping, so never routed through. Bubble columns fling a merfolk up or down. */
    static final Set<Material> AVOID = EnumSet.of(Material.LAVA, Material.BUBBLE_COLUMN, Material.FIRE,
            Material.SOUL_FIRE, Material.POWDER_SNOW, Material.COBWEB, Material.SWEET_BERRY_BUSH);

    private final World world;
    private final Predicate<Block> passable;

    /** {@code passable}: whether a block has no collision ({@link Block#isPassable()} on a real server). */
    public BlockTerrain(World world, Predicate<Block> passable) {
        this.world = world;
        this.passable = passable;
    }

    @Override
    public Cell at(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return Cell.SOLID;
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == Material.WATER || SEA_PLANTS.contains(type)) return Cell.WATER;
        if (AVOID.contains(type)) return Cell.SOLID;
        if (type.isAir()) return Cell.OPEN;
        if (!passable.test(block)) return Cell.SOLID;
        return block.getBlockData() instanceof Waterlogged w && w.isWaterlogged() ? Cell.WATER : Cell.OPEN;
    }
}
