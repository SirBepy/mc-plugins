package dev.sirbepy.mc.guild.time;

import dev.sirbepy.mc.guild.config.GuildConfig;

import java.time.LocalDateTime;
import java.util.function.Supplier;

public final class ZoneClock implements GuildClock {

    private final Supplier<GuildConfig> config;

    public ZoneClock(Supplier<GuildConfig> config) {
        this.config = config;
    }

    @Override
    public LocalDateTime localNow() {
        return LocalDateTime.now(config.get().zone());
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
