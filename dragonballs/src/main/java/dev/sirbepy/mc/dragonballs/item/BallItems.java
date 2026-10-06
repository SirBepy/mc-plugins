package dev.sirbepy.mc.dragonballs.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Builds and identifies Dragon Ball items/blocks. Balls are identified ONLY by the
 * {@code dragonballs:star} PDC marker, never by display name or texture, so a renamed or
 * re-textured copy still counts (and a lookalike without the marker never does).
 */
public final class BallItems {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    // A fixed UUID per star (derived from the star number, not random) so the same profile is used
    // across restarts; Mojang's session server never has to resolve it since the texture property
    // is set directly.
    private static final UUID[] PROFILE_IDS = {
            UUID.fromString("00000000-0000-4000-8000-000000000001"),
            UUID.fromString("00000000-0000-4000-8000-000000000002"),
            UUID.fromString("00000000-0000-4000-8000-000000000003"),
            UUID.fromString("00000000-0000-4000-8000-000000000004"),
            UUID.fromString("00000000-0000-4000-8000-000000000005"),
            UUID.fromString("00000000-0000-4000-8000-000000000006"),
            UUID.fromString("00000000-0000-4000-8000-000000000007"),
    };
    private static final UUID STONE_PROFILE_ID = UUID.fromString("00000000-0000-4000-8000-00000000005e");

    private final NamespacedKey starKey;
    private final NamespacedKey stoneKey;
    private final Supplier<DragonBallsConfig> config;

    public BallItems(JavaPlugin plugin, Supplier<DragonBallsConfig> config) {
        this.starKey = new NamespacedKey(plugin, "star");
        this.stoneKey = new NamespacedKey(plugin, "stone");
        this.config = config;
    }

    public NamespacedKey starKey() {
        return starKey;
    }

    /** True for a ball item, stone or active: every carrying rule (containers, slowness, death
     * drops, no despawn) applies to both the same way. */
    public boolean isBall(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(starKey, PersistentDataType.INTEGER);
    }

    public OptionalInt starOf(ItemStack item) {
        if (!isBall(item)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(item.getItemMeta().getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER));
    }

    /** True only for the dormant, stone-textured variant; a plain {@link #isBall} ball is active. */
    public boolean isStoneItem(ItemStack item) {
        return isBall(item)
                && item.getItemMeta().getPersistentDataContainer().has(stoneKey, PersistentDataType.BOOLEAN);
    }

    public boolean isBallBlock(Skull skull) {
        return skull != null && skull.getPersistentDataContainer().has(starKey, PersistentDataType.INTEGER);
    }

    public OptionalInt starOfBlock(Skull skull) {
        if (!isBallBlock(skull)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(skull.getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER));
    }

    /** The active item a player carries/finds: textured, named, marked. */
    public ItemStack createItem(int star) {
        return buildItem(star, false);
    }

    /** The dormant look a stone ball drops as when broken; same star, carrying rules, everything -
     * just a different texture/name and the extra {@code dragonballs:stone} PDC flag. */
    public ItemStack createStoneItem(int star) {
        return buildItem(star, true);
    }

    /** Picks the right variant by {@code stone}, for call sites that already have the flag at hand
     * (breaking a block, sweeping a stone-ended inventory) instead of two separate branches. */
    public ItemStack itemFor(int star, boolean stone) {
        return buildItem(star, stone);
    }

    private ItemStack buildItem(int star, boolean stone) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        UUID profileId = stone ? STONE_PROFILE_ID : PROFILE_IDS[star - 1];
        String texture = stone ? config.get().stoneTexture() : config.get().texture(star);
        applyProfile(meta, profileId, texture);
        if (stone) {
            meta.displayName(Component.text(star + "-Star Dragon Ball (stone)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Dormant. It wakes in a few days.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(stoneKey, PersistentDataType.BOOLEAN, true);
        } else {
            meta.displayName(Component.text(star + "-Star Dragon Ball", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Carrying it slows you down.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, star);
        item.setItemMeta(meta);
        return item;
    }

    /** Marks a placed head block as star N, so phase 2's radar/summon logic can find it by world scan. */
    public void markBlock(Skull skull, int star) {
        skull.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, star);
    }

    /** Swaps a placed block's texture between its star look and the stone (cooling-down) look. */
    public void applyBlockTexture(Skull skull, int star, boolean stone) {
        String texture = stone ? config.get().stoneTexture() : config.get().texture(star);
        UUID profileId = stone ? STONE_PROFILE_ID : PROFILE_IDS[star - 1];
        PlayerProfile profile = applyProfileTexture(profileId, texture);
        if (profile != null) {
            skull.setPlayerProfile(profile);
        }
    }

    private void applyProfile(SkullMeta meta, UUID profileId, String texture) {
        PlayerProfile profile = applyProfileTexture(profileId, texture);
        if (profile != null) {
            meta.setPlayerProfile(profile);
        }
    }

    private PlayerProfile applyProfileTexture(UUID profileId, String texture) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        PlayerProfile profile = Bukkit.createProfile(profileId);
        profile.setProperty(new ProfileProperty("textures", texture));
        return profile;
    }
}
