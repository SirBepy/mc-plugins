package dev.sirbepy.mc.tweaks;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void aUuidStringResolvesWithoutAnyLookup() {
        PlayerMock sender = server.addPlayer();
        UUID id = UUID.randomUUID();

        assertEquals(id, OwnerArgument.resolve(sender, id.toString(), TweaksTestMessages.jarDefaults()));
        assertNull(sender.nextComponentMessage());
    }

    @Test
    void aNameThatNeverJoinedTellsTheSenderWithTheConfiguredWording() {
        PlayerMock sender = server.addPlayer();

        UUID resolved = OwnerArgument.resolve(sender, "NoSuchPlayer", TweaksTestMessages.jarDefaults());

        assertNull(resolved);
        String text = PlainTextComponentSerializer.plainText().serialize(sender.nextComponentMessage());
        assertEquals("NoSuchPlayer has never joined this server.", text,
                "the configured wording must read exactly as the old hardcoded line did");
    }
}
