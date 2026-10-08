package dev.sirbepy.mc.dragonballs.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.sirbepy.mc.core.item.Heads;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Builds and identifies Dragon Ball items/blocks, across all 3 sets (overworld/nether/end). Balls
 * are identified ONLY by the {@code dragonballs:star} PDC marker, never by display name or texture,
 * so a renamed or re-textured copy still counts (and a lookalike without the marker never does).
 * The companion {@code dragonballs:set} PDC marker tells WHICH of the 3 it belongs to; its absence
 * means OVERWORLD, which is what every ball predating this 3-set split (the live balls.yml, and any
 * carried copy of one) already is.
 */
public final class BallItems {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    // A fixed UUID per star (derived from the star number, not random) so the same profile is used
    // across restarts; Mojang's session server never has to resolve it since the texture property
    // is set directly. Kept exactly as before the 3-set split: overworld items must stay identical.
    private static final UUID[] OVERWORLD_PROFILE_IDS = {
            UUID.fromString("00000000-0000-4000-8000-000000000001"),
            UUID.fromString("00000000-0000-4000-8000-000000000002"),
            UUID.fromString("00000000-0000-4000-8000-000000000003"),
            UUID.fromString("00000000-0000-4000-8000-000000000004"),
            UUID.fromString("00000000-0000-4000-8000-000000000005"),
            UUID.fromString("00000000-0000-4000-8000-000000000006"),
            UUID.fromString("00000000-0000-4000-8000-000000000007"),
    };
    // Shared across sets: the stone look is the same dormant texture regardless of which set it came from.
    private static final UUID STONE_PROFILE_ID = UUID.fromString("00000000-0000-4000-8000-00000000005e");

    private final NamespacedKey starKey;
    private final NamespacedKey setKey;
    private final NamespacedKey stoneKey;
    private final Supplier<DragonBallsConfig> config;

    public BallItems(JavaPlugin plugin, Supplier<DragonBallsConfig> config) {
        this.starKey = new NamespacedKey(plugin, "star");
        this.setKey = new NamespacedKey(plugin, "set");
        this.stoneKey = new NamespacedKey(plugin, "stone");
        this.config = config;
    }

    public NamespacedKey starKey() {
        return starKey;
    }

    /** True for a ball item, stone or active, of any set: every carrying rule (containers, slowness,
     * death drops, no despawn) applies to all 3 the same way. */
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

    /** Which of the 3 sets {@code item} belongs to; {@link BallSet#OVERWORLD} for a non-ball item or
     * one with no {@code set} PDC entry (every ball that predates this split). */
    public BallSet setOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return BallSet.OVERWORLD;
        }
        return fromId(item.getItemMeta().getPersistentDataContainer().get(setKey, PersistentDataType.STRING));
    }

    /** True only for the dormant, stone-textured variant; a plain {@link #isBall} ball is active. */
    public boolean isStoneItem(ItemStack item) {
        return isBall(item)
                && item.getItemMeta().getPersistentDataContainer().has(stoneKey, PersistentDataType.BOOLEAN);
    }

    /** A head block left by a ball placed before balls floated; only ever cleared, never made. */
    public boolean isBallBlock(Skull skull) {
        return skull != null && skull.getPersistentDataContainer().has(starKey, PersistentDataType.INTEGER);
    }

    private static BallSet fromId(String id) {
        if (id == null) {
            return BallSet.OVERWORLD;
        }
        BallSet set = BallSet.byId(id);
        return set == null ? BallSet.OVERWORLD : set;
    }

    /** The active item a player carries/finds: textured, named, marked. */
    public ItemStack createItem(BallSet set, int star) {
        return buildItem(set, star, false);
    }

    /** The dormant look a stone ball drops as when broken; same set/star, carrying rules, everything -
     * just a different texture/name and the extra {@code dragonballs:stone} PDC flag. */
    public ItemStack createStoneItem(BallSet set, int star) {
        return buildItem(set, star, true);
    }

    /** Picks the right variant by {@code stone}, for call sites that already have the flag at hand
     * (breaking a block, sweeping a stone-ended inventory) instead of two separate branches. */
    public ItemStack itemFor(BallSet set, int star, boolean stone) {
        return buildItem(set, star, stone);
    }

    private ItemStack buildItem(BallSet set, int star, boolean stone) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        UUID profileId = stone ? STONE_PROFILE_ID : profileId(set, star);
        String texture = stone ? config.get().stoneTexture() : config.get().texture(set, star);
        applyProfile(meta, profileId, texture);
        String name = ballName(set, star);
        if (stone) {
            meta.displayName(Component.text(name + " (stone)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Dormant. It wakes in a few days.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(stoneKey, PersistentDataType.BOOLEAN, true);
        } else {
            meta.displayName(Component.text(name, set.color()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Carrying it slows you down.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, star);
        meta.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, set.id());
        CustomModelDataComponent look = meta.getCustomModelDataComponent();
        look.setStrings(List.of(modelId(set, star, stone)));
        meta.setCustomModelDataComponent(look);
        item.setItemMeta(meta);
        return item;
    }

    /** The server pack's player_head select key: the rounded 3D ball model, while a client without
     * the pack (and Bedrock) falls back to the plain head with this item's skin. */
    public static String modelId(BallSet set, int star, boolean stone) {
        return stone ? "dragonball_stone" : "dragonball_" + set.id() + "_" + star;
    }

    /** True if {@code player} has {@code set}'s {@code star} ball anywhere they can hold one. */
    public boolean carries(Player player, BallSet set, int star) {
        // getContents covers storage, armor and offhand; the cursor holds a ball mid-click.
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isBall(stack, set, star)) {
                return true;
            }
        }
        return isBall(player.getItemOnCursor(), set, star);
    }

    public boolean isBall(ItemStack item, BallSet set, int star) {
        return isBall(item) && setOf(item) == set && starOf(item).getAsInt() == star;
    }

    /** False for a ball item made before the 3D look existed (no model id, or a stale one). */
    public boolean hasCurrentLook(ItemStack item) {
        if (!isBall(item)) {
            return true;
        }
        var meta = item.getItemMeta();
        int star = starOf(item).getAsInt();
        return meta.hasCustomModelDataComponent()
                && meta.getCustomModelDataComponent().getStrings().contains(modelId(setOf(item), star, isStoneItem(item)));
    }

    /** "<n>-Star Dragon Ball" for overworld (byte-identical to before this split), "<n>-Star Nether
     * Dragon Ball" / "<n>-Star End Dragon Ball" otherwise. */
    private static String ballName(BallSet set, int star) {
        String word = set.displayWord();
        return star + "-Star " + (word.isEmpty() ? "" : word + " ") + "Dragon Ball";
    }

    /** Overworld keeps its original hardcoded UUIDs; nether/end derive a deterministic (stable
     * across restarts), set-distinct UUID from the (set, star) pair instead - there's no need for
     * anyone to ever type these by hand the way the overworld ones occasionally get referenced. */
    private static UUID profileId(BallSet set, int star) {
        if (set == BallSet.OVERWORLD) {
            return OVERWORLD_PROFILE_IDS[star - 1];
        }
        return UUID.nameUUIDFromBytes(("dragonballs:" + set.id() + ":star:" + star).getBytes(StandardCharsets.UTF_8));
    }

    private void applyProfile(SkullMeta meta, UUID profileId, String texture) {
        PlayerProfile profile = Heads.texturedProfile(profileId, texture);
        if (profile != null) {
            meta.setPlayerProfile(profile);
        }
    }
}
