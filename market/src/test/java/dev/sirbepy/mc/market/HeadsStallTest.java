package dev.sirbepy.mc.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Heads stall's 4 signs, replacing HeadCollector (removed 2026-10-09). Uses a YAML fixture for
 * stalls.yml, not the shipped resource: server/spawn-market/generate.mjs is regenerating that file
 * to the new head-stall/wanted-board/shops schema at the same time as this change.
 */
class HeadsStallTest {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            head-stall:
              name: Heads
              color: red
              slots:
                - {x: 10, y: 70, z: 0, facing: south}
                - {x: 11, y: 70, z: 0, facing: south}
                - {x: 12, y: 70, z: 0, facing: south}
                - {x: 13, y: 70, z: 0, facing: south}
            stalls:
              mob_drops:
                name: Mob drops
                color: green
                slots:
                  - {x: 5, y: 70, z: 0, facing: north}
                  - {x: 6, y: 70, z: 0, facing: north}
                  - {x: 7, y: 70, z: 0, facing: north}
                buys:
                  - {item: rotten_flesh, amount: 64, price: 0.64}
                  - {item: zombie_head, amount: 1, price: 10}
            """;

    @TempDir
    File dir;

    private ServerMock server;
    private MobHeads heads;
    private MarketConfig config;
    private Market market;
    private HeadsStall stall;
    private PluginMock plugin;
    private final List<Double> paid = new ArrayList<>();
    private boolean bankOpen = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        heads = MobHeads.load(Objects.requireNonNull(getClass().getResourceAsStream("/heads.yml")));
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        config = new MarketConfig(yaml);
        MarketLayout layout = MarketLayout.from(YamlConfiguration.loadConfiguration(new StringReader(STALLS)));
        market = new Market(layout, heads, () -> config, () -> "day-1", new Random(7), new File(dir, "state.yml"),
                Logger.getLogger("heads-stall-test"));
        market.refresh();
        plugin = MockBukkit.createMockPlugin();
        stall = new HeadsStall(plugin, market, () -> config, (player, amount) -> {
            if (bankOpen) {
                paid.add(amount);
            }
            return bankOpen;
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A player head as More Mob Heads drops it: a skin texture and, unless given, no owner. */
    static ItemStack head(UUID owner, String name, String texture, int amount) {
        ItemStack stack = ItemStack.of(Material.PLAYER_HEAD, amount);
        PlayerProfile profile = Bukkit.createProfileExact(owner, name);
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + texture + "\"}}}";
        profile.setProperty(new ProfileProperty("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
        stack.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return stack;
    }

    private MobHeads.Head wantedHead() {
        return market.wantedMobs().getFirst().heads().getFirst();
    }

    private MobHeads.Head unwantedHead() {
        return heads.mobs().stream().filter(m -> !market.wantedMobs().contains(m)).findFirst().orElseThrow().heads().getFirst();
    }

    @Test
    void writesOneSignPerWantedMobAndClearsSpareSlotsOnRotation() {
        WorldMock world = server.addSimpleWorld("world");
        stall.apply(world);
        List<MobHeads.Mob> wanted = market.wantedMobs();
        assertEquals(4, wanted.size(), "default heads-per-day (4) matches the 4 signs");
        for (int i = 0; i < 4; i++) {
            var block = world.getBlockAt(10 + i, 70, 0);
            assertEquals(Material.SPRUCE_WALL_SIGN, block.getType());
            assertEquals(BlockFace.SOUTH, ((Directional) block.getBlockData()).getFacing());
            Sign sign = (Sign) block.getState();
            String mobName = wanted.get(i).name();
            assertEquals(Component.text(mobName.length() <= 15 ? mobName : mobName.substring(0, 15)).decorate(TextDecoration.BOLD),
                    sign.getSide(Side.FRONT).line(0), wanted.get(i).id());
            assertEquals(Component.text("head"), sign.getSide(Side.FRONT).line(1));
            assertEquals(Component.text("Right-click"), sign.getSide(Side.FRONT).line(3));
            assertTrue(sign.getSide(Side.FRONT).isGlowingText());
        }

        // Fewer wanted mobs than slots (e.g. a smaller catalogue some day) clears the spare spots.
        market = new Market(market.layout(), new MobHeads(new YamlConfiguration()), () -> config, () -> "day-2",
                new Random(0), new File(dir, "state2.yml"), Logger.getLogger("heads-stall-test-2"));
        market.refresh();
        stall = new HeadsStall(plugin, market, () -> config, (p, a) -> true);
        stall.apply(world);
        for (int i = 0; i < 4; i++) {
            assertEquals(Material.AIR, world.getBlockAt(10 + i, 70, 0).getType(), "no wanted mobs left to show");
        }
    }

    @Test
    void rightClickingASignSellsAWantedHeldHeadAndCancelsTheClick() {
        PlayerMock player = server.addPlayer();
        MobHeads.Head headItem = wantedHead();
        player.getInventory().setItemInMainHand(head(null, null, headItem.texture(), 2));
        var block = server.addSimpleWorld("world").getBlockAt(11, 70, 0);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.SOUTH, EquipmentSlot.HAND);
        stall.onInteract(event);

        assertTrue(event.isCancelled(), "no sign-edit screen opens on our own sign");
        BigDecimal each = market.headPrice(headItem);
        assertEquals(List.of(each.multiply(BigDecimal.valueOf(2)).doubleValue()), paid);
        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType(), "the whole stack is sold, like HeadCollector did");
    }

    @Test
    void clickingABlockThatIsNotOneOfTheFourSlotsDoesNothing() {
        PlayerMock player = server.addPlayer();
        var block = server.addSimpleWorld("world").getBlockAt(999, 70, 0);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, ItemStack.empty(),
                block, BlockFace.SOUTH, EquipmentSlot.HAND);
        stall.onInteract(event);
        assertFalse(event.isCancelled());
        assertTrue(paid.isEmpty());
    }

    @Test
    void keepsHeadsItCannotBuy() {
        PlayerMock player = server.addPlayer();
        MobHeads.Head unwanted = unwantedHead();
        player.getInventory().setItemInMainHand(head(null, null, unwanted.texture(), 1));
        stall.sell(player);
        assertTrue(player.nextMessage().contains("No " + unwanted.name() + " today"));

        player.getInventory().setItemInMainHand(head(UUID.randomUUID(), "Steve", wantedHead().texture(), 1));
        stall.sell(player);
        assertTrue(player.nextMessage().contains("never a player's"), "a real player's head, even wearing a wanted skin");

        // Vanilla heads (zombie_head etc.) are redirected to the [Sell] signs that already buy them.
        player.getInventory().setItemInMainHand(ItemStack.of(Material.ZOMBIE_HEAD));
        stall.sell(player);
        assertTrue(player.nextMessage().contains("signs buy that head"));

        bankOpen = false;
        player.getInventory().setItemInMainHand(head(null, null, wantedHead().texture(), 1));
        stall.sell(player);
        assertTrue(player.nextMessage().contains("can't pay"));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount(), "kept when the economy is down");

        assertTrue(paid.isEmpty());
    }

    @Test
    void emptyHandHearsTodaysWantedMobs() {
        PlayerMock player = server.addPlayer();
        stall.sell(player);
        String message = player.nextMessage();
        assertTrue(message.contains(stall.wantedText()), message);
        assertTrue(stall.wantedText().contains(" and "), stall.wantedText());
    }

    @Test
    void onEnableAndOnChunkLoadRemoveAnyLeftoverHeadCollectorNpc() {
        WorldMock world = server.addSimpleWorld("world");
        NamespacedKey marker = new NamespacedKey(plugin, "head-collector");
        Zombie stray = world.spawn(world.getBlockAt(0, 70, 0).getLocation(), Zombie.class);
        stray.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "java");
        Zombie unrelated = world.spawn(world.getBlockAt(1, 70, 0).getLocation(), Zombie.class);

        stall.removeOldNpcs(world);

        assertTrue(stray.isDead() || !world.getEntities().contains(stray), "the marked leftover NPC is gone");
        assertNotNull(unrelated, "an unrelated entity is untouched");
    }
}
