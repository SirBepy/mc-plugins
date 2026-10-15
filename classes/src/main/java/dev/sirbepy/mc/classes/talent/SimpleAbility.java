package dev.sirbepy.mc.classes.talent;

import org.bukkit.entity.Player;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** An {@link Ability} from lambdas, so a kit can declare one in a few lines. */
public record SimpleAbility(String name, Function<Player, String> describe, ToIntFunction<Player> cooldown,
                            Predicate<Player> caster, boolean passive) implements Ability {

    public static SimpleAbility of(String name, int cooldownSeconds, Function<Player, String> describe, Predicate<Player> caster) {
        return new SimpleAbility(name, describe, p -> cooldownSeconds, caster, false);
    }

    /** An ultimate: the cooldown is always {@link Slot#ULTIMATE_COOLDOWN_SECONDS}. */
    public static SimpleAbility ultimate(String name, Function<Player, String> describe, Predicate<Player> caster) {
        return new SimpleAbility(name, describe, p -> Slot.ULTIMATE_COOLDOWN_SECONDS, caster, false);
    }

    /** A passive keystone that leaves its slot without a button. */
    public static SimpleAbility passive(String name, String what) {
        return new SimpleAbility(name, p -> what, p -> 0, p -> false, true);
    }

    @Override
    public String describe(Player player) {
        return describe.apply(player);
    }

    @Override
    public int cooldownSeconds(Player player) {
        return cooldown.applyAsInt(player);
    }

    @Override
    public boolean cast(Player player) {
        return caster.test(player);
    }
}
