package dev.sirbepy.mc.tag.dragon;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
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

/**
 * Proves {@link DragonFightCheck} reads the exact PDC tally endfight's own
 * {@code DragonFighters} already keeps on the live dragon (key {@code endfight:dragon-fighters}),
 * rather than re-deriving fighter status some other way.
 */
class DragonFightCheckTest {

    private static final NamespacedKey ENDFIGHT_KEY = new NamespacedKey("endfight", "dragon-fighters");

    private ServerMock server;
    private WorldMock end;
    private PlayerMock alice;
    private PlayerMock bob;
    private DragonFightCheck check;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        check = new DragonFightCheck();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon spawnDragon() {
        return (EnderDragon) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    @Test
    void playerListedAsAFighterOnTheLiveDragonIsFighting() {
        EnderDragon dragon = spawnDragon();
        DragonFighters fighters = new DragonFighters(ENDFIGHT_KEY, System::currentTimeMillis);
        fighters.addFighter(dragon.getPersistentDataContainer(), alice.getUniqueId());

        assertTrue(check.isFighting(alice.getUniqueId()), "Alice hit the live dragon, so she is a fighter");
        assertFalse(check.isFighting(bob.getUniqueId()), "Bob never hit the dragon, so he is not a fighter");
    }

    @Test
    void noLiveDragonMeansNobodyIsFighting() {
        assertFalse(check.isFighting(alice.getUniqueId()), "no dragon is alive, so nobody can be fighting one");
    }

    @Test
    void aDeadDragonsOldFighterTallyNoLongerCounts() {
        EnderDragon dragon = spawnDragon();
        DragonFighters fighters = new DragonFighters(ENDFIGHT_KEY, System::currentTimeMillis);
        fighters.addFighter(dragon.getPersistentDataContainer(), alice.getUniqueId());

        dragon.setHealth(0);

        assertFalse(check.isFighting(alice.getUniqueId()), "a dead dragon's stale fighter tally must not keep the exemption alive");
    }
}
