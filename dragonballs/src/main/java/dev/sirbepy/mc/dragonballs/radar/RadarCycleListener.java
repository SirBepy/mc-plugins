package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Sneak + right-click with a radar (in the air or on a block) steps which ball it tracks: nearest,
 * then each awake ball of its set from nearest to furthest, then back to nearest (Joe,
 * 2026-10-08). Clicking a placed ball is an entity click, so it never reaches here and the summon
 * trigger keeps it. Signs are skipped so a sneaking click on the spawn station's radar signs
 * doesn't also cycle.
 */
public final class RadarCycleListener implements Listener {

    private final RadarItems items;
    private final RadarTracking tracking;
    private final RadarTask task;

    public RadarCycleListener(RadarItems items, RadarTracking tracking, RadarTask task) {
        this.items = items;
        this.tracking = tracking;
        this.task = task;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (!player.isSneaking() || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && Tag.ALL_SIGNS.isTagged(clicked.getType())) {
            return;
        }
        ItemStack radar = radarFor(player, event.getHand());
        if (radar == null) {
            return;
        }
        if (clicked != null && clicked.getType() == Material.LODESTONE) {
            // A lodestone would bind the radar like a plain compass; the radar has its own target.
            event.setUseItemInHand(Event.Result.DENY);
        }

        BallSet set = items.setOf(radar);
        List<Integer> stars = task.starsNearestFirst(player, set);
        if (stars.isEmpty()) {
            return;
        }
        tracking.cycle(player.getUniqueId(), set, stars);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.6f);
        task.refresh(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracking.forget(event.getPlayer().getUniqueId());
    }

    /**
     * The radar this click belongs to. The client sends the click for the main hand first and then
     * the off hand, so a radar held in the off hand only counts when the main hand holds none,
     * otherwise one click would step twice.
     */
    private ItemStack radarFor(Player player, EquipmentSlot hand) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (hand == EquipmentSlot.HAND) {
            return items.isRadar(main) ? main : null;
        }
        if (hand == EquipmentSlot.OFF_HAND && !items.isRadar(main)) {
            ItemStack off = player.getInventory().getItemInOffHand();
            return items.isRadar(off) ? off : null;
        }
        return null;
    }
}
