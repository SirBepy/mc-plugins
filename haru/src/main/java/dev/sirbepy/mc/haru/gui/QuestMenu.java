package dev.sirbepy.mc.haru.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Speech;
import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.quest.QuestBoard;
import dev.sirbepy.mc.haru.quest.QuestDef;
import dev.sirbepy.mc.haru.quest.QuestPool;
import dev.sirbepy.mc.haru.shop.Payment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The current three Spirit Requests: left-click claims, right-click asks Haru for a different one. */
final class QuestMenu {

    private static final int[] SLOTS = {20, 22, 24};

    private final HaruContext ctx;
    private final HaruMenus menus;

    QuestMenu(HaruContext ctx, HaruMenus menus) {
        this.ctx = ctx;
        this.menus = menus;
    }

    Menu build(Player player, OwnerState state) {
        Menu menu = new Menu(6, Component.text("Spirit Requests", Speech.HARU));
        int rerollsLeft = QuestBoard.MAX_REROLLS - state.rerollsUsed();
        menu.set(4, Icons.button(Material.BOOK, "Haru's requests", Goods.SAKURA,
                "New requests at midnight and noon.",
                "Finish all three for a bonus.",
                "Different request: " + rerollsLeft + " left until then ("
                        + (ctx.board().rerollCost(state) == 0 ? "next one free" : QuestBoard.REROLL_COST + " petals") + ")."));
        for (int i = 0; i < state.board().size() && i < SLOTS.length; i++) {
            int index = i;
            menu.set(SLOTS[i], icon(player, state, state.board().get(i)), (shown, click) -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    reroll(player, state, index);
                } else {
                    claim(player, state, index);
                }
                menus.refresh(player, shown, HaruMenus.Screen.QUESTS);
            });
        }
        menus.addBack(menu, player);
        menu.frame(Material.PINK_STAINED_GLASS_PANE);
        return menu;
    }

    private ItemStack icon(Player player, OwnerState state, ActiveQuest active) {
        QuestDef def = QuestPool.byId(active.questId());
        if (def == null) {
            return new ItemStack(Material.BARRIER);
        }
        List<String> lore = new ArrayList<>();
        lore.add(capitalize(def.difficulty().name()) + " request");
        boolean ready;
        if (def.type() == QuestDef.Type.DELIVER) {
            int carried = Math.min(def.amount(), Payment.count(player.getInventory(), def.deliverMaterial()));
            lore.add("You carry: " + carried + " / " + def.amount());
            ready = carried >= def.amount();
        } else {
            lore.add("Progress: " + active.progress() + " / " + def.amount());
            ready = active.progress() >= def.amount();
        }
        lore.add("Reward: " + def.difficulty().petals(state.tier()) + " Spirit Petals, +"
                + def.difficulty().reputation() + " trust");
        lore.add("");
        TextColor color = Goods.SAKURA;
        if (active.claimed()) {
            lore.add("Done. Haru is grateful.");
            color = Goods.LORE;
        } else if (ready) {
            lore.add(def.type() == QuestDef.Type.DELIVER ? "Click to hand them over" : "Click to claim your reward");
        } else {
            lore.add("Right-click for a different request");
        }
        ItemStack item = Icons.button(new ItemStack(def.icon()), def.text(), color, lore);
        return ready && !active.claimed() ? Goods.glint(item) : item;
    }

    private void claim(Player player, OwnerState state, int index) {
        List<String> given = new ArrayList<>();
        switch (ctx.board().claim(player, state, index, given)) {
            case CLAIMED -> {
                Speech.say(player, "Thank you. That was exactly what I needed.");
                Speech.note(player, "Request complete: " + String.join(", ", given));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                ctx.store().save();
            }
            case NOT_DONE -> Speech.say(player, "Not finished yet. I can wait. I am very good at waiting.");
            case MISSING_ITEMS -> Speech.say(player, "You do not have all of them with you yet.");
            case ALREADY_CLAIMED -> Speech.say(player, "You already did this one. Rest.");
        }
    }

    private void reroll(Player player, OwnerState state, int index) {
        switch (ctx.board().reroll(state, index, ctx.clock().requestSet())) {
            case REROLLED -> {
                Speech.say(player, "Hmm. Then perhaps this instead.");
                ctx.store().save();
            }
            case NO_REROLLS_LEFT -> Speech.say(player, "That is all the requests I have thought of for now.");
            case NO_PETALS -> Speech.note(player, "A different request costs " + QuestBoard.REROLL_COST + " Spirit Petals.");
            case NOT_REROLLABLE -> Speech.say(player, "That one is finished. Claim it instead.");
            case NOTHING_ELSE -> Speech.say(player, "I have no other request of that kind right now.");
        }
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
