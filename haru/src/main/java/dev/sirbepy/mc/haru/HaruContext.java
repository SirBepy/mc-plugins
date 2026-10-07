package dev.sirbepy.mc.haru;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.curio.MysteryCurios;
import dev.sirbepy.mc.haru.data.HaruStore;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.effect.Blessings;
import dev.sirbepy.mc.haru.item.Bound;
import dev.sirbepy.mc.haru.item.HaruItems;
import dev.sirbepy.mc.haru.npc.HaruLook;
import dev.sirbepy.mc.haru.npc.HaruNpc;
import dev.sirbepy.mc.haru.quest.QuestBoard;
import dev.sirbepy.mc.haru.rep.Progression;
import dev.sirbepy.mc.haru.shop.Rewards;
import dev.sirbepy.mc.haru.shop.Shop;
import dev.sirbepy.mc.haru.time.HaruClock;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Haru's services, built once in {@code onEnable} (or by a test) and handed to listeners and menus. */
public final class HaruContext {

    private final Plugin plugin;
    private final Supplier<HaruConfig> config;
    private final HaruClock clock;
    private final HaruStore store;
    private final HaruItems items;
    private final Blessings blessings;
    private final Bound bound;
    private final Rewards rewards;
    private final Progression progression;
    private final Shop shop;
    private final QuestBoard board;
    private final MysteryCurios curios;
    private final HaruNpc npc;
    private final Random random = new Random();

    public HaruContext(Plugin plugin, Supplier<HaruConfig> config, HaruClock clock, HaruStore store,
                       Consumer<Player> celebrate) {
        this.plugin = plugin;
        this.config = config;
        this.clock = clock;
        this.store = store;
        this.items = new HaruItems(plugin);
        this.blessings = new Blessings(clock::nowMillis);
        this.bound = new Bound(plugin);
        this.rewards = new Rewards(blessings, bound, clock::nowMillis);
        this.progression = new Progression(celebrate);
        this.shop = new Shop(clock, config, rewards, items);
        this.board = new QuestBoard(progression);
        this.curios = new MysteryCurios(rewards);
        BedrockForms bedrock = BedrockForms.create(plugin);
        this.npc = new HaruNpc(plugin, store, bedrock::isBedrock, new HaruLook(config));
    }

    public boolean isOwner(Player player) {
        return config.get().isOwner(player.getUniqueId());
    }

    /** The owner's state, with a fresh quest board drawn if a new request set began. */
    public OwnerState ownerState() {
        OwnerState state = store.state(config.get().owner());
        if (board.ensureCurrentSet(state, clock.requestSet())) {
            store.save();
        }
        return state;
    }

    public String ownerName() {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(config.get().owner());
        return owner.getName() == null ? "the one who gathered the seven stars" : owner.getName();
    }

    public Plugin plugin() {
        return plugin;
    }

    public HaruConfig config() {
        return config.get();
    }

    public HaruClock clock() {
        return clock;
    }

    public HaruStore store() {
        return store;
    }

    public HaruItems items() {
        return items;
    }

    public Blessings blessings() {
        return blessings;
    }

    public Bound bound() {
        return bound;
    }

    public Rewards rewards() {
        return rewards;
    }

    public Progression progression() {
        return progression;
    }

    public Shop shop() {
        return shop;
    }

    public QuestBoard board() {
        return board;
    }

    public MysteryCurios curios() {
        return curios;
    }

    public HaruNpc npc() {
        return npc;
    }

    public Random random() {
        return random;
    }
}
