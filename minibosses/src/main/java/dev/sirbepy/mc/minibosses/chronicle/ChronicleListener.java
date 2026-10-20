package dev.sirbepy.mc.minibosses.chronicle;

import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything players touch in the Chronicles: talking to the Chronicler (either body; nothing
 * hurts, burns, converts, leashes or trades with her), the fragment each fighter gets on their
 * first kill of a Guardian, reading a fragment, the discovery biome watcher, and the finished book.
 * Lines are spoken one at a time, {@link #LINE_GAP_TICKS} apart, and a player already mid-speech
 * can't start a second one.
 */
public final class ChronicleListener implements Listener {

    static final long LINE_GAP_TICKS = 50L;

    private final Plugin plugin;
    private final Chronicle chronicle;
    private final ChroniclerNpc npc;
    private final NamespacedKey fragmentKey;
    private final Set<UUID> speaking = new HashSet<>();

    public ChronicleListener(Plugin plugin, Chronicle chronicle, ChroniclerNpc npc) {
        this.plugin = plugin;
        this.chronicle = chronicle;
        this.npc = npc;
        this.fragmentKey = new NamespacedKey(plugin, "chronicle_fragment");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!npc.isChronicler(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            talk(event.getPlayer());
        }
    }

    /** Armor-stand style clicks arrive as this subclass first; only the cancel matters here. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (npc.isChronicler(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    public void talk(Player player) {
        if (speaking.contains(player.getUniqueId())) {
            return;
        }
        Chronicle.Talk talk = chronicle.talk(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 0.8f);
        speak(player, talk.lines(), false, () -> {
            if (talk.finale()) {
                finish(player);
            }
        });
    }

    private void finish(Player player) {
        player.getInventory().addItem(finishedBook(player)).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        String announce = chronicle.text().finale().announce();
        if (!announce.isBlank()) {
            Bukkit.broadcast(Component.text(announce.replace("{player}", player.getName()), npc.color()));
        }
    }

    public ItemStack finishedBook(Player player) {
        ChronicleText.Finale finale = chronicle.text().finale();
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(finale.bookTitle());
        meta.setAuthor(finale.bookAuthor());
        meta.addPages(components(chronicle.bookPages(player.getUniqueId())).toArray(Component[]::new));
        book.setItemMeta(meta);
        return book;
    }

    /** {@code /chronicle}: the pages unlocked so far, then where to go next. */
    public void openJournal(Player player) {
        List<String> pages = chronicle.journal(player.getUniqueId());
        if (pages.isEmpty()) {
            player.sendMessage(Component.text("Your chronicle is empty. Someone at spawn keeps the old stories.",
                    NamedTextColor.GRAY));
            return;
        }
        player.openBook(Book.book(Component.text("Chronicle"), Component.text(npc.name()), components(pages)));
    }

    private static List<Component> components(List<String> pages) {
        return pages.stream().<Component>map(Component::text).toList();
    }

    @EventHandler
    public void onGuardianDefeated(GuardianDefeatedEvent event) {
        chronicle.chapterFor(event.guardianId()).ifPresent(chapter -> {
            for (UUID id : event.fighterIds()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || chronicle.stage(id) <= 0 || chronicle.progress().hasFragment(id, chapter.guardian())) {
                    continue;
                }
                player.getInventory().addItem(fragment(chapter)).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                chronicle.progress().markFragment(id, chapter.guardian());
                player.sendMessage(Component.text("A torn page drifts down from where it fell: "
                        + chapter.fragment().name() + ".", npc.color()).decoration(TextDecoration.ITALIC, true));
            }
        });
    }

    ItemStack fragment(ChronicleText.Chapter chapter) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(chapter.fragment().name(), npc.color()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to read. The Chronicler would want to see it.",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(fragmentKey, PersistentDataType.STRING, chapter.guardian());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onFragmentRead(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.getItem() == null || !event.getItem().hasItemMeta()) {
            return;
        }
        String guardian = event.getItem().getItemMeta().getPersistentDataContainer()
                .get(fragmentKey, PersistentDataType.STRING);
        if (guardian == null) {
            return;
        }
        event.setCancelled(true);
        chronicle.chapterFor(guardian).ifPresent(chapter -> event.getPlayer().openBook(Book.book(
                Component.text(chapter.fragment().name()), Component.text("?"),
                components(chapter.fragment().pages()))));
    }

    /** The biome watcher, run on a timer: whoever is seeking a discovery and stands in its biome finds it. */
    public void checkDiscoveries() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!chronicle.seeking(player.getUniqueId()) || speaking.contains(player.getUniqueId())) {
                continue;
            }
            String biome = player.getWorld().getBiome(player.getLocation()).getKey().toString();
            List<String> echo = chronicle.discover(player.getUniqueId(), biome);
            if (!echo.isEmpty()) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
                speak(player, echo, true, () -> player.sendMessage(Component.text(
                        "Your chronicle has a new page. /chronicle", NamedTextColor.GRAY)));
            }
        }
    }

    private void speak(Player player, List<String> lines, boolean echo, Runnable after) {
        speaking.add(player.getUniqueId());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(echo
                            ? Component.text(line, NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, true)
                            : Component.text(npc.name() + ": ", npc.color()).append(Component.text(line, NamedTextColor.WHITE)));
                }
            }, i * LINE_GAP_TICKS);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            speaking.remove(player.getUniqueId());
            if (player.isOnline()) {
                after.run();
            }
        }, Math.max(0, lines.size() - 1) * LINE_GAP_TICKS + 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransform(EntityTransformEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (npc.isChronicler(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        npc.removeStrays(event.getEntities().toArray(Entity[]::new));
        if (event.getEntities().stream().anyMatch(npc::isChronicler)) {
            npc.applyVisibilityToAll();
        }
    }
}
