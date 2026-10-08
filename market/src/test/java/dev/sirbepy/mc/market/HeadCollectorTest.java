package dev.sirbepy.mc.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.InputStreamReader;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadCollectorTest {

    @TempDir
    File dir;

    private ServerMock server;
    private MobHeads heads;
    private MarketConfig config;
    private Market market;
    private HeadCollector collector;
    private final List<Double> paid = new ArrayList<>();
    private boolean bankOpen = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        heads = MobHeads.load(Objects.requireNonNull(getClass().getResourceAsStream("/heads.yml")));
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        config = new MarketConfig(yaml);
        MarketLayout layout = MarketLayout.load(Objects.requireNonNull(getClass().getResourceAsStream("/stalls.yml")));
        market = new Market(layout, heads, () -> config, () -> "day-1", new Random(7), new File(dir, "state.yml"),
                Logger.getLogger("head-collector-test"));
        market.refresh();
        collector = new HeadCollector(MockBukkit.createMockPlugin(), market, () -> config, id -> false, (player, amount) -> {
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
    void identifiesAMobHeadByItsTextureButNeverAPlayersOwnHead() {
        MobHeads.Head breeze = heads.mob("breeze").heads().getFirst();
        assertEquals(breeze, heads.identify(head(null, null, breeze.texture(), 1)));
        // A PvP-dropped or cartel trophy head names its owner, even when they wear a mob head's skin.
        assertNull(heads.identify(head(UUID.randomUUID(), "Steve", breeze.texture(), 1)));
        assertNull(heads.identify(head(null, "Steve", breeze.texture(), 1)));
        assertNull(heads.identify(head(null, null, "0123456789abcdef", 1)), "a texture no mob head has");
        assertNull(heads.identify(ItemStack.of(Material.ZOMBIE_HEAD)));
        assertNull(heads.identify(ItemStack.of(Material.PLAYER_HEAD)));
    }

    @Test
    void shippedCatalogueIsPricedByDropChance() {
        assertEquals(0, heads.mob("breeze").heads().getFirst().price().compareTo(new BigDecimal("5")), "10% drop");
        assertEquals(0, heads.mob("enderman").heads().getFirst().price().compareTo(new BigDecimal("100")), "0.5% drop");
        assertEquals(17, heads.mob("sheep").heads().size(), "16 colours and jeb_");
        assertNull(heads.mob("player"));
    }

    @Test
    void sellsTheWholeHeldStackOfAWantedHeadAtTodaysPrice() {
        PlayerMock player = server.addPlayer();
        MobHeads.Head head = wantedHead();
        player.getInventory().setItemInMainHand(head(null, null, head.texture(), 3));

        collector.sell(player);

        BigDecimal each = market.headPrice(head);
        assertEquals(List.of(each.multiply(BigDecimal.valueOf(3)).doubleValue()), paid);
        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType());
        String message = player.nextMessage();
        assertNotNull(message);
        assertTrue(message.contains("Sold 3 " + head.name()) && message.contains(PriceList.money(each.multiply(BigDecimal.valueOf(3)))), message);
    }

    @Test
    void keepsHeadsItCannotBuy() {
        PlayerMock player = server.addPlayer();
        MobHeads.Head unwanted = unwantedHead();
        ItemStack notToday = head(null, null, unwanted.texture(), 1);
        player.getInventory().setItemInMainHand(notToday);
        collector.sell(player);
        assertTrue(player.nextMessage().contains("No " + unwanted.name() + " today"));

        ItemStack playerHead = head(UUID.randomUUID(), "Steve", wantedHead().texture(), 1);
        player.getInventory().setItemInMainHand(playerHead);
        collector.sell(player);
        assertTrue(player.nextMessage().contains("never a player's"));

        player.getInventory().setItemInMainHand(ItemStack.of(Material.ZOMBIE_HEAD));
        collector.sell(player);
        assertTrue(player.nextMessage().contains("signs buy that head"));

        bankOpen = false;
        player.getInventory().setItemInMainHand(head(null, null, wantedHead().texture(), 1));
        collector.sell(player);
        assertTrue(player.nextMessage().contains("can't pay"));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount(), "kept when the economy is down");

        assertTrue(paid.isEmpty());
    }

    @Test
    void emptyHandHearsTodaysWantedMobs() {
        PlayerMock player = server.addPlayer();
        collector.sell(player);
        String message = player.nextMessage();
        assertTrue(message.contains(collector.wantedText()), message);
        assertTrue(collector.wantedText().contains(" and "), collector.wantedText());
    }

    @Test
    void faceTheDirectionTheSpotFaces() {
        assertEquals(180, HeadCollector.yaw(BlockFace.NORTH));
        assertEquals(0, HeadCollector.yaw(BlockFace.SOUTH));
        assertEquals(90, HeadCollector.yaw(BlockFace.WEST));
        assertEquals(-90, HeadCollector.yaw(BlockFace.EAST));
    }
}
