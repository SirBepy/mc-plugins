package dev.sirbepy.mc.endfight.tier;

import org.bukkit.Bukkit;

/** Runs a command exactly as the server console would. */
public final class ConsoleCommandSink implements CommandSink {

    @Override
    public void run(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
