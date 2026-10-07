package dev.sirbepy.mc.haru.time;

import dev.sirbepy.mc.haru.config.HaruConfig;
import org.bukkit.Server;
import org.bukkit.World;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

public final class ServerClock implements HaruClock {

    private final Server server;
    private final Supplier<HaruConfig> config;

    public ServerClock(Server server, Supplier<HaruConfig> config) {
        this.server = server;
        this.config = config;
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(config.get().zone());
    }

    @Override
    public LocalDateTime localNow() {
        return LocalDateTime.now(config.get().zone());
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long worldFullTime() {
        World world = server.getWorld(config.get().world());
        if (world == null && !server.getWorlds().isEmpty()) {
            world = server.getWorlds().getFirst();
        }
        return world == null ? 0 : world.getFullTime();
    }
}
