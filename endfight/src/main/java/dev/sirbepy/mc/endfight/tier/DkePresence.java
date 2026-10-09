package dev.sirbepy.mc.endfight.tier;

/** Whether the Dragonkind Evolved datapack is installed and enabled, so {@link DkeTierPusher}
 * can fall back to setting the dragon's health directly when it isn't. */
public interface DkePresence {

    boolean installed();
}
