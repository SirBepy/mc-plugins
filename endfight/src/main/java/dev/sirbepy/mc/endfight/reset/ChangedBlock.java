package dev.sirbepy.mc.endfight.reset;

/** One block the restore needs to put back: {@code targetBlockData} is the snapshot's own value
 * for (x,y,z), which already differs from what's there now. */
public record ChangedBlock(int x, int y, int z, String targetBlockData) {
}
