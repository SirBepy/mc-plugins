package dev.sirbepy.mc.merfolk.trade;

import dev.sirbepy.mc.merfolk.Kingdom;
import dev.sirbepy.mc.merfolk.Merfolk;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Merfolk jobs: a merfolk that takes the fisherman, mason or cartographer job at an underwater job
 * site becomes a Fisher, Mason or Treasurer with the wish's trades; any other job (or those jobs on
 * land) keeps its vanilla trades plus one {@link SeaTrade}. Ordinary villagers are never touched.
 */
public final class MerfolkJobs implements Listener {

    public static final NamespacedKey SPECIALIST = new NamespacedKey("merfolk", "specialist");
    /** The job whose sea trade was added, so a merfolk that changes job gets the new job's one. */
    static final NamespacedKey SEA_TRADE = new NamespacedKey("merfolk", "sea_trade");

    private final Kingdom kingdom;
    private final RareLedger ledger;
    /** Runs a task on the next tick (the scheduler in the plugin, direct in tests). */
    private final Consumer<Runnable> nextTick;
    private final Set<UUID> pending = new HashSet<>();

    public MerfolkJobs(Kingdom kingdom, RareLedger ledger, Consumer<Runnable> nextTick) {
        this.kingdom = kingdom;
        this.ledger = ledger;
        this.nextTick = nextTick;
    }

    public static Specialist specialist(Entity entity) {
        String key = entity.getPersistentDataContainer().get(SPECIALIST, PersistentDataType.STRING);
        return key == null ? null : Specialist.byKey(key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        if (!Merfolk.is(villager)) return;
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        pdc.remove(SEA_TRADE);
        Specialist specialist = event.getReason() == VillagerCareerChangeEvent.ChangeReason.EMPLOYED
                ? Specialist.forProfession(event.getProfession()) : null;
        if (specialist != null && kingdom.specialistsAllowed() && jobSiteUnderwater(villager)) {
            pdc.set(SPECIALIST, PersistentDataType.STRING, specialist.key());
            villager.customName(Component.text(specialist.title()));
        } else {
            clearSpecialist(villager);
        }
        later(villager);
    }

    /**
     * Vanilla hands a levelling villager two random trades of its job; a specialist's come from
     * {@link Specialist} instead, so the vanilla ones are refused and the next tick tops the list up.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || specialist(villager) == null) return;
        event.setCancelled(true);
        later(villager);
    }

    /** Before the trade screen opens: bring the merfolk's trades up to date and grey out capped rares. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager) || !Merfolk.is(villager)) return;
        refresh(villager);
        List<MerchantRecipe> recipes = villager.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);
            Material item = recipe.getResult().getType();
            if (RareLedger.isRare(item) && ledger.waitFor(item) > 0 && recipe.getUses() < recipe.getMaxUses()) {
                recipe.setUses(recipe.getMaxUses());
                villager.setRecipe(i, recipe);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!Merfolk.is(event.getVillager())) return;
        Material item = event.getTrade().getResult().getType();
        long wait = ledger.waitFor(item);
        if (wait <= 0) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("The sea's treasury holds no more of that for "
                + roughly(wait) + ".", NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTraded(PlayerTradeEvent event) {
        if (Merfolk.is(event.getVillager())) ledger.record(event.getTrade().getResult().getType());
    }

    /**
     * A villager converted while it already works an underwater barrel, stonecutter or cartography
     * table becomes that specialist too, as long as nobody has traded with it yet (its vanilla trades
     * are replaced, so a traded villager keeps its job as it is).
     */
    public void considerSpecialist(Villager villager) {
        Specialist specialist = Specialist.forProfession(villager.getProfession());
        if (specialist == null || specialist(villager) != null || !kingdom.specialistsAllowed()
                || villager.getVillagerExperience() > 0 || villager.getVillagerLevel() > 1
                || !jobSiteUnderwater(villager)) {
            return;
        }
        villager.getPersistentDataContainer().set(SPECIALIST, PersistentDataType.STRING, specialist.key());
        villager.customName(Component.text(specialist.title()));
    }

    /**
     * Brings one merfolk's job state in line: reverts a specialist once the kingdom clause has
     * failed, fills a specialist's trades up to its level, or adds an ordinary job's sea trade.
     */
    public void refresh(Villager villager) {
        if (!villager.isValid() || !Merfolk.is(villager)) return;
        Specialist specialist = specialist(villager);
        if (specialist != null && !kingdom.specialistsAllowed()) {
            revert(villager);
        } else if (specialist != null) {
            syncSpecialist(villager, specialist);
        } else {
            ensureSeaTrade(villager);
        }
    }

    /**
     * The specialist becomes an ordinary novice merfolk of the vanilla job it borrowed: fresh vanilla
     * trades plus that job's sea trade. Its job site stays its own.
     */
    public void revert(Villager villager) {
        Specialist specialist = specialist(villager);
        if (specialist == null) return;
        clearSpecialist(villager);
        villager.getPersistentDataContainer().remove(SEA_TRADE);
        Villager.Profession profession = villager.getProfession();
        villager.setVillagerExperience(0);
        villager.setVillagerLevel(1);
        // Changing the profession is what makes vanilla throw the old offers away and roll new ones.
        villager.setProfession(Villager.Profession.NONE);
        villager.setProfession(profession);
        ensureSeaTrade(villager);
    }

    private void later(Villager villager) {
        if (!pending.add(villager.getUniqueId())) return;
        nextTick.accept(() -> {
            pending.remove(villager.getUniqueId());
            refresh(villager);
        });
    }

    private void syncSpecialist(Villager villager, Specialist specialist) {
        List<MerchantRecipe> desired = specialist.tradesUpTo(villager.getVillagerLevel(), villager.getUniqueId());
        List<MerchantRecipe> current = villager.getRecipes();
        boolean same = current.size() == desired.size();
        List<MerchantRecipe> merged = new ArrayList<>(desired.size());
        for (int i = 0; i < desired.size(); i++) {
            MerchantRecipe want = desired.get(i);
            if (i < current.size() && sameOffer(current.get(i), want)) {
                merged.add(current.get(i));
            } else {
                merged.add(want);
                same = false;
            }
        }
        if (!same) villager.setRecipes(merged);
        if (villager.customName() == null) villager.customName(Component.text(specialist.title()));
    }

    private void ensureSeaTrade(Villager villager) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        String job = villager.getProfession().getKey().getKey();
        if (job.equals(pdc.get(SEA_TRADE, PersistentDataType.STRING))) return;
        MerchantRecipe extra = SeaTrade.forProfession(villager.getProfession(), villager.getUniqueId());
        if (extra != null) {
            List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
            recipes.add(extra);
            villager.setRecipes(recipes);
        }
        pdc.set(SEA_TRADE, PersistentDataType.STRING, job);
    }

    private static void clearSpecialist(Villager villager) {
        Specialist old = specialist(villager);
        villager.getPersistentDataContainer().remove(SPECIALIST);
        if (old != null && Objects.equals(villager.customName(), Component.text(old.title()))) {
            villager.customName(null);
        }
    }

    private static boolean sameOffer(MerchantRecipe a, MerchantRecipe b) {
        return a.getResult().isSimilar(b.getResult()) && a.getResult().getAmount() == b.getResult().getAmount()
                && a.getIngredients().size() == b.getIngredients().size()
                && a.getIngredients().getFirst().getType() == b.getIngredients().getFirst().getType();
    }

    /**
     * "Underwater" = water directly above the job site block, or the block itself waterlogged.
     * Barrels, stonecutters and cartography tables aren't waterloggable, so in practice it's the
     * water on top. Without a remembered site (another plugin set the job), the villager's own spot counts.
     */
    static boolean jobSiteUnderwater(Villager villager) {
        Location site = villager.getMemory(MemoryKey.JOB_SITE);
        if (site == null || site.getWorld() == null) return villager.isInWater();
        Block block = site.getBlock();
        return isWater(block) || isWater(block.getRelative(BlockFace.UP));
    }

    private static boolean isWater(Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    static String roughly(long millis) {
        Duration left = Duration.ofMillis(millis);
        if (left.toHours() >= 1) return left.toHours() + "h " + left.toMinutesPart() + "m";
        return Math.max(1, left.toMinutes()) + "m";
    }
}
