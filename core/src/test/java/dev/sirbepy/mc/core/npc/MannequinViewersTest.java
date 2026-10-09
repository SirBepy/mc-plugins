package dev.sirbepy.mc.core.npc;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MannequinViewersTest {

    private ServerMock server;
    private PlayerMock java;
    private PlayerMock pocket;
    private final List<String> changed = new ArrayList<>();
    private MannequinViewers viewers;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        java = server.addPlayer("Steve");
        pocket = server.addPlayer(".Pocket");
        UUID bedrock = pocket.getUniqueId();
        viewers = new MannequinViewers(bedrock::equals, player -> changed.add(player.getName()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bedrockPlayersNeverSeeTheMannequin() {
        assertTrue(viewers.seesMannequin(java, false));
        assertTrue(viewers.seesMannequin(java, true), "unknown pack status counts as loaded");
        assertFalse(viewers.seesMannequin(pocket, false));
        assertFalse(viewers.seesMannequin(pocket, true));
    }

    @Test
    void aFailedPackHidesOnlyPackSkins() {
        viewers.onPackStatus(new PlayerResourcePackStatusEvent(java, UUID.randomUUID(),
                PlayerResourcePackStatusEvent.Status.DECLINED));
        assertFalse(viewers.seesMannequin(java, true));
        assertTrue(viewers.seesMannequin(java, false), "a Mojang skin needs no pack");
        viewers.onPackStatus(new PlayerResourcePackStatusEvent(java, UUID.randomUUID(),
                PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        assertTrue(viewers.seesMannequin(java, true));
        assertEquals(List.of("Steve", "Steve"), changed, "each change updates that player's view");
    }

    @Test
    void packSkinsNeedThePack() {
        assertTrue(NpcSkin.pack("claudesmp:entity/haru", "", false).needsPack());
        assertTrue(NpcSkin.pack("", "claudesmp:entity/haru_cape", false).needsPack());
        assertFalse(new NpcSkin("abc123", "", "", false).needsPack());
        assertFalse(NpcSkin.NONE.needsPack());
    }

    @Test
    void snapBackMovesADriftedEntityButLeavesOneAlreadyThere() {
        WorldMock world = server.addSimpleWorld("world");
        Location home = new Location(world, 10, 64, 10);
        Villager entity = world.spawn(home, Villager.class);

        entity.teleport(new Location(world, 30, 64, 30));
        MannequinViewers.snapBack(entity, home);
        assertEquals(0, entity.getLocation().distance(home), 0.01, "drifted past half a block snaps back");

        Location nearby = new Location(world, 10.3, 64, 10);
        entity.teleport(nearby);
        MannequinViewers.snapBack(entity, home);
        assertEquals(nearby.getX(), entity.getLocation().getX(), 0.001, "within half a block is left alone");
    }
}
