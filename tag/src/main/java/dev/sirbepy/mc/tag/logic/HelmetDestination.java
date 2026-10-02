package dev.sirbepy.mc.tag.logic;

/** Where a displaced helmet goes when a player becomes IT: inventory, then ender chest, then the ground. */
public enum HelmetDestination {
    INVENTORY,
    ENDER_CHEST,
    DROP;

    public static HelmetDestination decide(boolean inventoryHasSpace, boolean enderChestHasSpace) {
        if (inventoryHasSpace) {
            return INVENTORY;
        }
        if (enderChestHasSpace) {
            return ENDER_CHEST;
        }
        return DROP;
    }
}
