package dev.sirbepy.mc.market;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Head collector at the Mob drops stall: buys today's wanted More Mob Heads heads from the player
 * who right-clicks it holding one. Two bodies stand on its spot, as with Haru: a Mannequin wearing a
 * zombie head that Java players see, and an AI-less villager Bedrock players see (Geyser shows a
 * villager reliably), each hidden from the other edition. Vanilla spawn protection swallows block
 * clicks but not entity clicks (Paper 26.3's handleInteract has no isUnderSpawnProtection check,
 * javap 2026-10-08), so an NPC works at spawn where a plugin sign can't.
 */
public final class HeadCollector implements Listener {

    /** Pays a player; false when no economy took the money. */
    @FunctionalInterface
    public interface Payout {
        boolean pay(Player player, double amount);
    }

    static final String JAVA = "java";
    static final String BEDROCK = "bedrock";
    // The heads the stalls' [Sell] signs buy, so the collector can point there instead.
    private static final Set<Material> SIGN_HEADS = Set.of(Material.ZOMBIE_HEAD, Material.SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD, Material.WITHER_SKELETON_SKULL, Material.DRAGON_HEAD);

    private final Plugin plugin;
    private final Market market;
    private final Supplier<MarketConfig> config;
    private final Predicate<UUID> bedrock;
    private final Payout payout;
    private final NamespacedKey marker;

    public HeadCollector(Plugin plugin, Market market, Supplier<MarketConfig> config, Predicate<UUID> bedrock, Payout payout) {
        this.plugin = plugin;
        this.market = market;
        this.config = config;
        this.bedrock = bedrock;
        this.payout = payout;
        this.marker = new NamespacedKey(plugin, "head-collector");
    }

    /** Which body this is ({@link #JAVA} or {@link #BEDROCK}), or null for any other entity. */
    String body(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
    }

    /**
     * The guard tick: while the spot's chunk and its entities are loaded, keeps exactly one of each
     * body on the spot (spawning, snapping back or removing duplicates), then refreshes who sees which.
     */
    public void ensure(World world) {
        Stall.Slot spot = market.layout().headCollector();
        if (spot == null || !world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)
                || !world.getChunkAt(spot.x() >> 4, spot.z() >> 4).isEntitiesLoaded()) {
            return;
        }
        Location home = home(world, spot);
        Mannequin mannequin = null;
        Villager villager = null;
        for (Entity entity : world.getChunkAt(spot.x() >> 4, spot.z() >> 4).getEntities()) {
            String body = body(entity);
            if (JAVA.equals(body) && mannequin == null && entity instanceof Mannequin m) {
                mannequin = m;
            } else if (BEDROCK.equals(body) && villager == null && entity instanceof Villager v) {
                villager = v;
            } else if (body != null) {
                entity.remove();
            }
        }
        if (mannequin == null) {
            mannequin = spawnMannequin(home);
        } else {
            snapBack(mannequin, home);
        }
        if (mannequin.getEquipment().getHelmet().getType() != Material.ZOMBIE_HEAD) {
            mannequin.getEquipment().setHelmet(ItemStack.of(Material.ZOMBIE_HEAD));
        }
        if (villager == null) {
            villager = spawnVillager(home);
        } else {
            snapBack(villager, home);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyVisibility(player, mannequin, villager);
        }
    }

    private void applyVisibility(Player player, Mannequin mannequin, Villager villager) {
        boolean java = !bedrock.test(player.getUniqueId());
        show(player, mannequin, java);
        show(player, villager, !java);
    }

    private void show(Player player, Entity entity, boolean visible) {
        if (visible) {
            player.showEntity(plugin, entity);
        } else {
            player.hideEntity(plugin, entity);
        }
    }

    private static Location home(World world, Stall.Slot spot) {
        Location home = new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5);
        home.setYaw(yaw(spot.facing()));
        return home;
    }

    static float yaw(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> -90;
            default -> 0;
        };
    }

    private static void snapBack(Entity entity, Location home) {
        if (entity.getLocation().distanceSquared(home) > 0.25) {
            entity.teleport(home);
        }
    }

    private Mannequin spawnMannequin(Location at) {
        Mannequin spawned = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setPersistent(true);
            mannequin.customName(config.get().messages().render("collector-name"));
            mannequin.setCustomNameVisible(true);
            mannequin.getPersistentDataContainer().set(marker, PersistentDataType.STRING, JAVA);
        });
        spawned.getEquipment().setHelmet(ItemStack.of(Material.ZOMBIE_HEAD));
        spawned.setBodyYaw(at.getYaw());
        return spawned;
    }

    private Villager spawnVillager(Location at) {
        return at.getWorld().spawn(at, Villager.class, villager -> {
            villager.setProfession(Villager.Profession.LEATHERWORKER);
            villager.setVillagerType(Villager.Type.TAIGA);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.customName(config.get().messages().render("collector-name"));
            villager.setCustomNameVisible(true);
            villager.getPersistentDataContainer().set(marker, PersistentDataType.STRING, BEDROCK);
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (body(event.getRightClicked()) == null) {
            return;
        }
        // No villager trade screen; and the off-hand click that follows every main-hand one is ignored.
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            sell(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // The joining player's view of the bodies is set on the next guard tick at the latest; doing it
        // a tick after join covers the common case at once.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = plugin.getServer().getWorld(config.get().world());
            if (world != null) {
                ensure(world);
            }
        });
    }

    /** Buys the whole stack in the player's main hand if it is a head wanted today, and says why not otherwise. */
    void sell(Player player) {
        var messages = config.get().messages();
        var wanted = Placeholder.unparsed("mobs", wantedText());
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (SIGN_HEADS.contains(stack.getType())) {
            messages.send(player, "collector-sign-head", wanted);
            return;
        }
        MobHeads.Head head = market.heads().identify(stack);
        if (head == null) {
            messages.send(player, stack.getType() == Material.PLAYER_HEAD ? "collector-player-head" : "collector-hold", wanted);
            return;
        }
        var name = Placeholder.unparsed("head", head.name());
        BigDecimal each = market.headPrice(head);
        if (each == null) {
            messages.send(player, "collector-not-today", name, wanted);
            return;
        }
        int amount = stack.getAmount();
        BigDecimal total = each.multiply(BigDecimal.valueOf(amount));
        if (!payout.pay(player, total.doubleValue())) {
            messages.send(player, "collector-closed");
            return;
        }
        player.getInventory().setItemInMainHand(null);
        messages.send(player, "collector-sold", name, Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("total", PriceList.money(total)));
    }

    /** Today's wanted mobs as words, e.g. {@code breeze, allay and cow}. */
    String wantedText() {
        var names = market.wantedMobs().stream().map(m -> m.name().toLowerCase(Locale.ROOT)).toList();
        if (names.isEmpty()) {
            return "nothing";
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }
}
