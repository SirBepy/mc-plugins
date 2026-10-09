package dev.sirbepy.mc.endfight.reset;

import org.bukkit.World;

/** The one method of {@link IslandRestorer} {@link RespawnWatcher} needs - split out so the
 * watcher's state machine can be unit-tested against a fake that doesn't touch Bukkit's
 * scheduler. */
public interface RestoreStarter {

    void start(World end, IslandSnapshot snapshot, Runnable onComplete);
}
