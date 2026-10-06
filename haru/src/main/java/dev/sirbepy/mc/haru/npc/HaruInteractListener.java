package dev.sirbepy.mc.haru.npc;

import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Dialogue;
import dev.sirbepy.mc.haru.dialogue.DialogueContext;
import dev.sirbepy.mc.haru.dialogue.HeldItems;
import dev.sirbepy.mc.haru.dialogue.Speech;
import dev.sirbepy.mc.haru.gui.HaruMenus;
import dev.sirbepy.mc.haru.item.BoundListener;
import dev.sirbepy.mc.haru.quest.QuestDef;
import dev.sirbepy.mc.haru.quest.QuestPool;
import dev.sirbepy.mc.haru.shop.Payment;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Right-clicking Haru never opens the vanilla trade screen. His owner gets a line of dialogue and his
 * menu (sneaking: only the dialogue); anyone else gets a polite word and his everyday shelf. For a day
 * after his owner dies the shop is shut: the owner can still settle requests, guests get nothing.
 */
public final class HaruInteractListener implements Listener {

    static final long GREETING_COOLDOWN_MILLIS = 20_000;
    static final String MET = "met";

    private final HaruContext ctx;
    private final HaruMenus menus;
    private final BoundListener bound;
    private final Map<UUID, Long> lastGreeting = new HashMap<>();

    public HaruInteractListener(HaruContext ctx, HaruMenus menus, BoundListener bound) {
        this.ctx = ctx;
        this.menus = menus;
        this.bound = bound;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!ctx.npc().isHaru(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        OwnerState state = ctx.ownerState();
        boolean closed = state.closed(ctx.clock().nowMillis());
        if (!ctx.isOwner(player)) {
            if (greetingDue(player, GREETING_COOLDOWN_MILLIS / 2)) {
                Speech.say(player, closed ? Dialogue.closedToGuests(ctx.ownerName())
                        : Dialogue.stranger(ctx.ownerName(), ctx.random()));
            }
            if (!closed && !player.isSneaking()) {
                menus.open(player, HaruMenus.Screen.EVERYDAY);
            }
            return;
        }
        if (bound.returnLostItems(player, state)) {
            Speech.say(player, Dialogue.RETURNED);
        }
        if (closed) {
            if (greetingDue(player, GREETING_COOLDOWN_MILLIS)) {
                Speech.say(player, Dialogue.closed(player.getName(), ctx.random()));
            }
            if (!player.isSneaking()) {
                menus.open(player, HaruMenus.Screen.QUESTS);
            }
            return;
        }
        boolean first = state.unlocks().add(MET);
        if (first || player.isSneaking() || greetingDue(player, GREETING_COOLDOWN_MILLIS)) {
            Speech.say(player, Dialogue.greet(context(player, state, first), player.getName(), ctx.random()));
            player.playSound(event.getRightClicked().getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1.1f);
        }
        if (first) {
            ctx.store().save();
        }
        if (!player.isSneaking()) {
            menus.open(player, HaruMenus.Screen.MAIN);
        }
    }

    /** Armor-stand-style clicks arrive as this subclass too; it only needs cancelling. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (ctx.npc().isHaru(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean greetingDue(Player player, long cooldown) {
        long now = ctx.clock().nowMillis();
        Long last = lastGreeting.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            return false;
        }
        lastGreeting.put(player.getUniqueId(), now);
        return true;
    }

    DialogueContext context(Player player, OwnerState state, boolean first) {
        World world = player.getWorld();
        String held = HeldItems.tag(player.getInventory().getItemInMainHand(), ctx.items());
        if (held == null) {
            held = HeldItems.tag(player.getInventory().getItemInOffHand(), ctx.items());
        }
        int ready = 0;
        for (ActiveQuest active : state.board()) {
            QuestDef def = QuestPool.byId(active.questId());
            if (def == null || active.claimed()) {
                continue;
            }
            boolean done = def.type() == QuestDef.Type.DELIVER
                    ? Payment.count(player.getInventory(), def.deliverMaterial()) >= def.amount()
                    : active.progress() >= def.amount();
            if (done) {
                ready++;
            }
        }
        boolean allClaimed = !state.board().isEmpty() && state.board().stream().allMatch(ActiveQuest::claimed);
        return new DialogueContext(first, state.tier(), world.getTime(), (int) ((world.getFullTime() / 24000) % 8),
                world.hasStorm(), world.isThundering(), held, player.getHealth(), player.getFoodLevel(),
                player.getLevel(), state.petals(), ready, allClaimed);
    }
}
