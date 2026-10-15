package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The resource as a boss bar (AuraSkills owns the action bar), up while there's something in it or
 * the player is fighting. Holding sneak swaps its text for the key legend:
 * {@code [1] Battle Cry  [2] Charge (12s)  [3] Ground Slam (needs 100 Rage)}.
 */
public final class ResourceBar {

    private final Classes classes;
    private final Resources resources;
    private final Map<RpgClass, ClassKit> kits;
    private final AbilitySlots slots;
    private final Binds binds;
    private final BindListener sneaking;
    private final Fx fx;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ResourceBar(Classes classes, Resources resources, Map<RpgClass, ClassKit> kits, AbilitySlots slots,
                       Binds binds, BindListener sneaking, Fx fx) {
        this.classes = classes;
        this.resources = resources;
        this.kits = kits;
        this.slots = slots;
        this.binds = binds;
        this.sneaking = sneaking;
        this.fx = fx;
        resources.onFull(this::filled);
    }

    /** Every 5 ticks per online player. */
    public void update(Player player) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        ClassKit kit = c == null ? null : kits.get(c);
        boolean legend = kit != null && sneaking.showLegend(player);
        if (kit == null || !(legend || resources.visible(id))) {
            hide(player);
            return;
        }
        BossBar bar = bars.computeIfAbsent(id, k -> BossBar.bossBar(Component.empty(), 0, kit.barColor(), BossBar.Overlay.PROGRESS));
        bar.color(kit.barColor());
        bar.progress((float) (resources.get(id) / Resources.MAX));
        bar.name(legend ? legend(player, c, kit) : status(player, c, kit));
        player.showBossBar(bar);
        if (resources.isFull(id) && classes.level(id) >= Slot.ULTIMATE.unlockLevel()) {
            fx.ring(player.getLocation(), Particle.END_ROD, 0.8, 6);
        }
    }

    Component status(Player player, RpgClass c, ClassKit kit) {
        UUID id = player.getUniqueId();
        String text = kit.resourceName() + " " + (int) resources.get(id) + "/" + (int) Resources.MAX;
        if (resources.isFull(id) && classes.level(id) >= Slot.ULTIMATE.unlockLevel()) {
            text += " · " + slots.name(id, c, Slot.ULTIMATE) + " ready [" + binds.bind(id, Slot.ULTIMATE).key() + "]";
        }
        return Component.text(text, c.color());
    }

    Component legend(Player player, RpgClass c, ClassKit kit) {
        UUID id = player.getUniqueId();
        int level = classes.level(id);
        StringBuilder text = new StringBuilder();
        for (Slot slot : Slot.values()) {
            if (!text.isEmpty()) text.append("   ");
            text.append('[').append(binds.bind(id, slot).key()).append("] ").append(slots.name(id, c, slot));
            if (level < slot.unlockLevel()) {
                text.append(" (level ").append(slot.unlockLevel()).append(')');
                continue;
            }
            long left = slots.cooldownLeft(id, c, slot);
            if (left > 0) text.append(" (").append((left + 999) / 1000).append("s)");
            else if (slot == Slot.ULTIMATE && !resources.isFull(id)) {
                text.append(" (needs ").append((int) Resources.MAX).append(' ').append(kit.resourceName()).append(')');
            }
        }
        return Component.text(text.toString(), NamedTextColor.WHITE);
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
