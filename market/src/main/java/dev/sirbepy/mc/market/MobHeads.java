package dev.sirbepy.mc.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The generated {@code heads.yml}: every More Mob Heads head the Head collector buys, by mob, and how
 * to tell one in a player's hand from any other player head.
 */
public final class MobHeads {

    public record Head(String mob, String name, String texture, BigDecimal price) {
    }

    public record Mob(String id, String name, List<Head> heads) {

        public BigDecimal minPrice() {
            return heads.stream().map(Head::price).min(BigDecimal::compareTo).orElseThrow();
        }

        public BigDecimal maxPrice() {
            return heads.stream().map(Head::price).max(BigDecimal::compareTo).orElseThrow();
        }
    }

    private static final UUID NIL = new UUID(0, 0);

    private final Map<String, Mob> mobs = new LinkedHashMap<>();
    private final Map<String, Head> byTexture = new HashMap<>();

    public static MobHeads load(InputStream stream) {
        return new MobHeads(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public MobHeads(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("mobs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection mob = section.getConfigurationSection(id);
            List<Head> heads = new ArrayList<>();
            for (Map<?, ?> head : mob.getMapList("heads")) {
                // Via the decimal string, as in MarketLayout, so 0.51 stays 0.51.
                heads.add(new Head(id, String.valueOf(head.get("name")), String.valueOf(head.get("texture")),
                        new BigDecimal(String.valueOf(head.get("price")))));
            }
            if (!heads.isEmpty()) {
                mobs.put(id, new Mob(id, mob.getString("name", id), List.copyOf(heads)));
                heads.forEach(h -> byTexture.put(h.texture(), h));
            }
        }
    }

    /** Every mob, in file order. */
    public List<Mob> mobs() {
        return List.copyOf(mobs.values());
    }

    public Mob mob(String id) {
        return mobs.get(id);
    }

    /**
     * The mob head this stack is, or null. A More Mob Heads head is a player head whose profile has
     * only a skin texture; a real player's head (PvP drops, cartel trophies) also names its owner, so
     * it never counts, even if that player wears the same skin.
     */
    public Head identify(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PLAYER_HEAD || !(stack.getItemMeta() instanceof SkullMeta skull)) {
            return null;
        }
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null || (profile.getId() != null && !NIL.equals(profile.getId()))
                || (profile.getName() != null && !profile.getName().isEmpty())) {
            return null;
        }
        for (ProfileProperty property : profile.getProperties()) {
            if ("textures".equals(property.getName())) {
                String texture = textureHash(property.getValue());
                return texture == null ? null : byTexture.get(texture);
            }
        }
        return null;
    }

    /** The last segment of the skin URL inside a base64 {@code textures} value, or null if it has none. */
    static String textureHash(String value) {
        try {
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                    .getAsJsonObject("SKIN").get("url").getAsString();
            return url.substring(url.lastIndexOf('/') + 1);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
