package dev.sirbepy.mc.guild.staff;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.GuildMenus;
import dev.sirbepy.mc.guild.data.Place;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffTest extends GuildTestBase {

    private StaffNpcs npcs;
    private StaffListener listener;
    private GuildMenus menus;
    private Dialogue dialogue;
    private final Set<UUID> bedrock = new HashSet<>();
    private final List<Staff> dressed = new ArrayList<>();
    private final Set<UUID> wiped = new HashSet<>();
    private org.bukkit.plugin.Plugin plugin;

    @BeforeEach
    void setUpStaff() {
        plugin = MockBukkit.createMockPlugin();
        npcs = new StaffNpcs(plugin, store, bedrock::contains);
        npcs.setLooks(display -> { }, (mannequin, staff) -> {
            dressed.add(staff);
            wiped.remove(mannequin.getUniqueId());
        }, (mannequin, staff) -> wiped.contains(mannequin.getUniqueId()));
        dialogue = new Dialogue(guild, new Random(7));
        menus = new GuildMenus(guild);
        listener = new StaffListener(npcs, guild, menus, dialogue);
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
        listener.talk(alice, Staff.TREASURER, alice.getLocation());
        assertTrue(!plain(alice.getOpenInventory().title()).contains("Office"));
        listener.talk(gm, Staff.TREASURER, gm.getLocation());
        assertTrue(plain(gm.getOpenInventory().title()).contains("Guildmaster's Office"));
    }

    @Test
    void javaPlayersSeeEachSkinnedMannequinAndBedrockPlayersTheVillager() {
        PlayerMock pocket = server.addPlayer(".Pocket");
        bedrock.add(pocket.getUniqueId());
        placeAll();
        for (Staff staff : Staff.values()) {
            Mannequin mannequin = npcs.mannequin(staff);
            assertNotNull(mannequin, staff + " has a Mannequin");
            assertEquals(staff, npcs.staffOf(mannequin));
            assertTrue(mannequin.isInvulnerable() && mannequin.isImmovable() && mannequin.isPersistent());
            assertTrue(mannequin.getLocation().distance(spot(staff).toLocation()) < 0.01, staff + " on the villager's spot");
            assertTrue(alice.canSee(mannequin) && !alice.canSee(npcs.villager(staff)), "Java: " + staff + "'s Mannequin");
            assertTrue(!pocket.canSee(mannequin) && pocket.canSee(npcs.villager(staff)), "Bedrock: " + staff + "'s villager");
        }
        assertEquals(List.of(Staff.values()), dressed, "each dressed in their own skin after spawning");
    }

    @Test
    void aJavaPlayerWhosePackFailedSeesTheVillagers() {
        placeAll();
        npcs.viewers().setPackFailed(alice, true);
        assertFalse(alice.canSee(npcs.mannequin(Staff.CLERK)));
        assertTrue(alice.canSee(npcs.villager(Staff.CLERK)));
        npcs.viewers().setPackFailed(alice, false);
        assertTrue(alice.canSee(npcs.mannequin(Staff.CLERK)));
    }

    @Test
    void rightClickingTheMannequinTalksAndOpensTheBoard() {
        placeAll();
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(alice, npcs.mannequin(Staff.RECEPTIONIST), EquipmentSlot.HAND);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        String said = lastLine(alice);
        assertTrue(said != null && said.startsWith("Lyra: "), said);
        assertTrue(plain(alice.getOpenInventory().title()).contains("Adventurer's Guild"));
    }

    @Test
    void talkingToBramPutsThePlayerAtTheDeskWhereTheBoardPays() {
        placeAll();
        Member member = join(alice);
        var daily = placeDaily(member, "e_zombies");
        guild.progress(alice, dev.sirbepy.mc.guild.contract.Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        alice.teleport(npcs.mannequin(Staff.CLERK).getLocation().add(0, 0, 2));
        assertFalse(menus.desk().at(alice), "/guild alone is not the desk");

        listener.onInteract(new PlayerInteractEntityEvent(alice, npcs.mannequin(Staff.CLERK), EquipmentSlot.HAND));
        assertTrue(menus.desk().at(alice));
        var board = (dev.sirbepy.mc.core.gui.Menu) alice.getOpenInventory().getTopInventory().getHolder();
        board.click(20, org.bukkit.event.inventory.ClickType.LEFT); // daily slot 1 (dailies sit in 19-23)

        assertTrue(daily.claimed());
    }

    @Test
    void staffPlacedBeforeTheMannequinsGetOneOnTheNextGuardTick() {
        placeAll();
        Villager bram = npcs.villager(Staff.CLERK);
        npcs.mannequin(Staff.CLERK).remove();
        store.staff().get("clerk")[2] = null;
        npcs.ensure();
        assertNotNull(npcs.mannequin(Staff.CLERK));
        assertEquals(bram, npcs.villager(Staff.CLERK), "the villager is kept");
    }

    @Test
    void aKilledMannequinComesBackAndAWipedSkinIsPutBackOn() {
        placeAll();
        Mannequin silas = npcs.mannequin(Staff.TREASURER);
        silas.remove();
        npcs.ensure();
        assertNotEquals(silas, npcs.mannequin(Staff.TREASURER));

        dressed.clear();
        wiped.add(npcs.mannequin(Staff.EXAMINER).getUniqueId());
        npcs.ensure();
        assertEquals(List.of(Staff.EXAMINER), dressed);
    }

    @Test
    void theMannequinIsRememberedAcrossARestart() {
        placeAll();
        UUID pip = npcs.mannequin(Staff.ARCHIVIST).getUniqueId();
        assertEquals(pip, newStore().staff().get("archivist")[2]);
    }

    @Test
    void aStrayCopyOfAMannequinIsRemoved() {
        placeAll();
        Mannequin copy = world.spawn(spot(Staff.CLERK).toLocation(), Mannequin.class);
        copy.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "staff"),
                org.bukkit.persistence.PersistentDataType.STRING, "clerk-java");
        assertEquals(Staff.CLERK, npcs.staffOf(copy));
        npcs.removeStrays(new org.bukkit.entity.Entity[] {copy, npcs.mannequin(Staff.CLERK)});
        assertFalse(copy.isValid());
        assertNotNull(npcs.mannequin(Staff.CLERK));
    }

    @Test
    void everyStaffSkinIsDrawnInTheServerPack() throws IOException {
        File textures = new File("../server/resourcepacks/claudesmp-items/assets/claudesmp/textures");
        for (Staff staff : Staff.values()) {
            assertTrue(staff.skin().needsPack(), staff + " is a pack skin");
            BufferedImage skin = ImageIO.read(new File(textures, path(staff.skin().packTexture())));
            assertNotNull(skin, staff + "'s skin is in the pack");
            assertEquals(64, skin.getWidth());
            assertEquals(64, skin.getHeight());
            if (!staff.skin().packCape().isBlank()) {
                BufferedImage cape = ImageIO.read(new File(textures, path(staff.skin().packCape())));
                assertNotNull(cape, staff + "'s cape is in the pack");
                assertEquals(64, cape.getWidth());
            }
        }
        assertEquals(Set.of(Staff.RECEPTIONIST, Staff.EXAMINER),
                Set.of(Staff.values()).stream().filter(s -> s.skin().slim()).collect(java.util.stream.Collectors.toSet()),
                "Lyra and Maren are drawn with slim arms");
    }

    /** {@code claudesmp:entity/guild/clerk} -> {@code entity/guild/clerk.png}, as the client resolves it. */
    private static String path(String key) {
        return key.substring(key.indexOf(':') + 1) + ".png";
    }

    @Test
    void removingAStaffMemberForgetsTheirSpot() {
        placeAll();
        Mannequin pip = npcs.mannequin(Staff.ARCHIVIST);
        npcs.remove(Staff.ARCHIVIST);
        assertNull(npcs.villager(Staff.ARCHIVIST));
        assertFalse(pip.isValid(), "the Mannequin goes too");
        npcs.ensure();
        assertNull(npcs.villager(Staff.ARCHIVIST), "not re-created once removed");
        assertTrue(!store.staffSpots().containsKey("archivist"));
    }
}
