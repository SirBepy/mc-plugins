package dev.sirbepy.mc.tag.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Typed view over the `tracker:` section: IT's compass. Every template has a code default because
 * a server's existing plugins/Tag/config.yml predates this section and is never overwritten.
 */
public final class TrackerConfig {

    private static final String DEFAULT_NAME = "<green>☣ Tag Tracker</green>";
    private static final String DEFAULT_LORE = "<gray>Right-click to switch player</gray>";
    private static final String DEFAULT_DISTANCE = "<white><player></white> <gray>-</gray> <yellow><distance>m</yellow>";
    private static final String DEFAULT_OTHER_REALM = "<white><player></white> <gray>- Not in this realm</gray>";
    private static final String DEFAULT_AFK = "<white><player></white> <gray>- AFK, can't be tagged</gray>";
    private static final String DEFAULT_NOBODY = "<gray>No one to track</gray>";

    public final boolean enabled;
    public final long updateIntervalTicks;
    public final String itemName;
    public final String itemLore;
    public final String readoutDistance;
    public final String readoutOtherRealm;
    public final String readoutAfk;
    public final String readoutNobody;

    private TrackerConfig(boolean enabled, long updateIntervalTicks, String itemName, String itemLore,
                          String readoutDistance, String readoutOtherRealm, String readoutAfk, String readoutNobody) {
        this.enabled = enabled;
        this.updateIntervalTicks = updateIntervalTicks;
        this.itemName = itemName;
        this.itemLore = itemLore;
        this.readoutDistance = readoutDistance;
        this.readoutOtherRealm = readoutOtherRealm;
        this.readoutAfk = readoutAfk;
        this.readoutNobody = readoutNobody;
    }

    public static TrackerConfig from(ConfigurationSection section) {
        if (section == null) {
            return new TrackerConfig(true, 10, DEFAULT_NAME, DEFAULT_LORE,
                    DEFAULT_DISTANCE, DEFAULT_OTHER_REALM, DEFAULT_AFK, DEFAULT_NOBODY);
        }
        return new TrackerConfig(
                section.getBoolean("enabled", true),
                section.getLong("update-interval-ticks", 10),
                section.getString("item-name", DEFAULT_NAME),
                section.getString("item-lore", DEFAULT_LORE),
                section.getString("readout.distance", DEFAULT_DISTANCE),
                section.getString("readout.other-realm", DEFAULT_OTHER_REALM),
                section.getString("readout.afk", DEFAULT_AFK),
                section.getString("readout.nobody", DEFAULT_NOBODY));
    }
}
