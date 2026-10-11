package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.core.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Picking and leaving a class from the menu or a command, with the messages and Frail Body. */
public final class ClassPicker {

    private final Classes classes;
    private final ClassPassives passives;
    private final LongSupplier now;

    public ClassPicker(Classes classes, ClassPassives passives, LongSupplier now) {
        this.classes = classes;
        this.passives = passives;
        this.now = now;
    }

    public Classes.Choice pick(Player player, RpgClass c) {
        Classes.Choice choice = classes.choose(player.getUniqueId(), c);
        switch (choice) {
            case ALREADY -> player.sendMessage(Component.text("You already are " + c.withArticle() + ".", NamedTextColor.GRAY));
            case LOCKED -> player.sendMessage(Component.text("You can change class again in "
                    + Durations.format(Duration.ofMillis(classes.lockedUntil(player.getUniqueId(), c) - now.getAsLong()))
                    + ".", NamedTextColor.RED));
            case CHOSEN -> {
                passives.applyFrailBody(player);
                passives.applyHeavyPlate(player);
                int level = classes.level(player.getUniqueId());
                player.sendMessage(Component.text("You are now " + c.withArticle() + " (level " + level + ").", c.color()));
                player.sendMessage(Component.text(c.passiveLine(level) + ". " + c.abilityLine(level) + ". "
                        + c.weaknessLine() + ".", NamedTextColor.GRAY));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        }
        return choice;
    }

    /**
     * Picks the permanent secondary class (Sivra21's dual-classing wish, docs/dragonballs.md):
     * half the passive, the full weakness, no ability. Only players with the grant can call this,
     * and only once, ever.
     */
    public Classes.DualChoice pickSecondary(Player player, RpgClass c) {
        Classes.DualChoice choice = classes.chooseSecondary(player.getUniqueId(), c);
        switch (choice) {
            case NOT_GRANTED -> player.sendMessage(Component.text("You have no dual-classing grant.", NamedTextColor.GRAY));
            case ALREADY_SET -> player.sendMessage(Component.text("Your secondary class is already "
                    + classes.secondary(player.getUniqueId()).withArticle() + ", permanently.", NamedTextColor.GRAY));
            case SAME_AS_MAIN -> player.sendMessage(Component.text("Your secondary class can't be the same as your main one.", NamedTextColor.RED));
            case CHOSEN -> {
                passives.applyFrailBody(player);
                passives.applyHeavyPlate(player);
                player.sendMessage(Component.text("Your secondary class is now " + c.withArticle()
                        + ", permanently. It gives half its passive and the full weakness, no ability.", c.color()));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        }
        return choice;
    }

    public boolean leave(Player player) {
        RpgClass c = classes.active(player.getUniqueId());
        if (!classes.leave(player.getUniqueId())) {
            player.sendMessage(Component.text("You have no class.", NamedTextColor.GRAY));
            return false;
        }
        passives.applyFrailBody(player);
        passives.applyHeavyPlate(player);
        player.sendMessage(Component.text("You left the " + c.displayName() + " class. Your levels are kept; going back to "
                + c.displayName() + " is free any time.", NamedTextColor.GRAY));
        return true;
    }
}
