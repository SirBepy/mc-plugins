package dev.sirbepy.mc.haru.item;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Textured heads: the collectible set sold for Spirit Petals, plus the menu icons. Hashes are
 * {@code textures.minecraft.net/texture/<hash>} ids from minecraft-heads.com (free-plugin use allowed),
 * each fetched from Mojang's CDN and eyeballed on 2026-10-06. Some are shorter than 64 hex digits on
 * purpose: Mojang serves them unpadded, and a zero-padded copy 404s.
 */
public final class Heads {

    /** One piece of the Sanctuary collection. */
    public record Collectible(String id, String name, String hash, String lore) {
        public ItemStack item() {
            return Goods.named(Goods.head(hash), name, Goods.SAKURA, lore, "Sanctuary Collection");
        }
    }

    public static final List<Collectible> COLLECTION = List.of(
            new Collectible("koi", "Koi of the Still Pond", "fb6b8cedfead4286f6c0bba32bbd365d2a8ca2f1eacd7b9d95147417dbbae9d4",
                    "It swam upstream for a hundred years."),
            new Collectible("lantern", "Paper Lantern", "26283e7a88d32719304a37ede0c6a8c5dc9d9cf9b00a179cf904e8ce821312",
                    "Lit for travellers who never came home."),
            new Collectible("bonsai", "Haru's Bonsai", "f12a3efc96bb7a0b291f373719ad2e56ead4e895ef9cf6da6d7aca9eefad73cd",
                    "Pruned every morning for three centuries."),
            new Collectible("maneki", "Maneki-neko", "d3529476e0f1dee8a1bd88c48b2d9ac57b826db62f5efa7cfd359c6fd828c8e",
                    "Its paw beckons good fortune."),
            new Collectible("sakura", "Sakura in Bloom", "6d0e77855d85b94987bd352aae16c67c95649863369b1b6c36362755a64a1214",
                    "A whole spring, folded small."),
            new Collectible("kitsune", "Kitsune Mask", "93de950f26f82e36347761976f02d7edc8b9ad8b93fdc4add3b76fa8fb02277",
                    "The fox who wore it still wants it back."),
            new Collectible("daruma", "Daruma", "228a3a083398a66cfb3409fd8d32826ec189368cc3308686da226f2621f1ed",
                    "Both eyes painted: a wish already kept."),
            new Collectible("mochi", "Moon Mochi", "2f7918f5f4829af9dda67a8f4e5aa1f68eda57fede664b6797b989727c5378c9",
                    "Pounded by the rabbit in the moon."),
            new Collectible("tanuki", "Tanuki", "2d5d9900b3abb6d1e045981c8075c35bf4659054e9bd7f39c6d4718a4b9d0bf2",
                    "Probably not a teapot. Probably."));

    public static final String MONK = "824f19da8d6203d896d83a81f114b86b1f73b447698c97da560f477a4cbe8";
    public static final String PINK_ORB = "c54deeeab3b9750c77642ec95e37fe2bdf9adc555e0826dedd769bedd10";
    public static final String PRESENT = "ed90816936569293a3824e6f1e7ea34a552fe90264b0352d914f8f645528b93d";
    public static final String CHERRY_TREE = "6d0e77855d85b94987bd352aae16c67c95649863369b1b6c36362755a64a1214";

    private Heads() {
    }

    public static Collectible collectible(String id) {
        return COLLECTION.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
