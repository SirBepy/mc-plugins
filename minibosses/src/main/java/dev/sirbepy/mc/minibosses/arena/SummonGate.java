package dev.sirbepy.mc.minibosses.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The arena rules every summon item goes through: where the Guardian may rise, and sealing the arena
 * once it has. Refusals stay vague on purpose (Joe, 2026-10-21): players who want the arena's
 * location can ask Shenron, who reads it from {@code /guardianadmin arena list}.
 */
public final class SummonGate {

    private final ArenaRegistry arenas;
    private final ArenaFights fights;

    public SummonGate(ArenaRegistry arenas, ArenaFights fights) {
        this.arenas = arenas;
        this.fights = fights;
    }

    /**
     * Where {@code player} may wake this Guardian: its arena's center, or their own feet while it
     * has no arena and may rise anywhere. Empty (and the player told why) when it can't rise here.
     * {@code wrongPlace} is the Guardian's own hint for standing outside its arena.
     */
    public Optional<Location> where(Player player, String guardianId, String wrongPlace, String notReady) {
        ArenaRegistry.Rule rule = arenas.rule(guardianId, player.getLocation());
        switch (rule) {
            case ANYWHERE -> {
                return Optional.of(player.getLocation());
            }
            case IN_ARENA -> {
                if (fights.busy(guardianId)) {
                    player.sendMessage(Component.text("It is already awake here.", NamedTextColor.GRAY));
                    return Optional.empty();
                }
                return arenas.get(guardianId).map(Arena::center);
            }
            case WRONG_PLACE -> player.sendMessage(Component.text(wrongPlace, NamedTextColor.GRAY));
            case NOT_READY -> player.sendMessage(Component.text(notReady, NamedTextColor.GRAY));
        }
        return Optional.empty();
    }

    /** Seals the arena around a Guardian that just rose in it; a Guardian risen outside any arena is left alone. */
    public void risen(String guardianId, LivingEntity boss) {
        arenas.get(guardianId).filter(arena -> arena.contains(boss.getLocation()))
                .ifPresent(arena -> fights.start(arena, boss));
    }
}
