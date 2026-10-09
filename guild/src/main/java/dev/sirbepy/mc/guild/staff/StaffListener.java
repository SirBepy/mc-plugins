package dev.sirbepy.mc.guild.staff;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.GuildMenus;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Nothing hurts, burns, converts, leashes or re-skills the staff, and right-clicking one (either body)
 * never opens a villager trade: they speak, then open the Guild screen that fits their job. Sneak-click
 * just talks. Talking to any of them puts the player at the hall's desk, where work is taken and turned in.
 */
public final class StaffListener implements Listener {

    private final StaffNpcs npcs;
    private final Guild guild;
    private final GuildMenus menus;
    private final Dialogue dialogue;

    public StaffListener(StaffNpcs npcs, Guild guild, GuildMenus menus, Dialogue dialogue) {
        this.npcs = npcs;
        this.guild = guild;
        this.menus = menus;
        this.dialogue = dialogue;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Staff staff = npcs.staffOf(event.getRightClicked());
        if (staff == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        talk(event.getPlayer(), staff, event.getRightClicked().getLocation());
    }

    /** Armor-stand style clicks arrive as this subclass first; only the cancel matters here. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (npcs.staffOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    void talk(Player player, Staff staff, Location at) {
        menus.desk().visit(player, at);
        Member member = guild.member(player);
        player.sendMessage(Chat.speech(staff.shortName(), staff.color(), dialogue.line(staff, player, member)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 1.1f);
        if (player.isSneaking()) {
            return;
        }
        GuildMenus.Screen screen = switch (staff) {
            case RECEPTIONIST -> GuildMenus.Screen.MAIN;
            case CLERK -> member == null ? null : GuildMenus.Screen.MAIN;
            case TREASURER -> guild.isGuildmaster(player) ? GuildMenus.Screen.OFFICE : null;
            case EXAMINER -> member != null && (Guild.qualifies(member) || activeTrial(member)) ? GuildMenus.Screen.MAIN : null;
            case ARCHIVIST -> null;
        };
        if (staff == Staff.ARCHIVIST) {
            menus.openHallOfFame(player);
        } else if (screen != null) {
            menus.open(player, screen);
        }
    }

    private static boolean activeTrial(Member member) {
        ActiveContract trial = member.trial();
        return trial != null && !trial.claimed();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransform(EntityTransformEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (npcs.staffOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        npcs.removeStrays(event.getEntities().toArray(Entity[]::new));
        // Hiding needs the entity loaded, so whoever is online when the hall's chunk loads is fixed here.
        if (event.getEntities().stream().anyMatch(entity -> npcs.staffOf(entity) != null)) {
            npcs.applyVisibilityToAll();
        }
    }
}
