package dev.sirbepy.mc.endfight.reward;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The full-set "Dragon's Presence" aura (Joe, 2026-10-09): draws only for a player actually
 * wearing all 4 Dragonscale pieces (helmet swap allowed, see {@link DragonHeadHelm}), skipped for
 * spectators and invisible players, and the opt-out persists across a store reload - see
 * {@link DragonPresence} and {@link DragonPresenceStore}. */
class DragonPresenceTest {

    private ServerMock server;
    private PlayerMock player;
    private DragonPresenceStore store;
    private File presenceFile;
    private List<Particle> drawn;
    private DragonPresence presence;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir File tempDir) {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        presenceFile = new File(tempDir, "presence.yml");
        store = new DragonPresenceStore(presenceFile, Logger.getLogger("test"));
        drawn = new ArrayList<>();
        presence = new DragonPresence(store, new Random(0),
                (who, particle, at, count, sx, sy, sz) -> drawn.add(particle));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void fullSetWithDragonscaleHelmDraws() {
        dressFullSet(true);

        presence.tickAll(List.of(player));

        assertTrue(drawn.contains(Particle.DRAGON_BREATH));
    }

    @Test
    void fullSetWithDragonHeadHelmDraws() {
        dressFullSet(false);
        player.getInventory().setHelmet(DragonHeadHelm.build());

        presence.tickAll(List.of(player));

        assertTrue(drawn.contains(Particle.DRAGON_BREATH));
    }

    @Test
    void missingOnePieceDrawsNothing() {
        dressFullSet(true);
        player.getInventory().setBoots(null);

        presence.tickAll(List.of(player));

        assertTrue(drawn.isEmpty());
    }

    @Test
    void spectatorDrawsNothing() {
        dressFullSet(true);
        player.setGameMode(GameMode.SPECTATOR);

        presence.tickAll(List.of(player));

        assertTrue(drawn.isEmpty());
    }

    @Test
    void invisiblePlayerDrawsNothing() {
        dressFullSet(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1000, 0));

        presence.tickAll(List.of(player));

        assertTrue(drawn.isEmpty());
    }

    @Test
    void toggledOffDrawsNothingAndStaysOffAfterReloadingTheStoreFromDisk() {
        dressFullSet(true);

        boolean nowOn = presence.toggle(player);
        assertFalse(nowOn);
        presence.tickAll(List.of(player));
        assertTrue(drawn.isEmpty());

        DragonPresenceStore reloaded = new DragonPresenceStore(presenceFile, Logger.getLogger("test"));
        assertTrue(reloaded.isOff(player.getUniqueId()));
    }

    @Test
    void toggleBackOnWorksAgain() {
        dressFullSet(true);
        presence.toggle(player);

        boolean nowOn = presence.toggle(player);

        assertTrue(nowOn);
        presence.tickAll(List.of(player));
        assertTrue(drawn.contains(Particle.DRAGON_BREATH));
    }

    @Test
    void theWonAuraStillTicksAlongsidePresence() {
        dressFullSet(true);
        RewardProgressStore progress = new RewardProgressStore(new File(presenceFile.getParentFile(), "rewards.yml"),
                Logger.getLogger("test"));
        DragonAuraService auras = new DragonAuraService(progress);
        PlayerProgress current = progress.load(player.getUniqueId());
        progress.save(player.getUniqueId(), new PlayerProgress(current.armor(), EnumSet.of(DragonAura.ENDER),
                current.activeAura(), current.heads(), current.weeklyHoards()));
        auras.toggle(player, "Ender");

        auras.tickAll(List.of(player));
        presence.tickAll(List.of(player));

        assertEquals(DragonAura.ENDER, progress.load(player.getUniqueId()).activeAura());
        assertTrue(drawn.contains(Particle.DRAGON_BREATH));
    }

    private void dressFullSet(boolean dragonscaleHelm) {
        if (dragonscaleHelm) {
            player.getInventory().setHelmet(DragonscaleArmor.build(ArmorPiece.HELMET));
        }
        player.getInventory().setChestplate(DragonscaleArmor.build(ArmorPiece.CHESTPLATE));
        player.getInventory().setLeggings(DragonscaleArmor.build(ArmorPiece.LEGGINGS));
        player.getInventory().setBoots(DragonscaleArmor.build(ArmorPiece.BOOTS));
    }
}
