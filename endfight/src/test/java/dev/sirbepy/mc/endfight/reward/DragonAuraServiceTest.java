package dev.sirbepy.mc.endfight.reward;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /dragonaura}: only an owned aura can be worn, one at a time, and {@code off} always
 * clears it - see {@link DragonAuraService#toggle}. */
class DragonAuraServiceTest {

    private ServerMock server;
    private PlayerMock player;
    private RewardProgressStore progress;
    private DragonAuraService auras;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir File tempDir) {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        progress = new RewardProgressStore(new File(tempDir, "rewards.yml"), Logger.getLogger("test"));
        auras = new DragonAuraService(progress);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void togglingAnUnownedAuraFails() {
        DragonAuraService.ToggleOutcome outcome = auras.toggle(player, "Ender");

        assertEquals(DragonAuraService.ToggleResult.NOT_OWNED, outcome.result());
        assertNull(progress.load(player.getUniqueId()).activeAura());
    }

    @Test
    void togglingAnOwnedAuraWearsIt() {
        giveOwnedAura(DragonAura.ENDER);

        DragonAuraService.ToggleOutcome outcome = auras.toggle(player, "Ender");

        assertEquals(DragonAuraService.ToggleResult.SWITCHED, outcome.result());
        assertEquals(DragonAura.ENDER, progress.load(player.getUniqueId()).activeAura());
    }

    @Test
    void onlyOneAuraIsWornAtATime() {
        giveOwnedAura(DragonAura.ENDER);
        giveOwnedAura(DragonAura.VOID_EMBER);

        auras.toggle(player, "Ender");
        auras.toggle(player, "Void Ember");

        assertEquals(DragonAura.VOID_EMBER, progress.load(player.getUniqueId()).activeAura());
    }

    @Test
    void offAlwaysClearsTheWornAura() {
        giveOwnedAura(DragonAura.ENDER);
        auras.toggle(player, "Ender");

        DragonAuraService.ToggleOutcome outcome = auras.toggle(player, "off");

        assertEquals(DragonAuraService.ToggleResult.OFF, outcome.result());
        assertNull(progress.load(player.getUniqueId()).activeAura());
    }

    @Test
    void ownedAuraNamesListsOnlyWhatThePlayerHasUnlocked() {
        giveOwnedAura(DragonAura.DRAGONS_BREATH);

        List<String> owned = auras.ownedAuraNames(player);

        assertEquals(List.of("Dragon's Breath"), owned);
    }

    private void giveOwnedAura(DragonAura aura) {
        PlayerProgress current = progress.load(player.getUniqueId());
        EnumSet<DragonAura> owned = EnumSet.noneOf(DragonAura.class);
        owned.addAll(current.auras());
        owned.add(aura);
        PlayerProgress updated = new PlayerProgress(current.armor(), owned, current.activeAura(),
                current.heads(), current.weeklyHoards());
        progress.save(player.getUniqueId(), updated);
    }
}
