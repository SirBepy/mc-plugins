package dev.sirbepy.mc.merfolk.bed;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.UUID;

/**
 * One placed Kelp Bed: two water blocks in a row on the sea floor, foot then head in the
 * {@code facing} direction, and the merfolk it homes (null while free).
 */
public final class KelpBed {

    private final String id;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final BlockFace facing;
    private UUID resident;

    public KelpBed(String id, String world, int x, int y, int z, BlockFace facing, UUID resident) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.resident = resident;
    }

    public String id() {
        return id;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public BlockFace facing() {
        return facing;
    }

    public UUID resident() {
        return resident;
    }

    void resident(UUID resident) {
        this.resident = resident;
    }

    /** Block keys of the foot and the head cell. */
    String[] cells() {
        return new String[] {cell(world, x, y, z), cell(world, x + facing.getModX(), y, z + facing.getModZ())};
    }

    /** The middle of the bed at floor level, where merfolk walk to and babies appear. */
    public Location centre(World w) {
        return new Location(w, x + 0.5 + facing.getModX() * 0.5, y, z + 0.5 + facing.getModZ() * 0.5);
    }

    static String cell(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
