package dev.sirbepy.mc.haru.shop;

import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.item.HaruItems;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.catalog.EverydayWares;
import dev.sirbepy.mc.haru.shop.catalog.PetalExchange;
import dev.sirbepy.mc.haru.shop.catalog.SecretStock;
import dev.sirbepy.mc.haru.shop.catalog.WanderingWares;
import dev.sirbepy.mc.haru.time.HaruClock;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Which wares are on which shelf right now, what they cost the owner, and the purchase itself. */
public final class Shop {

    public static final int WANDERING_COUNT = 6;
    public static final int COLLECTIBLES_PER_DAY = 3;

    public enum Result { BOUGHT, LOCKED, SOLD_OUT, NO_EMERALDS, NO_PETALS }

    private final HaruClock clock;
    private final Supplier<HaruConfig> config;
    private final Rewards rewards;
    private final List<Offer> everyday;
    private final List<Offer> wandering;
    private final List<Offer> legendaries;
    private final List<Offer> petals;
    private final List<Offer> collectibles;
    private final List<Offer> secret;

    public Shop(HaruClock clock, Supplier<HaruConfig> config, Rewards rewards, HaruItems items) {
        this.clock = clock;
        this.config = config;
        this.rewards = rewards;
        this.everyday = EverydayWares.offers();
        this.wandering = WanderingWares.pool();
        this.legendaries = WanderingWares.legendaries();
        this.petals = PetalExchange.offers(items);
        this.collectibles = PetalExchange.collectibles();
        this.secret = SecretStock.offers(items);
    }

    public List<Offer> everyday(Tier tier) {
        return unlocked(everyday, tier);
    }

    /** True while some Everyday Ware is still waiting on a higher tier. */
    public boolean everydayHasLocked(Tier tier) {
        return everyday.stream().anyMatch(o -> !tier.atLeast(o.minTier()));
    }

    public List<Offer> wandering(Tier tier) {
        long rotation = rotation();
        return Rotation.pick(unlocked(wandering, tier), "wandering-" + tier.ordinal(), rotation, WANDERING_COUNT);
    }

    public Optional<Offer> legendary() {
        return Rotation.legendary(legendaries, rotation(), config.get().legendaryChance());
    }

    public long rotation() {
        return clock.rotationIndex(config.get().rotationDays());
    }

    public long daysUntilRotation() {
        return clock.daysUntilRotation(config.get().rotationDays());
    }

    public List<Offer> petalExchange(Tier tier) {
        return unlocked(petals, tier);
    }

    public boolean petalsHaveLocked(Tier tier) {
        return petals.stream().anyMatch(o -> !tier.atLeast(o.minTier()));
    }

    public List<Offer> collectiblesToday() {
        return Rotation.pick(collectibles, "collectibles", clock.today().toEpochDay(), COLLECTIBLES_PER_DAY);
    }

    public List<Offer> secretStock(Tier tier) {
        return tier.isMax() ? secret : List.of();
    }

    public int emeraldPrice(OwnerState state, Offer offer) {
        return state.tier().discounted(offer.emeralds());
    }

    public String period(Restock restock) {
        return switch (restock) {
            case MINECRAFT_DAY -> "mcday:" + clock.minecraftDay();
            case ROTATION -> "rot:" + rotation();
            case REAL_DAY -> "day:" + clock.today();
            case REAL_WEEK -> "week:" + clock.weekKey();
            case ONCE, UNLIMITED -> "once";
        };
    }

    /** Stock left this period, or {@link Integer#MAX_VALUE} for unlimited offers. */
    public int remaining(OwnerState state, Offer offer) {
        if (!offer.limited()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, offer.stock() - state.bought(offer.id(), period(offer.restock())));
    }

    /** Checks tier, stock and both currencies before taking anything, then pays and hands over. */
    public Result buy(Player player, OwnerState state, Offer offer, List<String> given) {
        if (!state.tier().atLeast(offer.minTier())) {
            return Result.LOCKED;
        }
        if (remaining(state, offer) <= 0) {
            return Result.SOLD_OUT;
        }
        int emeralds = emeraldPrice(state, offer);
        if (Payment.emeraldsHeld(player.getInventory()) < emeralds) {
            return Result.NO_EMERALDS;
        }
        if (state.petals() < offer.petals()) {
            return Result.NO_PETALS;
        }
        Payment.take(player, emeralds);
        state.spendPetals(offer.petals());
        if (offer.limited()) {
            state.recordPurchase(offer.id(), period(offer.restock()));
        }
        given.addAll(rewards.grant(player, state, offer.reward(), offer.bound()));
        return Result.BOUGHT;
    }

    private static List<Offer> unlocked(List<Offer> offers, Tier tier) {
        List<Offer> result = new ArrayList<>();
        for (Offer offer : offers) {
            if (tier.atLeast(offer.minTier())) {
                result.add(offer);
            }
        }
        return result;
    }
}
