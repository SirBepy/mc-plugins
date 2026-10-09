package dev.sirbepy.mc.endfight.reset;

import org.bukkit.World;

/** The one method {@link IslandResetService} needs from {@link IslandSnapshotTaker} - split out
 * so "should I capture at all" stays testable without a real plugin/world. */
public interface SnapshotCapture {

    SnapshotStats capture(World end);
}
