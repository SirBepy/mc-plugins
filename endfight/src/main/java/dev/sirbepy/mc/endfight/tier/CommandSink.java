package dev.sirbepy.mc.endfight.tier;

/** Runs one console command, decoupled from Bukkit so {@link DkeTierPusher}'s command-building
 * logic is pure and testable with a recording fake. */
public interface CommandSink {

    void run(String command);
}
