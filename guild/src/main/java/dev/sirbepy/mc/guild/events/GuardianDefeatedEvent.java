package dev.sirbepy.mc.guild.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;
import java.util.UUID;

/**
 * Fired once a Three Ancient Guardians boss (the {@code minibosses} module) dies, so the Guild can
 * credit a {@code GUARDIAN}-kind contract without depending on {@code minibosses}' internal classes.
 *
 * <p>There's no existing cross-plugin event in this codebase to copy: EndFight and the Guild each
 * independently derive dragon-fight credit from the same vanilla {@code EntityDamageEvent}/
 * {@code EntityDeathEvent}, sharing only the attribution logic (core's {@code DragonFighters}), not
 * an event. That path doesn't reach here - a Guardian isn't a vanilla {@code EntityType}, so neither
 * plugin has a vanilla event carrying "this death was a Guardian, here's its id". A custom event is
 * the only mechanism left, and {@code minibosses} already declares a softdepend on {@code Guild}
 * (see its {@code plugin.yml}/module wiring), so having it call an event the Guild defines follows
 * the dependency direction already established between the two modules, rather than inventing a new
 * one or making the Guild depend on minibosses' boss classes.
 *
 * <p>{@code minibosses} has no real Guardian boss yet (todos 152-154); this event is the extension
 * point that future boss code fires on death. {@code tier} is carried as a plain string (not
 * minibosses' own {@code Tier} enum, to avoid the Guild depending on it) and is not currently used
 * for reward scaling - see {@code ProgressListener} for why.
 */
public final class GuardianDefeatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String guardianId;
    private final String tier;
    private final Set<UUID> fighterIds;

    public GuardianDefeatedEvent(String guardianId, String tier, Set<UUID> fighterIds) {
        this.guardianId = guardianId;
        this.tier = tier;
        this.fighterIds = Set.copyOf(fighterIds);
    }

    /** The minibosses registry id, e.g. {@code overgrown_sentinel}. */
    public String guardianId() {
        return guardianId;
    }

    /** The Guardian's tier (Normal/Heroic/Mythic) at the time it died; may be null. Unused for now. */
    public String tier() {
        return tier;
    }

    /** Everyone credited with fighting the Guardian (online or not); the Guild only pays online members. */
    public Set<UUID> fighterIds() {
        return fighterIds;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
