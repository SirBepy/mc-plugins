package dev.sirbepy.mc.haru.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Axolotl;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small builders for the vanilla items Haru hands out, so the catalogs read as a list of wares. */
public final class Goods {

    public static final TextColor SAKURA = TextColor.color(0xFFB7C5);
    public static final TextColor PETAL = TextColor.color(0xF48FB1);
    public static final TextColor LEGEND = TextColor.color(0xFFC857);
    public static final TextColor LORE = TextColor.color(0xB8A9B8);

    private Goods() {
    }

    public static ItemStack of(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    public static Component text(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Sets a non-italic name and grey lore lines. */
    public static ItemStack named(ItemStack item, String name, TextColor color, String... lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, color));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(text(line, LORE));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** An enchanted book holding every given enchantment at the given level. */
    public static ItemStack book(Object... enchantsAndLevels) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        pairs(enchantsAndLevels).forEach((enchantment, level) -> meta.addStoredEnchant(enchantment, level, true));
        item.setItemMeta(meta);
        return item;
    }

    /** A gear piece with the given enchantments applied directly. */
    public static ItemStack enchanted(Material material, Object... enchantsAndLevels) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        pairs(enchantsAndLevels).forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
        item.setItemMeta(meta);
        return item;
    }

    private static Map<Enchantment, Integer> pairs(Object... enchantsAndLevels) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < enchantsAndLevels.length; i += 2) {
            result.put((Enchantment) enchantsAndLevels[i], (Integer) enchantsAndLevels[i + 1]);
        }
        return result;
    }

    public static ItemStack dyedLeather(Material material, int rgb) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(rgb));
        item.setItemMeta(meta);
        return item;
    }

    /** The rare blue axolotl, in a bucket. */
    public static ItemStack blueAxolotl() {
        ItemStack item = new ItemStack(Material.AXOLOTL_BUCKET);
        AxolotlBucketMeta meta = (AxolotlBucketMeta) item.getItemMeta();
        meta.setVariant(Axolotl.Variant.BLUE);
        item.setItemMeta(meta);
        return item;
    }

    /** A pink-on-white blossom banner. */
    public static ItemStack sakuraBanner() {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) item.getItemMeta();
        meta.addPattern(new Pattern(DyeColor.PINK, PatternType.FLOWER));
        meta.addPattern(new Pattern(DyeColor.MAGENTA, PatternType.CIRCLE));
        meta.addPattern(new Pattern(DyeColor.PINK, PatternType.GRADIENT));
        meta.addPattern(new Pattern(DyeColor.BROWN, PatternType.BORDER));
        item.setItemMeta(meta);
        return item;
    }

    /** Flight-2 rockets that burst into pink and white blossoms. */
    public static ItemStack hanabi(int amount) {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET, amount);
        FireworkMeta meta = (FireworkMeta) item.getItemMeta();
        meta.setPower(2);
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.fromRGB(0xFFB7C5), Color.WHITE)
                .withFade(Color.fromRGB(0xF48FB1))
                .withTrail()
                .build());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * A player head wearing the skin at {@code textures.minecraft.net/texture/<hash>}. The profile id
     * is derived from the hash so the same head stacks with itself across restarts.
     */
    public static ItemStack head(String textureHash) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(textureHash.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", textureValue(textureHash)));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    static String textureValue(String textureHash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textureHash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
