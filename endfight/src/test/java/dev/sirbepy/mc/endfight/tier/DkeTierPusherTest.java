package dev.sirbepy.mc.endfight.tier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DkeTierPusher#commandsFor} is pure (no server needed); {@link DkeTierPusher#apply}'s
 * DKE-present/absent branches are each covered against a real MockBukkit dragon. */
class DkeTierPusherTest {

    private static final Logger LOGGER = Logger.getLogger("dke-pusher-test");

    private ServerMock server;
    private WorldMock end;

    private static final class RecordingSink implements CommandSink {
        final List<String> ran = new ArrayList<>();

        @Override
        public void run(String command) {
            ran.add(command);
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon() {
        return (EnderDragon) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    @Test
    void commandsForResetsTheTypeTagBeforeReRunningTheMatchingBuffFunction() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

        List<String> commands = DkeTierPusher.commandsFor(uuid, Tier.FEROCIOUS);

        assertEquals(List.of(
                "execute as 11111111-2222-3333-4444-555555555555 run function dke:dragons/reset_tag",
                "execute as 11111111-2222-3333-4444-555555555555 run function dke:dragons/dragon_buff_ferocious"
        ), commands);
    }

    @Test
    void everyTierMapsToTheExactDkeFunctionName() {
        assertEquals("easy", DkeTierPusher.dkeName(Tier.FLEDGELING));
        assertEquals("medium", DkeTierPusher.dkeName(Tier.POWERFUL));
        assertEquals("hard", DkeTierPusher.dkeName(Tier.CRUEL));
        assertEquals("immortal", DkeTierPusher.dkeName(Tier.IMMORTAL));
        assertEquals("ferocious", DkeTierPusher.dkeName(Tier.FEROCIOUS));
        assertEquals("primordial", DkeTierPusher.dkeName(Tier.PRIMORDIAL));
        assertEquals("ascended", DkeTierPusher.dkeName(Tier.ASCENDED));
    }

    @Test
    void whenDkeIsInstalledItRunsTheDkeCommandsInsteadOfTouchingHealthDirectly() {
        EnderDragon dragon = dragon();
        double vanillaMax = dragon.getMaxHealth();
        RecordingSink sink = new RecordingSink();
        DkeTierPusher pusher = new DkeTierPusher(() -> true, sink, LOGGER);

        pusher.apply(dragon, Tier.CRUEL);

        assertEquals(DkeTierPusher.commandsFor(dragon.getUniqueId(), Tier.CRUEL), sink.ran);
        assertEquals(vanillaMax, dragon.getMaxHealth(), "a console command can't run for real in this test, so health is untouched");
    }

    @Test
    void whenDkeIsAbsentItSetsTheDragonsHealthDirectlyToTheTiersHealth() {
        EnderDragon dragon = dragon();
        RecordingSink sink = new RecordingSink();
        DkeTierPusher pusher = new DkeTierPusher(() -> false, sink, LOGGER);

        pusher.apply(dragon, Tier.IMMORTAL);

        assertTrue(sink.ran.isEmpty(), "no DKE commands should run when it isn't installed");
        assertEquals(Tier.IMMORTAL.health(), dragon.getMaxHealth());
        assertEquals(Tier.IMMORTAL.health(), dragon.getHealth());
    }
}
