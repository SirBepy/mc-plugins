package dev.sirbepy.mc.haru.shop;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.catalog.PetalExchange;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTest extends HaruTestBase {

    private Offer everyday(String id) {
        return ctx.shop().everyday(Tier.SAKURA_BOUND).stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    private Offer petal(String id) {
        return ctx.shop().petalExchange(Tier.SAKURA_BOUND).stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void buyingTakesTheEmeraldsAndHandsOverTheGoods() {
        give(owner, Material.EMERALD, 10);
        List<String> given = new ArrayList<>();

        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), everyday("ev_redstone"), given));

        assertEquals(8, count(owner, Material.EMERALD));
        assertEquals(5, count(owner, Material.REDSTONE));
        assertEquals(List.of("5 Redstone"), given);
        assertNull(ctx.bound().ownerOf(owner.getInventory().getItem(owner.getInventory().first(Material.REDSTONE))),
                "everyday wares are not bound");
    }

    @Test
    void stockComesBackEveryThreeMinecraftDays() {
        give(owner, Material.EMERALD, 64);
        Offer lanterns = everyday("ev_lanterns");
        for (int i = 0; i < lanterns.stock(); i++) {
            assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), lanterns, new ArrayList<>()));
        }
        assertEquals(Shop.Result.SOLD_OUT, ctx.shop().buy(owner, state(), lanterns, new ArrayList<>()));

        clock.fullTime += 24000;
        assertEquals(Shop.Result.SOLD_OUT, ctx.shop().buy(owner, state(), lanterns, new ArrayList<>()));

        clock.fullTime += 2 * 24000;
        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), lanterns, new ArrayList<>()));
    }

    @Test
    void ownerOnlyWaresAreBoundToTheBuyer() {
        state().setReputation(Tier.SAKURA_BOUND.threshold());
        state().setPetals(500);
        give(owner, Material.EMERALD, 64);
        Offer light = ctx.shop().secretStock(Tier.SAKURA_BOUND).stream()
                .filter(o -> o.id().equals("sec_light")).findFirst().orElseThrow();
        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), light, new ArrayList<>()));
        assertEquals(owner.getUniqueId(),
                ctx.bound().ownerOf(owner.getInventory().getItem(owner.getInventory().first(Material.LIGHT))));
        assertTrue(ctx.shop().secretStock(Tier.SAKURA_BOUND).stream().allMatch(Offer::bound));
        assertTrue(dev.sirbepy.mc.haru.shop.catalog.WanderingWares.pool().stream().allMatch(Offer::bound));
        assertTrue(dev.sirbepy.mc.haru.shop.catalog.WanderingWares.legendaries().stream().allMatch(Offer::bound));
    }

    @Test
    void waresAboveTheOwnersTierCannotBeBoughtOrSeen() {
        give(owner, Material.EMERALD, 64);
        Offer mending = everyday("ev_book_mending");
        assertFalse(ctx.shop().everyday(Tier.STRANGER).contains(mending));
        assertTrue(ctx.shop().everydayHasLocked(Tier.STRANGER));
        assertEquals(Shop.Result.LOCKED, ctx.shop().buy(owner, state(), mending, new ArrayList<>()));
        assertEquals(64, count(owner, Material.EMERALD));
    }

    @Test
    void reputationDiscountLowersTheEmeraldPrice() {
        state().setReputation(Tier.SAKURA_BOUND.threshold());
        give(owner, Material.EMERALD, 37);

        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), everyday("ev_book_efficiency"), new ArrayList<>()));

        assertEquals(3, count(owner, Material.EMERALD));
    }

    @Test
    void notEnoughEmeraldsTakesNothing() {
        give(owner, Material.EMERALD, 1);
        assertEquals(Shop.Result.NO_EMERALDS, ctx.shop().buy(owner, state(), everyday("ev_redstone"), new ArrayList<>()));
        assertEquals(1, count(owner, Material.EMERALD));
        assertEquals(0, count(owner, Material.REDSTONE));
    }

    @Test
    void auraIsAOneTimeUnlockPaidInPetals() {
        OwnerState state = state();
        state.setPetals(100);
        Offer aura = petal("pet_aura_sakura");

        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state, aura, new ArrayList<>()));
        assertTrue(state.has(PetalExchange.AURA_SAKURA));
        assertEquals(60, state.petals());
        assertEquals(Shop.Result.SOLD_OUT, ctx.shop().buy(owner, state, aura, new ArrayList<>()));
    }

    @Test
    void notEnoughPetalsTakesNothing() {
        state().setPetals(3);
        assertEquals(Shop.Result.NO_PETALS, ctx.shop().buy(owner, state(), petal("pet_bless_miner"), new ArrayList<>()));
        assertEquals(3, state().petals());
    }

    @Test
    void blessingAppliesTheEffectAndIsRemembered() {
        state().setPetals(20);
        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), petal("pet_bless_miner"), new ArrayList<>()));
        assertTrue(owner.hasPotionEffect(PotionEffectType.HASTE));
        assertTrue(state().blessings().containsKey("haste"));
    }

    @Test
    void collectiblePurchaseRecordsItForTheCollection() {
        state().setPetals(100);
        Offer collectible = ctx.shop().collectiblesToday().getFirst();
        assertEquals(Shop.Result.BOUGHT, ctx.shop().buy(owner, state(), collectible, new ArrayList<>()));
        assertTrue(state().has("head:" + collectible.id().substring("col_".length())));
        assertEquals(1, count(owner, Material.PLAYER_HEAD));
    }

    @Test
    void wanderingWaresAreStableWithinARotationAndChangeAfterIt() {
        List<Offer> first = ctx.shop().wandering(Tier.STRANGER);
        assertEquals(Shop.WANDERING_COUNT, first.size());
        assertEquals(first, ctx.shop().wandering(Tier.STRANGER));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            clock.fullTime += 3 * 24000;
            ctx.shop().wandering(Tier.STRANGER).forEach(o -> seen.add(o.id()));
        }
        assertTrue(seen.size() > Shop.WANDERING_COUNT, "rotations bring different wares");
    }

    @Test
    void secretStockOnlyAtMaxTrust() {
        assertTrue(ctx.shop().secretStock(Tier.KINDRED).isEmpty());
        assertFalse(ctx.shop().secretStock(Tier.SAKURA_BOUND).isEmpty());
    }

    @Test
    void everyOfferBuildsItsItemAndIcon() {
        for (Tier tier : List.of(Tier.SAKURA_BOUND)) {
            List<Offer> all = new ArrayList<>(ctx.shop().everyday(tier));
            all.addAll(ctx.shop().petalExchange(tier));
            all.addAll(ctx.shop().secretStock(tier));
            all.addAll(dev.sirbepy.mc.haru.shop.catalog.WanderingWares.pool());
            all.addAll(dev.sirbepy.mc.haru.shop.catalog.WanderingWares.legendaries());
            all.addAll(PetalExchange.collectibles());
            Set<String> ids = new HashSet<>();
            for (Offer offer : all) {
                assertTrue(ids.add(offer.id()), "duplicate offer id " + offer.id());
                assertFalse(offer.id().contains("."), "ids are YAML keys: " + offer.id());
                assertTrue(offer.icon().get().getType() != Material.AIR, offer.id());
            }
        }
    }
}
