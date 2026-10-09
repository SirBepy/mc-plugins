package dev.sirbepy.mc.endfight.altar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Opens whatever the "Auras" slot in the altar menu leads to. The rewards builder implements this
 * once auras are designed; {@link #NONE} tells the player there's nothing there yet instead of
 * silently doing nothing on click.
 */
public interface AuraMenu {

    void open(Player player);

    AuraMenu NONE = player -> player.sendMessage(Component.text("Auras aren't ready yet.", NamedTextColor.GRAY));
}
