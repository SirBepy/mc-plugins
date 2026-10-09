package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.item.Heads;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Dragon/End-themed trophy heads a Dragon Hoard roll can unlock (Joe's reward spec, 2026-10-09),
 * wired through {@code core.item.Heads} exactly the way haru's own {@code item.Heads} is (see
 * {@code docs/haru.md}'s "Sanctuary collection" section).
 *
 * <p>The 5 hashes below come from the community CSV mirror of minecraft-heads.com's own public
 * API ({@code https://cdn.jsdelivr.net/gh/TheLuca98/MinecraftHeads@master/heads.csv}, fetched
 * 2026-10-09) and were verified live this session by resolving every one of them against Mojang's
 * CDN ({@code https://textures.minecraft.net/texture/<hash>} - all 5 returned 200). That is the
 * only verification this comment claims: minecraft-heads.com's own site could not be fetched
 * directly to confirm an exact page URL - both {@code WebFetch} and a direct {@code curl} to
 * {@code minecraft-heads.com} returned HTTP 403 with a Cloudflare bot challenge this session, for
 * every path tried, including the bare homepage. The {@code sourceUrl} below is therefore the
 * site's own search-by-name page for each head, not a fetched/verified permalink - flagged here so
 * nobody mistakes it for one.
 */
public final class DragonTrophyHeads {

    public record Head(String id, String displayName, String textureHash, String sourceUrl) {
        public ItemStack item() {
            ItemStack stack = Heads.texturedHead(UUID.nameUUIDFromBytes(textureHash.getBytes(StandardCharsets.UTF_8)),
                    Heads.textureValue(textureHash));
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize("<dark_purple>" + displayName)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
            return stack;
        }
    }

    public static final List<Head> ALL = List.of(
            new Head("dragon_action_figure", "Dragon Action Figure",
                    "166fb41b885258b308be342d5952ea365e0332e097cda2dbd6c29749664ebfb2",
                    "https://minecraft-heads.com/custom-heads/decoration?filter-search=Dragon+Action+Figure"),
            new Head("enderman_knight_helmet", "Enderman Knight Helmet",
                    "f6295fa76afc76a5712ab5c95104d00dac8a474a67cbccdc04c2fde397f0326f",
                    "https://minecraft-heads.com/custom-heads/decoration?filter-search=Enderman+Knight+Helmet"),
            new Head("ender_plushie", "Ender Plushie",
                    "b0d7f17e729331c51f5bfa893765422a35b306aee95aef7bcec63e514ce31d99",
                    "https://minecraft-heads.com/custom-heads/decoration?filter-search=Ender+Plushie"),
            new Head("end_crystal_orb", "End Crystal Orb",
                    "96636ba6988ce9b40ddc749a09ce0fb939af526005995c18d323ac96625f0d6d",
                    "https://minecraft-heads.com/custom-heads/decoration?filter-search=End+Crystal+Orb"),
            new Head("end_portal_fumes", "End Portal Fumes",
                    "12ccb84f9c0cbb2eb7b9abbb7a4fc7b3186150d5e7db01852ac6d495de17e812",
                    "https://minecraft-heads.com/custom-heads/decoration?filter-search=End+Portal+Fumes"));

    private DragonTrophyHeads() {
    }

    public static Head byId(String id) {
        return ALL.stream().filter(h -> h.id().equals(id)).findFirst().orElse(null);
    }
}
