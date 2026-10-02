package dev.sirbepy.mc.tag.logic;

/**
 * An IT change that touches a player's inventory (head swap, prefix) but could not be applied
 * while they were offline. Applied the moment they join.
 */
public enum PendingAction {
    BECOME_IT,
    CLEAR_IT
}
