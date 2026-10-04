package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Location;
import org.bukkit.entity.Cow;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EntityMock;
import org.mockbukkit.mockbukkit.entity.HopperMinecartMock;
import org.mockbukkit.mockbukkit.entity.RideableMinecartMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecartSpeedListenerTest {

    private static final double VANILLA = 0.4;

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit's world.spawn() and world.addEntity() don't support minecarts, so the mocks are
    // registered with the server directly.
    private <T extends EntityMock> T add(T entity) {
        entity.setLocation(new Location(world, 0, 64, 0));
        server.registerEntity(entity);
        return entity;
    }

    @Test
    void minecartEnteringTheWorldGetsDoubleSpeed() {
        RideableMinecart cart = add(new RideableMinecartMock(server, UUID.randomUUID()));
        cart.setMaxSpeed(VANILLA);

        new MinecartSpeedListener().onEntityAddToWorld(new EntityAddToWorldEvent(cart, world));

        assertEquals(MinecartSpeedListener.MAX_SPEED, cart.getMaxSpeed());
    }

    @Test
    void storageMinecartsGetDoubleSpeedToo() {
        HopperMinecart hopper = add(new HopperMinecartMock(server, UUID.randomUUID()));
        hopper.setMaxSpeed(VANILLA);

        new MinecartSpeedListener().onEntityAddToWorld(new EntityAddToWorldEvent(hopper, world));

        assertEquals(MinecartSpeedListener.MAX_SPEED, hopper.getMaxSpeed());
    }

    @Test
    void minecartsAlreadyLoadedGetDoubleSpeed() {
        RideableMinecart cart = add(new RideableMinecartMock(server, UUID.randomUUID()));
        cart.setMaxSpeed(VANILLA);

        MinecartSpeedListener.applyToLoaded(server.getWorlds());

        assertEquals(MinecartSpeedListener.MAX_SPEED, cart.getMaxSpeed());
    }

    @Test
    void nonMinecartEntitiesAreIgnored() {
        Cow cow = world.spawn(new Location(world, 0, 64, 0), Cow.class);

        assertDoesNotThrow(() -> new MinecartSpeedListener().onEntityAddToWorld(new EntityAddToWorldEvent(cow, world)));
    }
}
