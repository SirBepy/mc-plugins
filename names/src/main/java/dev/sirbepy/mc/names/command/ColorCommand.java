package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.gui.ColorGui;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

/** {@code /color}: opens the color-picker chest GUI. Permission {@code names.color}. */
public final class ColorCommand implements BasicCommand {

    private final NamesConfig config;

    public ColorCommand(NamesConfig config) {
        this.config = config;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            config.messages().send(source.getSender(), "player-only");
            return;
        }
        if (!player.hasPermission("names.color")) {
            config.messages().send(player, "no-permission");
            return;
        }
        boolean canUseGradients = player.hasPermission("names.color.gradient");
        ColorGui gui = ColorGui.build(config, canUseGradients);
        player.openInventory(gui.getInventory());
    }
}
