package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaBodyTest {

    @TempDir
    File dir;

    private ServerMock server;
    private WorldMock world;
    private KelpBeds beds;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        beds = new KelpBeds(new File(dir, "beds.yml"), Logger.getAnonymousLogger());
        server.getPluginManager().registerEvents(new SeaBody(beds, v -> { }), MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Villager villager() {
        return world.spawn(new Location(world, 0, 60, 0), Villager.class);
    }

    private boolean airDrops(Villager villager) {
        villager.setRemainingAir(300);
        EntityAirChangeEvent event = new EntityAirChangeEvent(villager, 299);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private boolean drowns(Villager villager) {
        EntityDamageEvent event = new EntityDamageEvent(villager, EntityDamageEvent.DamageCause.DROWNING,
                DamageSource.builder(DamageType.DROWN).build(), 2);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Test
    void merfolkNeverRunOutOfAirOrDrownOrdinaryVillagersDo() {
        Villager merfolk = villager();
        Merfolk.make(merfolk);
        Villager ordinary = villager();

        assertFalse(airDrops(merfolk));
        assertFalse(drowns(merfolk));
        assertTrue(airDrops(ordinary));
        assertTrue(drowns(ordinary));
    }

    @Test
    void anOrdinarySwampOrJungleVillagerIsMovedToPlainsRobesSoItDoesntLookLikeAMerfolk() {
        Villager swamp = world.spawn(new Location(world, 0, 60, 0), Villager.class,
                v -> v.setVillagerType(Villager.Type.SWAMP));
        assertEquals(Villager.Type.PLAINS, swamp.getVillagerType());
        Villager jungle = world.spawn(new Location(world, 0, 60, 0), Villager.class,
                v -> v.setVillagerType(Villager.Type.JUNGLE));
        assertEquals(Villager.Type.PLAINS, jungle.getVillagerType());
        Villager snow = world.spawn(new Location(world, 0, 60, 0), Villager.class,
                v -> v.setVillagerType(Villager.Type.SNOW));
        assertEquals(Villager.Type.SNOW, snow.getVillagerType(), "village types are left alone");

        Villager npc = world.spawn(new Location(world, 0, 60, 0), Villager.class, v -> {
            v.setVillagerType(Villager.Type.SWAMP);
            v.setAI(false);
            v.setInvulnerable(true);
        });
        assertEquals(Villager.Type.SWAMP, npc.getVillagerType(), "a plugin NPC like the Guild's Archivist keeps its robes");
        // A merfolk keeping SWAMP is ConversionTest's job: adopting one touches the pathfinder, which
        // MockBukkit doesn't implement.
    }

    @Test
    void aZombifiedMerfolkFreesItsBedAndComesBackAMerfolkWhenCured() {
        Villager merfolk = villager();
        Merfolk.make(merfolk);
        beds.claim(beds.add(world.getBlockAt(5, 60, 5), BlockFace.NORTH), merfolk.getUniqueId());
        ZombieVillager zombie = (ZombieVillager) world.spawnEntity(merfolk.getLocation(), EntityType.ZOMBIE_VILLAGER);
        zombie.getEquipment().setHelmet(Merfolk.head(Merfolk.Variant.of(merfolk.getUniqueId())));

        server.getPluginManager().callEvent(new EntityTransformEvent(merfolk, List.of(zombie),
                EntityTransformEvent.TransformReason.INFECTION));
        assertNull(beds.homeOf(merfolk.getUniqueId()));
        assertFalse(Merfolk.isHead(zombie.getEquipment().getHelmet()));

        Villager cured = villager();
        server.getPluginManager().callEvent(new EntityTransformEvent(zombie, List.of(cured),
                EntityTransformEvent.TransformReason.CURED));
        assertTrue(Merfolk.is(cured));
        assertEquals(Merfolk.Variant.of(cured.getUniqueId()).type, cured.getVillagerType());
    }

    @Test
    void inWaterAMerfolkIsHeavyLighterWhileSwimmingOffTheFloorAndOnLandNeither() {
        // MockBukkit's villagers have no gravity attribute, so this checks the amounts weigh() applies.
        assertEquals(SeaBody.SINK_GRAVITY, SeaBody.extraGravity(true, false));
        assertEquals(SeaBody.HOVER_GRAVITY, SeaBody.extraGravity(true, true));
        assertEquals(0, SeaBody.extraGravity(false, true));
        assertEquals(0, SeaBody.extraGravity(false, false));
        // Between floating up (0.08 total) and pinned (1.0): a slow sink, just past neutral (0.512).
        double total = 0.08 + SeaBody.HOVER_GRAVITY;
        assertTrue(total > 0.512 && total < 0.7, "hover total " + total);
    }
}
