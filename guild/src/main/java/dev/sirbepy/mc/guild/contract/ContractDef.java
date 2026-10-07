package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;

import java.util.List;

/**
 * A kind of contract. {@code rank} is the board it is drawn for; {@code effort} scales a daily's
 * coins and reputation around its rank's base. Trials with {@code noDeath} lose all progress on death.
 */
public record ContractDef(String id, Type type, Rank rank, double effort, String title, Material icon,
                          List<Objective> objectives, boolean noDeath) {

    public enum Type { DAILY, WEEKLY, RARE, TRIAL }

    public ContractDef {
        objectives = List.copyOf(objectives);
    }

    public boolean hasGather() {
        return objectives.stream().anyMatch(o -> o.kind() == Objective.Kind.GATHER);
    }
}
