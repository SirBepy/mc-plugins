package dev.sirbepy.mc.tweaks;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerArgumentTest {

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

    @Test
    void onlyTheOwningPlayerIsTheOwner() {
        PlayerMock owner = server.addPlayer("Owner");
        PlayerMock other = server.addPlayer("Other");

        assertTrue(OwnerArgument.isOwner(owner, owner.getUniqueId()));
        assertFalse(OwnerArgument.isOwner(other, owner.getUniqueId()));
        assertFalse(OwnerArgument.isOwner(owner, null));
    }

    @Test
    void aMobIsNeverTheOwnerEvenWithTheOwnersId() {
        var zombie = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        assertFalse(OwnerArgument.isOwner(zombie, zombie.getUniqueId()));
    }
}
