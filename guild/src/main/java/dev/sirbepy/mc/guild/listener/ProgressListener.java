package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns gameplay events into contract progress for whoever did them. Bosses go through {@link BossCredit};
 * every other kill passes {@link FarmGuard} first.
 */
public final class ProgressListener implements Listener {

    private final Guild guild;
    private final BossCredit bosses;
    private final FarmGuard farms;

    public ProgressListener(Guild guild, BossCredit bosses, FarmGuard farms) {
        this.guild = guild;
        this.bosses = bosses;
        this.farms = farms;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        String type = entity.getType().name();
        Player killer = entity.getKiller();
        boolean boss = BossCredit.BOSSES.contains(entity.getType());
        FarmGuard.Verdict verdict = killer == null || boss ? FarmGuard.Verdict.COUNTS : farms.judge(entity);
        if (killer != null && verdict != FarmGuard.Verdict.NONE) {
            List<String> facts = new ArrayList<>();
            facts.add(type);
            if (entity instanceof Enemy && verdict == FarmGuard.Verdict.COUNTS) {
                facts.add("#HOSTILE");
            }
            guild.progress(killer, Kind.HUNT, facts, 1);
            String key = nameKey(entity);
            if (key != null) {
                guild.progress(killer, Kind.NAMED_HUNT, Set.of(key), 1);
            }
        }
        if (boss) {
            for (Player credited : bosses.credited(entity, killer)) {
                guild.progress(credited, Kind.BOSS, Set.of(type), 1);
                var member = guild.store().member(credited.getUniqueId());
                if (member != null) {
                    guild.achievements().bossCredited(credited, member, type);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable crop) {
            if (crop.getAge() >= crop.getMaximumAge()) {
                guild.progress(player, Kind.HARVEST, Set.of(block.getType().name()), 1);
            }
            return;
        }
        // Silk Touch would let one ore be placed and broken again forever.
        if (!player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            guild.progress(player, Kind.MINE, Set.of(block.getType().name()), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Entity caught = event.getCaught();
        String material = caught instanceof Item item ? item.getItemStack().getType().name() : "UNKNOWN";
        guild.progress(event.getPlayer(), Kind.FISH, Set.of(material), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            guild.progress(player, Kind.BREED, Set.of(event.getEntity().getType().name()), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        AnimalTamer owner = event.getOwner();
        if (owner instanceof Player player) {
            guild.progress(player, Kind.TAME, Set.of(event.getEntity().getType().name()), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        guild.progress(event.getPlayer(), Kind.TRADE, Set.of(event.getVillager().getType().name()), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        guild.progress(event.getEnchanter(), Kind.ENCHANT, Set.of(event.getItem().getType().name()), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent event) {
        if (event.getItemAmount() > 0) {
            guild.progress(event.getPlayer(), Kind.SMELT, Set.of(event.getItemType().name()), event.getItemAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        guild.onDeath(event.getEntity());
    }

    /** A datapack mob's name key, e.g. {@code incendium.mob.misc.toxic.name}; null for unnamed or player-named mobs. */
    static String nameKey(Entity entity) {
        Component name = entity.customName();
        return name instanceof TranslatableComponent translatable ? translatable.key() : null;
    }
}
