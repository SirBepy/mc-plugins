package dev.sirbepy.mc.carry;

import dev.sirbepy.mc.carry.guard.CarryGuard;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

public final class CarryTestSupport {

    private CarryTestSupport() {
    }

    /** The shipped config.yml, so tests exercise the real carryable list. */
    public static CarryConfig shippedConfig() {
        var stream = Objects.requireNonNull(CarryTestSupport.class.getResourceAsStream("/config.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        return new CarryConfig(yaml, Logger.getLogger("carry-test"));
    }

    /** A guard whose answers the test flips, standing in for Towny/Bolt. */
    public static final class FakeGuard implements CarryGuard {
        public String pickUpDenial;
        public String placeDenial;
        public Location lastPlaceCheck;

        @Override
        public String denyPickUp(Player player, Entity entity) {
            return pickUpDenial;
        }

        @Override
        public String denyPlace(Player player, Location location) {
            lastPlaceCheck = location;
            return placeDenial;
        }
    }
}
