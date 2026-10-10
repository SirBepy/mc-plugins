package dev.sirbepy.mc.tweaks.seagifts;

import dev.sirbepy.mc.tweaks.TweaksTestMessages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaGiftsTest {

    private static final long HOUR = Duration.ofHours(1).toMillis();

    @TempDir
    Path dir;

    private ServerMock server;
    private PlayerMock owner;
    private PlayerMock stranger;
    private final Set<UUID> owners = new LinkedHashSet<>();
    private File file;
    private SeaGiftsStore store;
    private SeaGifts gifts;
    private long now = 1_000_000_000L;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        owner = server.addPlayer("ItsLogiks");
        stranger = server.addPlayer("Someone");
        file = dir.resolve("sea-gifts.yml").toFile();
        store = new SeaGiftsStore(file, Logger.getLogger("test"));
        owners.add(owner.getUniqueId());
        gifts = new SeaGifts(server, store, () -> owners, () -> now, TweaksTestMessages.jarDefaults());
        server.getPluginManager().registerEvents(gifts, MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private UUID id() {
        return owner.getUniqueId();
    }

    @Test
    void bothGiftsStartOnAndFreeToFlip() {
        for (SeaGift gift : SeaGift.values()) {
            assertTrue(store.isOn(id(), gift));
            assertEquals(Duration.ZERO, store.wait(id(), gift, now));
        }
    }

    @Test
    void theTickPutsGiftsThatAreOnOnTheOwnerOnly() {
        gifts.tick();
        PotionEffect dolphin = owner.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        assertNotNull(dolphin);
        assertEquals(PotionEffect.INFINITE_DURATION, dolphin.getDuration());
        assertEquals(0, dolphin.getAmplifier());
        assertTrue(owner.hasPotionEffect(PotionEffectType.CONDUIT_POWER));
        assertFalse(stranger.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));
        assertFalse(stranger.hasPotionEffect(PotionEffectType.CONDUIT_POWER));
    }

    @Test
    void eachGiftFlipsOnItsOwnAtMostOncePerHour() {
        gifts.tick();
        gifts.flip(owner, SeaGift.DOLPHIN);
        assertFalse(store.isOn(id(), SeaGift.DOLPHIN));
        assertFalse(owner.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));
        assertTrue(owner.hasPotionEffect(PotionEffectType.CONDUIT_POWER));

        now += HOUR - 60_000;
        gifts.flip(owner, SeaGift.DOLPHIN);
        assertFalse(store.isOn(id(), SeaGift.DOLPHIN), "refused inside the hour");
        String refusal = lastMessage();
        assertTrue(refusal.contains("1m"), refusal);

        gifts.flip(owner, SeaGift.CONDUIT);
        assertFalse(store.isOn(id(), SeaGift.CONDUIT), "the other gift has its own hour");

        now += 60_000;
        gifts.flip(owner, SeaGift.DOLPHIN);
        assertTrue(store.isOn(id(), SeaGift.DOLPHIN));
        assertTrue(owner.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));
    }

    @Test
    void everyOwnerHasTheGiftsWithHisOwnSwitchesAndHour() {
        PlayerMock second = server.addPlayer("SirBepy");
        owners.add(second.getUniqueId());
        gifts.tick();
        for (PlayerMock player : new PlayerMock[] {owner, second}) {
            assertTrue(player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE), player.getName());
            assertTrue(player.hasPotionEffect(PotionEffectType.CONDUIT_POWER), player.getName());
        }
        assertFalse(stranger.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));

        gifts.flip(owner, SeaGift.DOLPHIN);
        assertFalse(owner.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));
        assertTrue(second.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE), "his flip is his alone");
        assertTrue(store.isOn(second.getUniqueId(), SeaGift.DOLPHIN));

        now += 60_000;
        gifts.flip(second, SeaGift.DOLPHIN);
        assertFalse(store.isOn(second.getUniqueId(), SeaGift.DOLPHIN), "the first owner's hour doesn't hold him");
        assertFalse(second.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));

        second.setHealth(0);
        assertFalse(store.isOn(second.getUniqueId(), SeaGift.CONDUIT));
        assertTrue(store.isOn(id(), SeaGift.CONDUIT), "his death takes only his gifts");
    }

    @Test
    void flippingOffReadsExactlyAsTheOldHardcodedLineDid() {
        gifts.tick();
        gifts.flip(owner, SeaGift.DOLPHIN);
        String text = PlainTextComponentSerializer.plainText().serialize(owner.nextComponentMessage());
        assertEquals("Dolphin's Grace is switched off. You can call it back in 1h.", text);
    }

    @Test
    void aStrangerCannotFlip() {
        gifts.flip(stranger, SeaGift.DOLPHIN);
        assertTrue(store.isOn(id(), SeaGift.DOLPHIN));
        assertNull(stranger.nextComponentMessage());
    }

    @Test
    void deathSwitchesBothOffUntilHeCallsThemBackWithoutWaitingTheHour() {
        gifts.tick();
        gifts.flip(owner, SeaGift.CONDUIT);
        now += HOUR;
        gifts.flip(owner, SeaGift.CONDUIT);
        assertTrue(store.isOn(id(), SeaGift.CONDUIT));
        assertEquals(Duration.ofHours(1), store.wait(id(), SeaGift.CONDUIT, now), "just flipped back on");

        owner.setHealth(0);
        for (SeaGift gift : SeaGift.values()) assertFalse(store.isOn(id(), gift));

        owner.respawn();
        owner.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        owner.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        gifts.tick();
        assertFalse(owner.hasPotionEffect(PotionEffectType.CONDUIT_POWER), "stays off after death");

        gifts.flip(owner, SeaGift.CONDUIT);
        assertTrue(store.isOn(id(), SeaGift.CONDUIT), "death's switch-off doesn't cost him the hour");
        assertTrue(owner.hasPotionEffect(PotionEffectType.CONDUIT_POWER));
        gifts.flip(owner, SeaGift.CONDUIT);
        assertTrue(store.isOn(id(), SeaGift.CONDUIT), "calling it back starts the hour");
    }

    @Test
    void milkCannotClearAGiftThatIsOn() {
        gifts.tick();
        gifts.flip(owner, SeaGift.DOLPHIN);
        assertTrue(milk(owner, PotionEffectType.CONDUIT_POWER).isCancelled());
        assertFalse(milk(owner, PotionEffectType.DOLPHINS_GRACE).isCancelled(), "off gifts are milk's to clear");
        assertFalse(milk(owner, PotionEffectType.SPEED).isCancelled());
        assertFalse(milk(stranger, PotionEffectType.CONDUIT_POWER).isCancelled());
    }

    @Test
    void anyOtherRemovalIsUndoneByTheTick() {
        gifts.tick();
        owner.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        gifts.tick();
        assertTrue(owner.hasPotionEffect(PotionEffectType.CONDUIT_POWER));
    }

    @Test
    void switchesAndCooldownsSurviveARestart() {
        gifts.flip(owner, SeaGift.DOLPHIN);
        owner.setHealth(0);

        SeaGiftsStore reloaded = new SeaGiftsStore(file, Logger.getLogger("test"));
        assertFalse(reloaded.isOn(id(), SeaGift.DOLPHIN));
        assertFalse(reloaded.isOn(id(), SeaGift.CONDUIT));
        assertEquals(Duration.ZERO, reloaded.wait(id(), SeaGift.CONDUIT, now), "death-off survives");

        reloaded.flip(id(), SeaGift.CONDUIT, now);
        SeaGiftsStore again = new SeaGiftsStore(file, Logger.getLogger("test"));
        assertTrue(again.isOn(id(), SeaGift.CONDUIT));
        assertEquals(Duration.ofHours(1), again.wait(id(), SeaGift.CONDUIT, now));
    }

    private EntityPotionEffectEvent milk(PlayerMock player, PotionEffectType type) {
        EntityPotionEffectEvent event = new EntityPotionEffectEvent(player, new PotionEffect(type, -1, 0), null, null,
                EntityPotionEffectEvent.Cause.MILK, EntityPotionEffectEvent.Action.CLEARED, false);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private String lastMessage() {
        String last = null;
        String next;
        while ((next = owner.nextMessage()) != null) last = next;
        return last;
    }
}
