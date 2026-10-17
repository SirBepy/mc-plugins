package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Combo.Click;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The spell bar for players without the tree mod's HUD: a boss bar (AuraSkills owns the action bar)
 * filled to the resource, always up for a player with a class unless they hid it ({@code /class hud}).
 * Its text is the resource and every slot's combo:
 * {@code Rage 40   [R-L-R] Battle Cry   [R-R-L] Charge (12s)   [R-L-L] Ground Slam (needs 100 Rage)},
 * or with the pack's ability icons ({@link AbilityIcons}) each name becomes its icon. While a combo
 * is in progress the text is the clicks so far instead: {@code R - L - ?}.
 */
public final class ResourceBar {

    private final Classes classes;
    private final Resources resources;
    private final Map<RpgClass, ClassKit> kits;
    private final AbilitySlots slots;
    private final ComboListener combos;
    private final Fx fx;
    private final AbilityIcons icons;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Players whose tree mod draws the HUD itself, so the boss bar stays off. Wired once the channel exists. */
    private Predicate<Player> drawnByMod = p -> false;

    public ResourceBar(Classes classes, Resources resources, Map<RpgClass, ClassKit> kits, AbilitySlots slots,
                       ComboListener combos, Fx fx, AbilityIcons icons) {
        this.classes = classes;
        this.resources = resources;
        this.kits = kits;
        this.slots = slots;
        this.combos = combos;
        this.fx = fx;
        this.icons = icons;
        resources.onFull(this::filled);
    }

    void setDrawnByMod(Predicate<Player> drawnByMod) {
        this.drawnByMod = drawnByMod;
    }

    /** Every 5 ticks per online player. */
    public void update(Player player) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        ClassKit kit = c == null ? null : kits.get(c);
        if (kit == null) {
            hide(player);
            return;
        }
        if (resources.isFull(id) && classes.level(id) >= Slot.ULTIMATE.unlockLevel()) {
            fx.ring(player.getLocation(), Particle.END_ROD, 0.8, 6);
        }
        if (!classes.profile(id).spellBar() || drawnByMod.test(player)) {
            hide(player);
            return;
        }
        BossBar bar = bars.computeIfAbsent(id, k -> BossBar.bossBar(Component.empty(), 0, kit.barColor(), BossBar.Overlay.PROGRESS));
        bar.color(kit.barColor());
        bar.progress((float) (resources.get(id) / Resources.MAX));
        List<Click> progress = combos.progress(player);
        bar.name(progress.isEmpty() ? legend(player, c, kit) : casting(progress, c));
        player.showBossBar(bar);
    }

    /** "R - L - ?" while a combo is in progress. */
    static Component casting(List<Click> clicks, RpgClass c) {
        return Component.text(Combo.letters(clicks, 3).replace("-", " - "), c.color()).decorate(TextDecoration.BOLD);
    }

    Component legend(Player player, RpgClass c, ClassKit kit) {
        UUID id = player.getUniqueId();
        int level = classes.level(id);
        Click first = combos.first(player, c);
        Component legend = Component.text(kit.resourceName() + " " + (int) resources.get(id), c.color());
        for (Slot slot : Slot.values()) {
            legend = legend.append(Component.text("   "));
            String name = slots.name(id, c, slot);
            String combo = Combo.label(slot, first);
            Component icon = icons.icon(player, name);
            StringBuilder text = new StringBuilder(icon == null ? "[" + combo + "] " + name : " " + combo);
            if (level < slot.unlockLevel()) {
                text.append(" (level ").append(slot.unlockLevel()).append(')');
            } else {
                long left = slots.cooldownLeft(id, c, slot);
                if (left > 0) text.append(" (").append((left + 999) / 1000).append("s)");
                else if (slot == Slot.ULTIMATE && !resources.isFull(id)) {
                    text.append(" (needs ").append((int) Resources.MAX).append(' ').append(kit.resourceName()).append(')');
                }
            }
            if (icon != null) legend = legend.append(icon);
            legend = legend.append(Component.text(text.toString(), NamedTextColor.WHITE));
        }
        return legend;
    }

    /** {@code /class hud} and the hub's Spell bar entry. */
    public void toggle(Player player) {
        boolean on = classes.toggleSpellBar(player.getUniqueId());
        player.sendMessage(Component.text(on ? "Spell bar shown." : "Spell bar hidden. /class hud shows it again.",
                NamedTextColor.GRAY));
        update(player);
    }

    private void filled(Player player) {
        if (classes.level(player.getUniqueId()) < Slot.ULTIMATE.unlockLevel()) return;
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        fx.ring(player.getLocation(), Particle.END_ROD, 1.2, 16);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
}
