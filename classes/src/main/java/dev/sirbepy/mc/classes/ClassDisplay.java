package dev.sirbepy.mc.classes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * How players see their class without touching the action bar, which AuraSkills redraws every 5
 * ticks: the tab list footer (Java), a chat line on join, and a title on level-up. Bedrock players
 * get the chat lines and titles through Geyser.
 */
public final class ClassDisplay implements Classes.Feedback {

    private final Classes classes;

    public ClassDisplay(Classes classes) {
        this.classes = classes;
    }

    /** "Warrior · Level 7 (30/110 XP) · today 45/300 XP", or nothing with no class. */
    public Component footer(UUID player) {
        ClassProfile p = classes.profile(player);
        RpgClass c = p.active();
        if (c == null) return Component.empty();
        return Component.text(c.displayName() + " · ", c.color())
                .append(Component.text(progress(p, c) + " · today " + p.earnedToday() + "/" + classes.dailyCap() + " XP",
                        NamedTextColor.GRAY));
    }

    /** "Level 7 (30/110 XP)", or "Level 25 (max)". */
    static String progress(ClassProfile p, RpgClass c) {
        int level = p.level(c);
        if (level >= Levels.MAX) return "Level " + Levels.MAX + " (max)";
        return "Level " + level + " (" + (p.xp(c) - Levels.totalFor(level)) + "/" + Levels.toNext(level) + " XP)";
    }

    public void refresh(Player player) {
        player.sendPlayerListFooter(footer(player.getUniqueId()));
    }

    public void greet(Player player) {
        RpgClass c = classes.active(player.getUniqueId());
        if (c == null) return;
        player.sendMessage(Component.text("You are " + c.withArticle() + ", " + progress(classes.profile(player.getUniqueId()), c)
                + ". /class for your class, /class ability for " + c.abilityName() + ".", c.color()));
    }

    @Override
    public void levelUp(UUID id, RpgClass c, int level) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        player.showTitle(Title.title(Component.text(c.displayName() + " level " + level, c.color()),
                Component.text(c.passiveLine(level), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(750))));
        player.sendMessage(Component.text(c.displayName() + " level " + level + "! ", c.color())
                .append(Component.text(c.passiveLine(level) + "; " + c.abilityLine(level), NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    @Override
    public void dailyCapReached(UUID id, int cap) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.sendMessage(Component.text("You've earned today's " + cap + " class XP. More tomorrow.", NamedTextColor.GRAY));
        }
    }

    @Override
    public void changed(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) refresh(player);
    }
}
