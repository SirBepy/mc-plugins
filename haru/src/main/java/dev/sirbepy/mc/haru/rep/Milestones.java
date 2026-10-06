package dev.sirbepy.mc.haru.rep;

import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.item.Heads;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/** The one-off gifts behind {@link Progression}'s hidden milestones. */
final class Milestones {

    private Milestones() {
    }

    static ItemStack finalPetal(String ownerName) {
        return Goods.glint(Goods.named(Goods.head(Heads.CHERRY_TREE), "The Final Petal", Goods.LEGEND,
                "The last petal of Haru's old tree,",
                "kept three hundred years for " + ownerName + "."));
    }

    static ItemStack portrait() {
        return Goods.named(Goods.head(Heads.MONK), "Portrait of Haru", Goods.LEGEND,
                "He insists it is a good likeness.", "Sanctuary Collection");
    }

    static ItemStack journal(String ownerName) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Haru's Journal");
        meta.setAuthor("Haru");
        List<String> pages = List.of(
                "Spring, the first year.\n\nA girl planted me on a hill and said: grow tall enough to see the sea. "
                        + "I tried. It took eighty years. She did not see it, but her granddaughter did.",
                "The keepers came after that. One sang to the koi. One swept my petals into the shapes of animals. "
                        + "One only ever came to sleep in my shade. I liked him best.",
                "Then the hill went quiet. No keepers, no songs. I slept, and moss grew on my dreams.",
                "Then a voice like thunder, rolling out of seven stars: wake, and keep this place for "
                        + ownerName + ".\n\nI woke with hands. I am still getting used to them.",
                "If you are reading this, " + ownerName + ", you have done twenty-five things I asked "
                        + "and never asked why. The old keepers would have liked you.\n\n- Haru");
        // Plain-text pages (no formatting), so the legacy string setter is enough and MockBukkit supports it.
        meta.setPages(pages);
        book.setItemMeta(meta);
        return book;
    }
}
