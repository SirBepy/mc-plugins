package dev.sirbepy.mc.haru.item;

import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.NpcAnchor;
import dev.sirbepy.mc.haru.dialogue.Speech;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What Haru's items do, and the guards that keep them from being placed, crafted into dye or fed to
 * an allay. A bound item only works for the player it is bound to.
 */
public final class HaruItemListener implements Listener {

    public static final int HOMEWARD_SECONDS = 3;
    public static final double HORN_RADIUS = 48;
    public static final int HORN_GLOW_TICKS = 30 * 20;
    public static final int HORN_COOLDOWN_TICKS = 60 * 20;
    private static final NamespacedKey DRAGON_BALL = new NamespacedKey("dragonballs", "star");

    private final HaruContext ctx;
    private final HaruItems items;
    private final Map<UUID, BukkitTask> channels = new HashMap<>();

    public HaruItemListener(HaruContext ctx) {
        this.ctx = ctx;
        this.items = ctx.items();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String id = items.idOf(item);
        if (id == null) {
            return;
        }
        Player player = event.getPlayer();
        if (ctx.bound().foreignTo(item, player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        switch (id) {
            case HaruItems.HOMEWARD_PETAL -> {
                event.setCancelled(true);
                startHomeward(player);
            }
            case HaruItems.SPIRIT_HORN -> blowHorn(player);
            default -> {
                // The rebirth petal is passive; denying the use stops it acting on blocks.
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            }
        }
    }

    private void startHomeward(Player player) {
        if (channels.containsKey(player.getUniqueId())) {
            return;
        }
        NpcAnchor anchor = ctx.store().anchor();
        Location home = anchor == null ? null : anchor.toLocation();
        if (home == null) {
            Speech.note(player, "The petal stirs, but Haru has no sanctuary to carry you to yet.");
            return;
        }
        Location start = player.getLocation();
        int[] remaining = {HOMEWARD_SECONDS};
        player.sendActionBar(Component.text("Hold still... " + remaining[0], Goods.SAKURA));
        BukkitTask task = ctx.plugin().getServer().getScheduler().runTaskTimer(ctx.plugin(), () -> {
            if (!player.isOnline() || player.isDead()
                    || !player.getWorld().equals(start.getWorld())
                    || player.getLocation().distanceSquared(start) > 0.25) {
                cancelChannel(player);
                if (player.isOnline()) {
                    player.sendActionBar(Component.text("The petal settles. You moved.", Goods.LORE));
                }
                return;
            }
            player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1, 0), 12, 0.5, 0.8, 0.5, 0);
            remaining[0]--;
            if (remaining[0] > 0) {
                player.sendActionBar(Component.text("Hold still... " + remaining[0], Goods.SAKURA));
                return;
            }
            cancelChannel(player);
            if (consumeOne(player, HaruItems.HOMEWARD_PETAL)) {
                player.teleport(arrival(home));
                player.playSound(player.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 1f, 1f);
                Speech.say(player, "Welcome home.");
            }
        }, 20L, 20L);
        channels.put(player.getUniqueId(), task);
    }

    /** One and a half blocks in front of Haru, facing him. */
    static Location arrival(Location home) {
        Location front = home.clone().add(home.getDirection().setY(0).normalize().multiply(1.5));
        front.setYaw(home.getYaw() + 180);
        front.setPitch(0);
        return front;
    }

    private void cancelChannel(Player player) {
        BukkitTask task = channels.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelChannel(event.getPlayer());
    }

    void blowHorn(Player player) {
        if (player.hasCooldown(Material.GOAT_HORN)) {
            return;
        }
        int glowing = 0;
        for (Entity entity : player.getNearbyEntities(HORN_RADIUS, HORN_RADIUS, HORN_RADIUS)) {
            if (entity instanceof Monster monster) {
                monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, HORN_GLOW_TICKS, 0, false, false));
                glowing++;
            }
        }
        player.sendActionBar(Component.text(glowing + " hostile creature(s) revealed", Goods.SAKURA));
        // Vanilla sets its own 7 s horn cooldown after this event; the longer one goes on a tick later.
        ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(),
                () -> player.setCooldown(Material.GOAT_HORN, HORN_COOLDOWN_TICKS));
    }

    /**
     * A Petal of Rebirth keeps everything but Dragon Balls: those stay in the drops so the Dragon
     * Balls plugin still scatters them from the fallen player, as its rules require.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Iterator<ItemStack> drops = event.getDrops().iterator();
        boolean consumed = false;
        while (drops.hasNext() && !consumed) {
            ItemStack drop = drops.next();
            if (items.is(drop, HaruItems.REBIRTH_PETAL) && !ctx.bound().foreignTo(drop, event.getEntity().getUniqueId())) {
                if (drop.getAmount() > 1) {
                    drop.setAmount(drop.getAmount() - 1);
                } else {
                    drops.remove();
                }
                consumed = true;
            }
        }
        if (!consumed) {
            return;
        }
        event.getDrops().removeIf(drop -> {
            if (isDragonBall(drop)) {
                return false;
            }
            event.getItemsToKeep().add(drop);
            return true;
        });
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Speech.say(event.getEntity(), "A petal falls in your place. Everything you carried is still yours.");
    }

    private static boolean isDragonBall(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(DRAGON_BALL, PersistentDataType.INTEGER);
    }

    /** Removes one from the hands first, then anywhere in the inventory. */
    private boolean consumeOne(Player player, String id) {
        for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            ItemStack held = player.getInventory().getItem(hand);
            if (items.is(held, id)) {
                held.setAmount(held.getAmount() - 1);
                player.getInventory().setItem(hand, held.getAmount() > 0 ? held : null);
                return true;
            }
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (items.is(contents[slot], id)) {
                contents[slot].setAmount(contents[slot].getAmount() - 1);
                player.getInventory().setItem(slot, contents[slot].getAmount() > 0 ? contents[slot] : null);
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (items.idOf(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (items.idOf(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseOnEntity(PlayerInteractEntityEvent event) {
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (items.idOf(held) != null) {
            event.setCancelled(true);
        }
    }

    public void shutdown() {
        channels.values().forEach(BukkitTask::cancel);
        channels.clear();
    }
}
