package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.grant.FakeBoltAPI;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.CopperGolemMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.popcraft.bolt.protection.BlockProtection;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopperGolemAccessListenerTest {

    private ServerMock server;
    private WorldMock world;
    private FakeBoltAPI api;
    private CopperGolemAccessListener listener;
    private Block lockedChest;
    private Block unlockedChest;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        api = new FakeBoltAPI();
        listener = new CopperGolemAccessListener(new BoltAccess(api));
        lockedChest = world.getBlockAt(0, 64, 0);
        unlockedChest = world.getBlockAt(5, 64, 0);
        api.protections.add(new BlockProtection(UUID.randomUUID(), UUID.randomUUID(), "private", 0, 0,
                new HashMap<>(), world.getName(), lockedChest.getX(), lockedChest.getY(), lockedChest.getZ(), "CHEST"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Entity golem() {
        CopperGolemMock golem = new CopperGolemMock(server, UUID.randomUUID());
        golem.setLocation(new Location(world, 0, 64, 1));
        return golem;
    }

    private ItemTransportingEntityValidateTargetEvent deniedEvent(Entity entity, Block block) {
        ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(entity, block);
        event.setAllowed(false);
        return event;
    }

    @Test
    void golemDeniedByBoltOnALockedChestIsAllowed() {
        ItemTransportingEntityValidateTargetEvent event = deniedEvent(golem(), lockedChest);

        listener.onValidateTarget(event);

        assertTrue(event.isAllowed());
    }

    @Test
    void golemRefusedOnAnUnprotectedBlockStaysRefused() {
        ItemTransportingEntityValidateTargetEvent event = deniedEvent(golem(), unlockedChest);

        listener.onValidateTarget(event);

        assertFalse(event.isAllowed());
    }

    @Test
    void otherEntitiesAreNotAffected() {
        Cow cow = world.spawn(new Location(world, 0, 64, 1), Cow.class);
        ItemTransportingEntityValidateTargetEvent event = deniedEvent(cow, lockedChest);

        listener.onValidateTarget(event);

        assertFalse(event.isAllowed());
    }
}
