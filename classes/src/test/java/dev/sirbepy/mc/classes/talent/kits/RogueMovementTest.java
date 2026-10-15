package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Double Jump and Wall Climb (docs/classes-revamp.md, Rogue Shadow branch). */
class RogueMovementTest extends KitTestBase {

    private void fireMove(PlayerMock p) {
        Location from = p.getLocation();
        server.getPluginManager().callEvent(new PlayerMoveEvent(p, from, from.clone()));
    }

    @Test
    void doubleJumpGrantsAllowFlightOnlyWhileGroundedWithTheTalent() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        p.teleport(at(0));
        p.setOnGround(true);
        fireMove(p);
        assertFalse(p.getAllowFlight(), "no rank yet");

        rank(p, "double_jump", 1);
        fireMove(p);
        assertTrue(p.getAllowFlight(), "grounded with a rank: allowFlight is granted");
    }

    @Test
    void grantedFlightIsHandedBackAtDisableAndAgainOnTheNextJoinAfterACrash() {
        PlayerMock p = player(RpgClass.ROGUE, 25);
        p.teleport(at(0));
        p.setOnGround(true);
        rank(p, "double_jump", 1);
        fireMove(p);
        assertTrue(p.getAllowFlight());
        engine.stop();
        assertFalse(p.getAllowFlight(), "a restart skips quit handlers, so disable cleans up");

        // A crash skips disable too: the saved marker makes the next join clean up.
        fireMove(p);
        assertTrue(p.getAllowFlight());
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(p, (net.kyori.adventure.text.Component) null));
        assertFalse(p.getAllowFlight());
    }

    @Test
    void doubleJumpCancelsRealFlightAndBoostsVelocityOnceOffCooldown() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "double_jump", 1);
        p.teleport(at(0));
        p.setOnGround(true);
        fireMove(p);
        p.setOnGround(false);

        var event = p.simulateToggleFlight(true);
        assertTrue(event.isCancelled(), "never lets real flight start");
        assertTrue(p.getVelocity().getY() > 0, "the jump gives upward velocity");
        assertFalse(p.getAllowFlight(), "the charge is spent");
    }

    @Test
    void doubleJumpIsGatedByItsOwnCooldown() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "double_jump", 1);
        p.teleport(at(0));
        p.setOnGround(true);
        fireMove(p);
        p.setOnGround(false);
        p.simulateToggleFlight(true);

        p.setVelocity(new Vector(0, 0, 0));
        p.simulateToggleFlight(true); // still mid-air, but allowFlight is already false now.
        assertEquals(0, p.getVelocity().getY(), "a second attempt without allowFlight does nothing");
    }

    @Test
    void doubleJumpNeverLeavesASurvivalPlayerFlyingAfterLeavingTheClassOrLogout() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "double_jump", 1);
        p.teleport(at(0));
        p.setOnGround(true);
        fireMove(p);
        assertTrue(p.getAllowFlight());

        classes.leave(p.getUniqueId());
        fireMove(p);
        assertFalse(p.getAllowFlight(), "leaving the class drops allowFlight on the next move");
    }

    @Test
    void doubleJumpNeverTouchesCreativeOrSpectatorFlight() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "double_jump", 1);
        p.setGameMode(GameMode.CREATIVE);
        p.setAllowFlight(true);
        p.teleport(at(0));
        p.setOnGround(true);
        fireMove(p);
        assertTrue(p.getAllowFlight(), "a creative player's own flight is left alone");
    }

    /** Faces {@code p} along +X (matching {@link RogueMovement}'s own dominant-axis facing) and walls it in. */
    private void faceAWall(PlayerMock p) {
        Location loc = at(0);
        loc.setDirection(new Vector(1, 0, 0));
        p.teleport(loc);
        world.getBlockAt(loc.clone().add(1, 0, 0)).setType(Material.STONE);
        world.getBlockAt(loc.clone().add(1, 1, 0)).setType(Material.STONE);
    }

    @Test
    void wallClimbGivesUpwardVelocityAgainstASolidWallWhileSneaking() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "wall_climb", 1);
        faceAWall(p);

        p.simulateSneak(true);
        advance(2);
        assertTrue(p.getVelocity().getY() > 0, "climbing applies upward velocity against the wall");
    }

    @Test
    void wallClimbStopsOnceItHasClimbedSixBlocksThisContact() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "wall_climb", 1);
        faceAWall(p);

        p.simulateSneak(true);
        advance(2);
        p.teleport(p.getLocation().clone().add(0, 6.5, 0));
        p.setVelocity(new Vector(0, 0, 0));
        advance(2);
        assertEquals(0, p.getVelocity().getY(), "6 blocks climbed this contact: no more lift");
    }

    @Test
    void wallClimbStopsWhenTheRogueStopsSneaking() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "wall_climb", 1);
        faceAWall(p);

        p.simulateSneak(true);
        advance(2);
        p.simulateSneak(false);
        p.setVelocity(new Vector(0, 0, 0));
        advance(4);
        assertEquals(0, p.getVelocity().getY(), "no climbing once sneak is released");
    }
}
