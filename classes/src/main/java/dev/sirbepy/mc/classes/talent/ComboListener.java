package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Combo.Click;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Turns three quick clicks with a class weapon into a spell ({@link Combo}). Rules that keep normal
 * play from casting by accident:
 * <ul>
 *   <li>Clicks while sneaking never count, so sneak + right click stays AuraSkills' and Carry's.</li>
 *   <li>Only the weapon's first click opens a combo, so plain melee (all left) or plain archery
 *       (all right) never starts one.</li>
 *   <li>A right click that turns into a hold (shield block, bow draw, trident charge, eating) drops
 *       the combo: only taps count.</li>
 *   <li>The first click does what it always did (doors, chests); the second and third right clicks
 *       are cancelled so they don't draw, fire, throw or block. Left clicks are never cancelled, so a
 *       combo's swing still hits.</li>
 * </ul>
 */
public final class ComboListener implements Listener {

    /** Longest gap between two clicks of one combo. */
    static final long GAP_MILLIS = 1000;
    /**
     * One physical click can reach the server as two events (a swing and an attack, a block click
     * and an air click), always within the same tick or the next; no player clicks twice this fast.
     */
    static final long SAME_CLICK_MILLIS = 80;
    /** Ticks after an opening right click to look for a held item use; a tap is long over by then. */
    static final long HOLD_CHECK_TICKS = 6;

    private final Plugin plugin;
    private final Classes classes;
    private final AbilitySlots slots;
    private final LongSupplier now;
    private final Predicate<Player> holding;
    private final Map<UUID, State> states = new HashMap<>();
    /** Redraws the player's spell bar or HUD; the engine wires it. */
    private Consumer<Player> onProgress = p -> {
    };

    /** {@code holding}: whether the player is using an item right now (raised shield, drawn bow...). */
    public ComboListener(Plugin plugin, Classes classes, AbilitySlots slots, LongSupplier now, Predicate<Player> holding) {
        this.plugin = plugin;
        this.classes = classes;
        this.slots = slots;
        this.now = now;
        this.holding = holding;
    }

    private static final class State {
        final List<Click> clicks = new ArrayList<>();
        long lastAt;
        /** The last click counted, kept after a cast so its duplicate can't open a new combo. */
        Click lastClick;
        /** Until when the off-hand half of a cancelled right click is cancelled too. */
        long cancelOffhandUntil;
    }

    void setOnProgress(Consumer<Player> onProgress) {
        this.onProgress = onProgress;
    }

    /** The clicks of the combo in progress, empty when there is none. */
    public List<Click> progress(Player player) {
        State s = states.get(player.getUniqueId());
        if (s == null || s.clicks.isEmpty() || now.getAsLong() - s.lastAt > GAP_MILLIS) return List.of();
        return List.copyOf(s.clicks);
    }

    /** The first click {@code player}'s combos open with right now: the held weapon's, else the class's. */
    public Click first(Player player, RpgClass c) {
        var held = player.getInventory().getItemInMainHand().getType();
        return Combo.weapon(c, held) ? Combo.first(held) : Combo.defaultFirst(c);
    }

    /** Whether {@code player} holds one of their class's weapons. */
    public boolean armed(Player player) {
        RpgClass c = classes.active(player.getUniqueId());
        return c != null && Combo.weapon(c, player.getInventory().getItemInMainHand().getType());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            // The client tries the off hand when the main hand's use did nothing, so a cancelled
            // mid-combo right click with a sword would still raise the off-hand shield.
            State s = states.get(event.getPlayer().getUniqueId());
            if (a.isRightClick() && s != null && now.getAsLong() <= s.cancelOffhandUntil) cancel(event);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || a == Action.PHYSICAL) return;
        Result r = click(event.getPlayer(), a.isLeftClick() ? Click.LEFT : Click.RIGHT);
        if (r.redraws) onProgress.accept(event.getPlayer());
        if (r.cancels && a.isRightClick()) cancel(event);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            State s = states.get(event.getPlayer().getUniqueId());
            if (s != null && now.getAsLong() <= s.cancelOffhandUntil) event.setCancelled(true);
            return;
        }
        Result r = click(event.getPlayer(), Click.RIGHT);
        if (r.cancels) event.setCancelled(true);
        if (r.redraws) onProgress.accept(event.getPlayer());
    }

    // No ignoreCancelled: hits on Interaction entities arrive already cancelled, and still are clicks.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (click(event.getPlayer(), Click.LEFT).redraws) onProgress.accept(event.getPlayer());
    }

    @EventHandler
    public void onHotbar(PlayerItemHeldEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private void cancel(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    enum Result {
        /** Not a combo click: the click goes through untouched. */
        IGNORED(false, false),
        /** Opened a combo; the click still does its normal thing. */
        STARTED(false, true),
        /** The same physical click arriving a second time. */
        DUPLICATE(false, false),
        CONTINUED(true, true),
        CAST(true, true),
        /** Three clicks that match no combo. */
        FIZZLED(true, true);

        final boolean cancels;
        /** Whether the combo so far changed, so the bar or HUD should redraw. */
        final boolean redraws;

        Result(boolean cancels, boolean redraws) {
            this.cancels = cancels;
            this.redraws = redraws;
        }
    }

    Result click(Player player, Click click) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        var held = player.getInventory().getItemInMainHand().getType();
        if (c == null || player.isSneaking() || !Combo.weapon(c, held)) return Result.IGNORED;
        long t = now.getAsLong();
        State s = states.computeIfAbsent(id, k -> new State());
        if (s.lastClick == click && t - s.lastAt <= SAME_CLICK_MILLIS) {
            // The opening click passes through untouched both times; any later one is cancelled both times.
            return s.clicks.size() == 1 ? Result.DUPLICATE : Result.CONTINUED;
        }
        if (t - s.lastAt > GAP_MILLIS) s.clicks.clear();
        if (s.clicks.isEmpty()) {
            if (click != Combo.first(held)) return Result.IGNORED;
            s.clicks.add(click);
            s.lastAt = t;
            s.lastClick = click;
            if (click == Click.RIGHT) watchForHold(player, s, t);
            tick(player, 1);
            return Result.STARTED;
        }
        s.clicks.add(click);
        s.lastAt = t;
        s.lastClick = click;
        if (click == Click.RIGHT) s.cancelOffhandUntil = t + SAME_CLICK_MILLIS;
        if (s.clicks.size() < 3) {
            tick(player, s.clicks.size());
            return Result.CONTINUED;
        }
        Slot slot = Combo.match(s.clicks);
        s.clicks.clear();
        if (slot == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
            return Result.FIZZLED;
        }
        slots.cast(player, slot);
        return Result.CAST;
    }

    /** Drops the combo if the opening right click is still held a moment later. */
    private void watchForHold(Player player, State s, long startedAt) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (states.get(player.getUniqueId()) == s && s.clicks.size() == 1 && s.lastAt == startedAt && holding.test(player)) {
                s.clicks.clear();
                onProgress.accept(player);
            }
        }, HOLD_CHECK_TICKS);
    }

    private static void tick(Player player, int step) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.0f + 0.3f * step);
    }
}
