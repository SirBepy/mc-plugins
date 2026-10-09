package dev.sirbepy.mc.merfolk;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantTest {

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

    private Villager villager() {
        return world.spawn(new Location(world, 0, 60, 0), Villager.class);
    }

    private static NamespacedKey model(Villager villager) {
        return villager.getEquipment().getHelmet().getItemMeta().getItemModel();
    }

    @Test
    void theVariantIsFixedByTheUuidAndBothLooksTurnUp() {
        UUID id = UUID.randomUUID();
        assertEquals(Merfolk.Variant.of(id), Merfolk.Variant.of(new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits())));
        Set<Merfolk.Variant> seen = EnumSet.noneOf(Merfolk.Variant.class);
        for (int i = 0; i < 64; i++) seen.add(Merfolk.Variant.of(UUID.randomUUID()));
        assertEquals(EnumSet.allOf(Merfolk.Variant.class), seen);
    }

    @Test
    void eachVariantWearsItsOwnTypeAndFins() {
        assertEquals(Villager.Type.SWAMP, Merfolk.Variant.KELP.type);
        assertEquals("claudesmp:merfolk_head", Merfolk.Variant.KELP.headModel.toString());
        assertEquals(Villager.Type.JUNGLE, Merfolk.Variant.CORAL.type);
        assertEquals("claudesmp:merfolk_head_coral", Merfolk.Variant.CORAL.headModel.toString());
    }

    @Test
    void makingAMerfolkDressesItInItsVariant() {
        for (int i = 0; i < 8; i++) {
            Villager villager = villager();
            Merfolk.make(villager);
            Merfolk.Variant variant = Merfolk.Variant.of(villager.getUniqueId());
            assertEquals(variant.type, villager.getVillagerType());
            assertEquals(variant.headModel, model(villager));
        }
    }

    @Test
    void aMerfolkFromBeforeTheVariantsIsRedressedInItsOwn() {
        // Live's first merfolk were all swamp with the kelp fins; a Coral one must switch both.
        Villager villager;
        do {
            villager = villager();
        } while (Merfolk.Variant.of(villager.getUniqueId()) != Merfolk.Variant.CORAL);
        Merfolk.make(villager);
        villager.setVillagerType(Villager.Type.SWAMP);
        villager.getEquipment().setHelmet(Merfolk.head(Merfolk.Variant.KELP));

        Merfolk.dress(villager);

        assertEquals(Villager.Type.JUNGLE, villager.getVillagerType());
        assertEquals(Merfolk.Variant.CORAL.headModel, model(villager));
        assertTrue(Merfolk.isHead(villager.getEquipment().getHelmet()));
    }
}
