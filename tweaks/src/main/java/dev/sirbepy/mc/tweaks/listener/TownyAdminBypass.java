package dev.sirbepy.mc.tweaks.listener;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.resident.mode.ResidentModeHandler;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/** Only loaded when Towny is enabled; the class references Towny types directly. */
public final class TownyAdminBypass implements TownyAdminOffListener.BypassMode {

    private static final String MODE = "adminbypass";

    private final Logger logger;

    public TownyAdminBypass(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean isOn(Player player) {
        return Towny.getPlugin().hasPlayerMode(player, MODE);
    }

    @Override
    public void turnOn(Player player) {
        try {
            // Same call as /resident toggle adminbypass; notify=true tells the player "Modes set: adminbypass".
            ResidentModeHandler.toggleMode(player, MODE, true);
        } catch (TownyException e) {
            logger.warning("Couldn't turn on Towny's adminbypass mode for " + player.getName() + ": " + e.getMessage());
        }
    }
}
