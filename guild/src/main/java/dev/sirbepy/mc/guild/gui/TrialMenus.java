package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Trials;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The Promotion Trial icon on the main board: qualifying, waiting out a cooldown, running, and claiming it. */
final class TrialMenus {

    private final Guild guild;

    TrialMenus(Guild guild) {
        this.guild = guild;
    }

    ItemStack trialIcon(Player player, Member member) {
        Rank next = member.rank().next();
        if (next == null) {
            return Icons.glint(Icons.icon(Material.NETHER_STAR, "S-Rank", NamedTextColor.GOLD,
                    "You stand at the summit of the Guild.", "Few ever will."));
        }
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            ContractDef def = trial.def();
            List<String> lore = new ArrayList<>();
            lore.add("Promotion Trial for " + next.name() + "-Rank");
            lore.add("");
            boolean ready = GuildMenus.objectiveLines(player, trial, lore);
            lore.add("");
            if (def.noDeath()) {
                lore.add("Dying wipes this trial's progress.");
            }
            long left = member.trialStarted() + Trials.days(next) * Guild.DAY - guild.clock().nowMillis();
            lore.add("Time left: " + Text.duration(left));
            lore.add(GuildMenus.rewardLine(trial.reward()) + ", and the rank");
            lore.add("");
            lore.add(ready ? "!Click to complete the trial" : "Finish every part, then click here");
            ItemStack icon = Icons.icon(def.icon(), def.title(), next.color(), lore);
            return ready ? Icons.glint(icon) : icon;
        }
        if (!Guild.qualifies(member)) {
            return Icons.icon(Material.IRON_BARS, "Next: " + next.title(), NamedTextColor.GRAY,
                    "Reputation: " + member.reputation() + " / " + next.reputation(),
                    "Contracts: " + member.contractsDone() + " / " + next.contracts(),
                    "",
                    "Then the examiner will set you a",
                    "Promotion Trial. What it asks, nobody says.");
        }
        long wait = member.trialRetryAfter() - guild.clock().nowMillis();
        if (wait > 0) {
            return Icons.icon(Material.CLOCK, "Promotion Trial: " + next.name() + "-Rank", NamedTextColor.GRAY,
                    "The examiner will see you again in " + Text.duration(wait) + ".");
        }
        ContractDef def = Trials.forRank(next);
        List<String> lore = new ArrayList<>();
        lore.add("You have earned the right to attempt it.");
        lore.add("");
        for (Objective o : def.objectives()) {
            lore.add("- " + o.text());
        }
        if (def.noDeath()) {
            lore.add("- Without dying: a death wipes its progress");
        }
        lore.add("");
        lore.add("You get " + Trials.days(next) + " days once you begin.");
        lore.add("");
        lore.add("!Click to begin");
        return Icons.glint(Icons.icon(def.icon(), "Promotion Trial: " + def.title(), next.color(), lore));
    }

    void trialClick(Player player, Member member) {
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            GuildMenus.claim(player, guild, trial);
            return;
        }
        switch (guild.startTrial(player)) {
            case STARTED -> {
                Chat.send(player, "Your Promotion Trial has begun: " + member.trial().def().title()
                        + ". The examiner is watching.", Chat.BRONZE);
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
            }
            case COOLDOWN -> Chat.send(player, "Not yet. Rest, and come back when the examiner calls.", NamedTextColor.YELLOW);
            case NOT_ELIGIBLE, ALREADY_ACTIVE -> { }
        }
    }
}
