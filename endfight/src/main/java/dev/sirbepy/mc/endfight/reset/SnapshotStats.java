package dev.sirbepy.mc.endfight.reset;

/** How a snapshot capture went: how many blocks it covered, the compressed file size, and how
 * long the capture (world read + GZIP write) took. */
public record SnapshotStats(long blockCount, long fileSizeBytes, long millis) {
}
