package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.item.Goods;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.List;

/** Counts the owner's deeds towards today's Spirit Requests. Nobody else's actions count. */
public final class QuestListener implements Listener {

    private final HaruContext ctx;

    public QuestListener(HaruContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            progress(killer, QuestDef.Type.KILL, event.getEntityType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!ctx.isOwner(player)) {
            return;
        }
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable crop) {
            if (crop.getAge() >= crop.getMaximumAge()) {
                progress(player, QuestDef.Type.HARVEST, block.getType().name());
            }
            return;
        }
        // Silk Touch would let an ore be placed and broken again forever.
        if (!player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            progress(player, QuestDef.Type.MINE, block.getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            progress(event.getPlayer(), QuestDef.Type.FISH, "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            progress(player, QuestDef.Type.BREED, "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        progress(event.getEnchanter(), QuestDef.Type.ENCHANT, "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        progress(event.getPlayer(), QuestDef.Type.TRADE, "");
    }

    /** Biome requests: checked on a timer rather than on every step. */
    public void biomeTick(Player player) {
        progress(player, QuestDef.Type.BIOME, player.getWorld().getBiome(player.getLocation()).getKey().toString());
    }

    void progress(Player player, QuestDef.Type type, String target) {
        if (!ctx.isOwner(player)) {
            return;
        }
        OwnerState state = ctx.ownerState();
        List<QuestDef> finished = ctx.board().progress(state, type, target, 1);
        if (finished.isEmpty()) {
            for (ActiveQuest active : state.board()) {
                QuestDef def = QuestPool.byId(active.questId());
                if (def != null && def.type() == type && !active.claimed() && def.matches(target)
                        && active.progress() < def.amount()) {
                    player.sendActionBar(Component.text("✿ " + def.text() + ": " + active.progress() + " / "
                            + def.amount(), Goods.SAKURA));
                    break;
                }
            }
            ctx.store().markDirty();
            return;
        }
        for (QuestDef def : finished) {
            player.sendMessage(Component.text("✿ Spirit Request done: " + def.text()
                    + ". Return to Haru for your reward.", Goods.SAKURA));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        ctx.store().save();
    }
}
