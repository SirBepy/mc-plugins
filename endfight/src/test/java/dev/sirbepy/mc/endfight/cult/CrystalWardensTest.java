package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaLayout;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import dev.sirbepy.mc.endfight.arena.PillarScanner;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Phantom;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalWardensTest {

    private static final int SURFACE_Y = 64;

    private ServerMock server;
    private WorldMock end;
    private List<Pillar> pillars;
    private CrystalWardens wardens;
    private final List<EnderCrystal> crystals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        pillars = new ArrayList<>();
        List<int[]> centers = PillarScanner.vanillaCenters();
        for (int i = 0; i < 10; i++) {
            pillars.add(new Pillar(centers.get(i)[0], centers.get(i)[1], 2 + i / 3, 76 + i * 3, SURFACE_Y));
        }
        ArenaRegistry registry = new ArenaRegistry();
        registry.update(pillars, ArenaLayout.build(pillars, SURFACE_Y), true);
        wardens = new CrystalWardens(MockBukkit.createMockPlugin(), registry, () -> new CultConfig(null), () -> false);
        for (int i = 0; i < 3; i++) {
            Pillar p = pillars.get(i);
            crystals.add(end.spawn(new Location(end, p.cx() + 0.5, p.topY() + 1, p.cz() + 0.5), EnderCrystal.class));
        }
        end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
        server.addPlayer().teleport(new Location(end, 10, SURFACE_Y + 1, 10));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<Phantom> wardensOf(int pillar) {
        return end.getEntitiesByClass(Phantom.class).stream()
                .filter(p -> !p.isDead() && Integer.valueOf(pillar).equals(
                        p.getPersistentDataContainer().get(CrystalWardens.WARDEN, PersistentDataType.INTEGER)))
                .toList();
    }

    @Test
    void everyStandingCrystalGetsTwoPhantomsCirclingAboveIt() {
        wardens.topUp();

        for (int i = 0; i < 3; i++) {
            List<Phantom> two = wardensOf(i);
            assertEquals(2, two.size(), "pillar " + i);
            for (Phantom phantom : two) {
                assertTrue(CultMarker.isCult(phantom));
                assertEquals("phantom", CultMarker.type(phantom));
                assertTrue(phantom.getLocation().getY() > pillars.get(i).topY(), "above the tower");
            }
        }
        assertEquals(0, wardensOf(5).size(), "no crystal, no wardens");
        assertEquals(0, CultRange.aliveWaveCount(end), "wardens never count against the wave cap");
    }

    @Test
    void aKilledWardenComesBackOnlyWhileItsCrystalStands() {
        wardens.topUp();
        wardensOf(0).get(0).remove();
        wardensOf(1).get(0).remove();
        crystals.get(1).remove();

        wardens.topUp();

        assertEquals(2, wardensOf(0).size(), "crystal standing: replaced");
        assertEquals(1, wardensOf(1).size(), "crystal broken: not replaced, the survivor stays");
        assertEquals(2, wardensOf(2).size());
    }

    @Test
    void theShortfallIsPerPillar() {
        assertEquals(Map.of(0, 2, 2, 1), CrystalWardens.shortfall(List.of(0, 1, 2), Map.of(1, 2, 2, 1), 2));
    }
}
