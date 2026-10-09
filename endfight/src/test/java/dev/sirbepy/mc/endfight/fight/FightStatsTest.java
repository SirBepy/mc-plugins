package dev.sirbepy.mc.endfight.fight;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.endfight.cult.CultMarker;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Pillager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FightStatsTest {

    private ServerMock server;
    private WorldMock end;
    private FightStatsListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        listener = new FightStatsListener(new DragonFighters(new NamespacedKey("endfight", "dragon-fighters"), System::currentTimeMillis));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon(double health) {
        EnderDragon dragon = end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
        dragon.registerAttribute(Attribute.MAX_HEALTH);
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        dragon.setHealth(health);
        return dragon;
    }

    @SuppressWarnings("removal")
    private void hit(PlayerMock player, EnderDragon dragon, double damage) {
        listener.onHurt(new EntityDamageByEntityEvent(player, dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build(), damage));
    }

    @Test
    void damageAddsUpPerPlayerAndRanksMostFirst() {
        EnderDragon dragon = dragon(200);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        hit(alice, dragon, 10);
        hit(bob, dragon, 15);
        hit(alice, dragon, 7.5);

        PersistentDataContainer pdc = dragon.getPersistentDataContainer();
        assertEquals(17.5, FightStats.damage(pdc).get(alice.getUniqueId()), 1e-9);
        assertEquals(15, FightStats.damage(pdc).get(bob.getUniqueId()), 1e-9);
        assertEquals(List.of(alice.getUniqueId(), bob.getUniqueId()), FightStats.topDamagers(pdc, 6));
        assertEquals(List.of(alice.getUniqueId()), FightStats.topDamagers(pdc, 1));
    }

    @Test
    void theKillingBlowOnlyCountsTheHealthThatWasLeft() {
        EnderDragon dragon = dragon(4);
        PlayerMock alice = server.addPlayer("Alice");

        hit(alice, dragon, 50);

        assertEquals(4, FightStats.damage(dragon.getPersistentDataContainer()).get(alice.getUniqueId()), 1e-9);
    }

    @Test
    @SuppressWarnings("removal")
    void unblamedDamageCreditsNobody() {
        EnderDragon dragon = dragon(200);

        listener.onHurt(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.FALL, 10));

        assertTrue(FightStats.damage(dragon.getPersistentDataContainer()).isEmpty());
    }

    @Test
    void cultKillsAreTalliedPerTypeOnTheLivingDragon() {
        EnderDragon dragon = dragon(200);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        kill(alice, "pillager");
        kill(alice, "pillager");
        kill(bob, "pillager");
        kill(bob, "paratrooper");
        Pillager notCult = end.spawn(new Location(end, 5, 65, 5), Pillager.class);
        notCult.setKiller(alice);
        listener.onKill(new EntityDeathEvent(notCult, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), new ArrayList<>()));

        PersistentDataContainer pdc = dragon.getPersistentDataContainer();
        Map<String, Map<UUID, Integer>> kills = FightStats.kills(pdc);
        assertEquals(2, kills.get("pillager").get(alice.getUniqueId()));
        assertEquals(1, kills.get("pillager").get(bob.getUniqueId()));
        assertEquals(1, kills.get("paratrooper").get(bob.getUniqueId()));
        assertEquals(2, kills.size(), "the plain pillager counts for nothing");
        assertEquals(alice.getUniqueId(), FightStats.topKillers(pdc).get("pillager"));
        assertEquals(bob.getUniqueId(), FightStats.topKillers(pdc).get("paratrooper"));
    }

    @Test
    void aTieForTopKillerGoesToWhoeverGotThereFirst() {
        EnderDragon dragon = dragon(200);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        kill(bob, "phantom");
        kill(alice, "phantom");

        assertEquals(bob.getUniqueId(), FightStats.topKillers(dragon.getPersistentDataContainer()).get("phantom"));
    }

    @Test
    void handEditedJunkIsSkipped() {
        EnderDragon dragon = dragon(200);
        dragon.getPersistentDataContainer().set(FightStats.DAMAGE, org.bukkit.persistence.PersistentDataType.STRING,
                "not-a-uuid=5,=3," + UUID.randomUUID() + "=x");
        dragon.getPersistentDataContainer().set(FightStats.KILLS, org.bukkit.persistence.PersistentDataType.STRING,
                "pillager|nope=2,broken=1");

        assertTrue(FightStats.damage(dragon.getPersistentDataContainer()).isEmpty());
        assertTrue(FightStats.kills(dragon.getPersistentDataContainer()).isEmpty());
    }

    private void kill(PlayerMock killer, String type) {
        Pillager mob = end.spawn(new Location(end, 5, 65, 5), Pillager.class);
        CultMarker.mark(mob);
        CultMarker.setType(mob, type);
        mob.setKiller(killer);
        listener.onKill(new EntityDeathEvent(mob, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), new ArrayList<>()));
    }
}
