package dev.sirbepy.mc.haru.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Speech;
import dev.sirbepy.mc.haru.curio.MysteryCurios;
import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.item.Heads;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Offer;
import dev.sirbepy.mc.haru.shop.Payment;
import dev.sirbepy.mc.haru.shop.Shop;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds every Haru screen and runs what their buttons do. Guests only ever see the Everyday Wares,
 * bought on their own stock ledger at Stranger prices; while the shop is shut after the owner's death
 * the owner only sees his requests.
 */
public final class HaruMenus {

    public enum Screen { MAIN, EVERYDAY, WANDERING, PETALS, QUESTS, KEEPSAKES, SECRET }

    static final int BACK_SLOT = 45;
    private static final int[] INNER = innerSlots();

    private final HaruContext ctx;
    private final QuestMenu quests;
    private final KeepsakeMenu keepsakes;

    public HaruMenus(HaruContext ctx) {
        this.ctx = ctx;
        this.quests = new QuestMenu(ctx, this);
        this.keepsakes = new KeepsakeMenu(ctx, this);
    }

    public Menu open(Player player, Screen screen) {
        Menu menu = build(player, screen);
        player.openInventory(menu.getInventory());
        return menu;
    }

    /** Redraws the screen the player is looking at without closing it. */
    void refresh(Player player, Menu shown, Screen screen) {
        shown.adopt(build(player, screen));
    }

    public Menu build(Player player, Screen requested) {
        boolean owner = ctx.isOwner(player);
        OwnerState state = owner ? ctx.ownerState() : ctx.store().state(player.getUniqueId());
        Screen screen = requested;
        if (!owner) {
            screen = Screen.EVERYDAY;
        } else if (closed() && screen != Screen.QUESTS) {
            screen = Screen.QUESTS;
        }
        return switch (screen) {
            case MAIN -> main(player, state);
            case EVERYDAY -> shelf(player, state, Screen.EVERYDAY, "Everyday Wares",
                    ctx.shop().everyday(state.tier()), owner && ctx.shop().everydayHasLocked(state.tier()), List.of());
            case WANDERING -> wandering(player, state);
            case PETALS -> petals(player, state);
            case QUESTS -> quests.build(player, state);
            case KEEPSAKES -> keepsakes.build(player, state);
            case SECRET -> state.tier().isMax()
                    ? shelf(player, state, Screen.SECRET, "Secret Stock", ctx.shop().secretStock(state.tier()), false, List.of())
                    : main(player, state);
        };
    }

    private Menu main(Player player, OwnerState state) {
        Menu menu = new Menu(6, Component.text("✿ Haru's Sanctuary ✿", Speech.HARU));
        menu.set(4, portrait(player, state));
        menu.set(20, Icons.button(Material.EMERALD, "Everyday Wares", Goods.SAKURA,
                "Cheap, honest, always on the shelf.", "Restocked every dawn."), go(player, Screen.EVERYDAY));
        List<String> wanderLore = new ArrayList<>(List.of("Rarer things that come and go.",
                "New wares in " + ctx.shop().daysUntilRotation() + " Minecraft day(s)."));
        if (ctx.shop().legendary().isPresent()) {
            wanderLore.add("✦ Something legendary is on the shelf ✦");
        }
        menu.set(22, Icons.button(Material.LANTERN, "Wandering Wares", Goods.SAKURA, wanderLore.toArray(String[]::new)),
                go(player, Screen.WANDERING));
        long ready = state.board().stream().filter(q -> !q.claimed()).count();
        menu.set(24, Icons.button(Material.WRITABLE_BOOK, "Spirit Requests", Goods.SAKURA,
                "Small favours, paid in Spirit Petals and trust.", ready + " of 3 still open."), go(player, Screen.QUESTS));
        menu.set(29, Icons.button(Goods.head(Heads.PINK_ORB), "Petal Exchange", Goods.PETAL,
                List.of("Spend Spirit Petals on cosmetics,", "collectibles, blessings and more.",
                        "You have " + state.petals() + " Spirit Petals.")), go(player, Screen.PETALS));
        menu.set(31, curioIcon(state), (shown, click) -> openCurio(player, shown));
        menu.set(33, Icons.button(Material.AMETHYST_CLUSTER, "Keepsakes", Goods.SAKURA,
                "Your auras and the Sanctuary collection."), go(player, Screen.KEEPSAKES));
        if (state.tier().isMax()) {
            menu.set(49, Goods.glint(Icons.button(Material.ENDER_CHEST, "Secret Stock", Goods.LEGEND,
                    "The drawer that opened for you.")), go(player, Screen.SECRET));
        } else {
            menu.set(49, Icons.button(Material.GRAY_DYE, "???", Goods.LORE, "A drawer that will not open. Yet."));
        }
        menu.set(53, Icons.button(Material.BARRIER, "Close", Goods.LORE), (shown, click) -> player.closeInventory());
        menu.frame(Material.PINK_STAINED_GLASS_PANE);
        return menu;
    }

    private ItemStack portrait(Player player, OwnerState state) {
        Tier tier = state.tier();
        String trust = tier.isMax() ? "Trust: " + tier.title() + " (" + state.reputation() + ")"
                : "Trust: " + tier.title() + " (" + state.reputation() + "/" + tier.next().threshold() + ")";
        return Icons.button(Goods.head(Heads.MONK), "Haru, Keeper of the Sakura Sanctuary", Goods.SAKURA, List.of(
                trust,
                "Spirit Petals: " + state.petals(),
                "Requests completed: " + state.questsCompleted(),
                "Emerald prices: " + tier.discountPercent() + "% off",
                "Emeralds on you: " + Payment.emeraldsHeld(player.getInventory())));
    }

    private ItemStack curioIcon(OwnerState state) {
        LocalDate today = ctx.clock().today();
        MysteryCurios.Curio curio = ctx.curios().today(state.tier(), today);
        int left = MysteryCurios.DAILY_LIMIT - ctx.curios().boughtToday(state, today);
        return Icons.button(Goods.head(Heads.PRESENT), "Mystery Curio: " + curio.name(), Goods.PETAL, List.of(
                curio.lore(), "Nobody knows what is inside.", "Price: " + curio.petals() + " Spirit Petals",
                left > 0 ? left + " left today. Click to buy and open." : "None left today."));
    }

    private Menu wandering(Player player, OwnerState state) {
        Optional<Offer> legendary = ctx.shop().legendary();
        return shelf(player, state, Screen.WANDERING,
                "Wandering Wares (" + ctx.shop().daysUntilRotation() + " day(s) left)",
                ctx.shop().wandering(state.tier()), false, legendary.map(List::of).orElse(List.of()));
    }

    private Menu petals(Player player, OwnerState state) {
        Menu menu = shelf(player, state, Screen.PETALS, "Petal Exchange (" + state.petals() + " petals)",
                ctx.shop().petalExchange(state.tier()), ctx.shop().petalsHaveLocked(state.tier()), List.of());
        List<Offer> collectibles = ctx.shop().collectiblesToday();
        menu.set(47, Icons.button(Material.PAINTING, "Today's collectibles →", Goods.SAKURA,
                "Three pieces of the Sanctuary", "collection, different each day."));
        int slot = 48;
        for (Offer offer : collectibles) {
            menu.set(slot++, Icons.offer(ctx.shop(), state, offer), buyAction(player, state, offer, Screen.PETALS));
        }
        return menu;
    }

    /** A screen of offers in the inner slots, legendary ones first, plus a hint if more are locked. */
    private Menu shelf(Player player, OwnerState state, Screen screen, String title, List<Offer> offers,
                       boolean moreLocked, List<Offer> legendaries) {
        Menu menu = new Menu(6, Component.text(title, Speech.HARU));
        int index = 0;
        for (Offer offer : legendaries) {
            ItemStack icon = Goods.glint(Icons.offer(ctx.shop(), state, offer));
            menu.set(INNER[index++], icon, buyAction(player, state, offer, screen));
        }
        for (Offer offer : offers) {
            if (index >= INNER.length) {
                break;
            }
            menu.set(INNER[index++], Icons.offer(ctx.shop(), state, offer), buyAction(player, state, offer, screen));
        }
        if (moreLocked && index < INNER.length) {
            menu.set(INNER[index], Icons.button(Material.PAPER, "More, when Haru trusts you more", Goods.LORE,
                    "Finish his Spirit Requests."));
        }
        addBack(menu, player);
        menu.frame(Material.PINK_STAINED_GLASS_PANE);
        return menu;
    }

    private Menu.Action buyAction(Player player, OwnerState state, Offer offer, Screen screen) {
        return (shown, click) -> {
            List<String> given = new ArrayList<>();
            Shop.Result result = ctx.shop().buy(player, state, offer, given);
            switch (result) {
                case BOUGHT -> {
                    if (!given.isEmpty()) {
                        Speech.note(player, "Haru hands you: " + String.join(", ", given));
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
                    ctx.progression().checkMilestones(player, state);
                    ctx.store().save();
                }
                case LOCKED -> Speech.say(player, "Not that one. Not yet.");
                case SOLD_OUT -> Speech.note(player, "Sold out, " + Icons.restockText(offer.restock()) + ".");
                case NO_EMERALDS -> Speech.note(player, "That costs " + ctx.shop().emeraldPrice(state, offer)
                        + " emeralds. You have " + Payment.emeraldsHeld(player.getInventory()) + ".");
                case NO_PETALS -> Speech.note(player, "That costs " + offer.petals() + " Spirit Petals. You have "
                        + state.petals() + ".");
            }
            if (result != Shop.Result.BOUGHT) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            refresh(player, shown, screen);
        };
    }

    private void openCurio(Player player, Menu shown) {
        OwnerState state = ctx.ownerState();
        List<String> given = new ArrayList<>();
        MysteryCurios.Result result = ctx.curios().open(player, state, ctx.clock().today(), ctx.random(), given);
        switch (result) {
            case OPENED -> {
                Speech.note(player, "Inside the curio: " + String.join(", ", given));
                player.playSound(player.getLocation(), Sound.BLOCK_DECORATED_POT_INSERT, 1f, 1f);
                ctx.store().save();
            }
            case SOLD_OUT -> Speech.say(player, "No more curios today. I am not made of boxes.");
            case NO_PETALS -> Speech.note(player, "Not enough Spirit Petals (you have " + state.petals() + ").");
        }
        refresh(player, shown, Screen.MAIN);
    }

    Menu.Action go(Player player, Screen screen) {
        return (shown, click) -> open(player, screen);
    }

    /** Back to the main menu, or a plain Close for guests and while the shop is shut. */
    void addBack(Menu menu, Player player) {
        if (!ctx.isOwner(player) || closed()) {
            menu.set(BACK_SLOT, Icons.button(Material.BARRIER, "Close", Goods.LORE), (shown, click) -> player.closeInventory());
        } else {
            menu.set(BACK_SLOT, Icons.button(Material.ARROW, "Back", Goods.LORE), go(player, Screen.MAIN));
        }
    }

    private boolean closed() {
        return ctx.ownerState().closed(ctx.clock().nowMillis());
    }

    /** The 7x4 block inside the border of a 6-row chest. */
    private static int[] innerSlots() {
        int[] slots = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * 9 + col;
            }
        }
        return slots;
    }
}
