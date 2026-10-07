package dev.sirbepy.mc.guild.staff;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.GuildMenus;
import dev.sirbepy.mc.guild.data.Place;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffTest extends GuildTestBase {

    private StaffNpcs npcs;
    private StaffListener listener;
    private Dialogue dialogue;

    @BeforeEach
    void setUpStaff() {
        npcs = new StaffNpcs(MockBukkit.createMockPlugin(), store);
        npcs.setTitleLook(display -> { });
        dialogue = new Dialogue(guild, new Random(7));
        listener = new StaffListener(npcs, guild, new GuildMenus(guild), dialogue);
        world.getChunkAt(0, 0);
    }

    private static Place spot(Staff staff) {
        return new Place("world", 2.5 + staff.ordinal() * 2, 64, 3.5, 180);
    }

    private void placeAll() {
        for (Staff staff : Staff.values()) {
            npcs.place(staff, spot(staff));
        }
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private String lastLine(org.mockbukkit.mockbukkit.entity.PlayerMock player) {
        String last = null;
        for (var m = player.nextComponentMessage(); m != null; m = player.nextComponentMessage()) {
            last = plain(m);
        }
        return last;
    }

    @Test
    void placingTheStaffPutsEachAtTheirSpot() {
        placeAll();
        for (Staff staff : Staff.values()) {
            Villager villager = npcs.villager(staff);
            assertNotNull(villager, staff + " spawned");
            assertTrue(villager.getLocation().distance(spot(staff).toLocation()) < 0.5, staff + " at their spot");
            assertEquals(staff, npcs.staffOf(villager));
            assertTrue(villager.isInvulnerable());
        }
    }

    @Test
    void aKilledStaffMemberComesBackOnTheNextGuardTick() {
        placeAll();
        Villager lyra = npcs.villager(Staff.RECEPTIONIST);
        lyra.remove();
        assertNull(npcs.villager(Staff.RECEPTIONIST));
        npcs.ensure();
        assertNotNull(npcs.villager(Staff.RECEPTIONIST));
    }

    @Test
    void theStaffCannotBeHurt() {
        placeAll();
        Villager bram = npcs.villager(Staff.CLERK);
        EntityDamageEvent event = new EntityDamageEvent(bram, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 5);
        listener.onDamage(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void lyraTalksAndOpensRegistrationForAStranger() {
        placeAll();
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(alice, npcs.villager(Staff.RECEPTIONIST), EquipmentSlot.HAND);
        listener.onInteract(event);
        assertTrue(event.isCancelled(), "never the villager trade screen");
        String said = lastLine(alice);
        assertTrue(said != null && said.startsWith("Lyra: "), said);
        String title = plain(alice.getOpenInventory().title());
        assertTrue(title.contains("Adventurer's Guild"), title);
    }

    @Test
    void theFirstVisitGetsAnIntroductionThenEverydayLines() {
        Member member = join(alice);
        String first = dialogue.line(Staff.CLERK, alice, member);
        assertTrue(first.startsWith("Bram Ashgrove"), first);
        String second = dialogue.line(Staff.CLERK, alice, member);
        assertTrue(!second.startsWith("Bram Ashgrove"), second);
    }

    @Test
    void theExaminerNamesWhatIsStillNeededButNeverTheTrial() {
        Member member = join(alice);
        dialogue.line(Staff.EXAMINER, alice, member);
        member.setReputation(100);
        member.setContractsDone(2);
        String line = dialogue.line(Staff.EXAMINER, alice, member);
        assertTrue(line.contains("50 more reputation") && line.contains("4 more contracts"), line);
        member.setReputation(Rank.D.reputation());
        member.setContractsDone(Rank.D.contracts());
        String ready = dialogue.line(Staff.EXAMINER, alice, member);
        assertTrue(ready.contains("D-Rank trial is yours"), ready);
    }

    @Test
    void silasOpensTheOfficeOnlyForTheGuildmaster() {
        placeAll();
        join(gm);
        join(alice);
        listener.talk(alice, Staff.TREASURER);
        assertTrue(!plain(alice.getOpenInventory().title()).contains("Office"));
        listener.talk(gm, Staff.TREASURER);
        assertTrue(plain(gm.getOpenInventory().title()).contains("Guildmaster's Office"));
    }

    @Test
    void removingAStaffMemberForgetsTheirSpot() {
        placeAll();
        npcs.remove(Staff.ARCHIVIST);
        assertNull(npcs.villager(Staff.ARCHIVIST));
        npcs.ensure();
        assertNull(npcs.villager(Staff.ARCHIVIST), "not re-created once removed");
        assertTrue(!store.staffSpots().containsKey("archivist"));
    }
}
