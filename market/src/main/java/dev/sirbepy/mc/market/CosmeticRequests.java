package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.ItemNames;
import dev.sirbepy.mc.core.YamlFiles;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * {@code /cosmetic <idea>}: spends one {@link CosmeticToken} to stamp the item held in the main
 * hand with a {@code cosmetic_<id>} custom_model_data string and log a request Player Claude's
 * listener picks up (it tails the server log for the exact line {@link #request} writes). Admin
 * subcommands track each request through to {@code done} (live after the next natural restart,
 * Joe: never a forced one) or {@code refund}; either notifies the player now if online, or on
 * their next join if not - {@code cosmetics.yml} persists both the requests and which
 * notifications are still owed, so a restart never drops one.
 */
public final class CosmeticRequests implements Listener {

    enum Status { PENDING, DONE, REFUNDED }

    /** One stamp request. Immutable; {@link #withStatus} and {@link #notifiedNow} return a copy. */
    record Request(int id, UUID playerId, String playerName, String material, String displayName,
                   List<String> modelStrings, String idea, String created, Status status, boolean notified) {

        Request withStatus(Status next) {
            return new Request(id, playerId, playerName, material, displayName, modelStrings, idea, created, next, false);
        }

        Request notifiedNow() {
            return new Request(id, playerId, playerName, material, displayName, modelStrings, idea, created, status, true);
        }
    }

    // Matches a prior stamp from this plugin, so a second /cosmetic on the same item replaces it
    // instead of piling up strings forever.
    private static final Pattern COSMETIC_STRING = Pattern.compile("cosmetic_\\d+");

    private final Supplier<MarketConfig> config;
    private final CosmeticToken token;
    private final NamespacedKey cosmeticIdKey;
    private final File stateFile;
    private final Logger logger;

    private final Map<Integer, Request> requests = new LinkedHashMap<>();
    private int counter;

    public CosmeticRequests(Plugin plugin, Supplier<MarketConfig> config, CosmeticToken token, File stateFile, Logger logger) {
        this.config = config;
        this.token = token;
        this.cosmeticIdKey = new NamespacedKey(plugin, "cosmetic_id");
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    /** Spends one token to request a restyle of whatever the player holds in their main hand. */
    void request(Player player, String idea) {
        var messages = config.get().messages();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            messages.send(player, "cosmetic-hold");
            return;
        }
        if (token.is(held)) {
            messages.send(player, "cosmetic-is-token");
            return;
        }
        if (held.getAmount() > 1) {
            messages.send(player, "cosmetic-stack");
            return;
        }
        if (!token.hasToken(player.getInventory())) {
            messages.send(player, "cosmetic-no-token");
            return;
        }
        if (idea == null || idea.isBlank()) {
            messages.send(player, "cosmetic-no-idea");
            return;
        }
        token.takeOneFrom(player.getInventory());
        int id = ++counter;
        List<String> strings = stamp(held, id);
        player.getInventory().setItemInMainHand(held);
        String materialId = held.getType().getKey().getKey();
        String displayName = plainDisplayName(held);
        Request created = new Request(id, player.getUniqueId(), player.getName(), materialId, displayName,
                strings, idea, Instant.now().toString(), Status.PENDING, false);
        requests.put(id, created);
        save();
        // Exact shape Player Claude's log-tailing listener matches on; changing it breaks that watcher.
        logger.info("Cosmetic request #" + id + " from " + player.getName() + " (" + player.getUniqueId() + "): "
                + materialId + " - " + idea);
        messages.send(player, "cosmetic-requested", Placeholder.unparsed("id", String.valueOf(id)),
                Placeholder.unparsed("item", ItemNames.sentence(materialId)));
    }

    /** PDC {@code market:cosmetic_id} plus a {@code cosmetic_<id>} custom_model_data string, replacing
     * any earlier one of the plugin's own but keeping every other string, float, flag and colour. */
    private List<String> stamp(ItemStack item, int id) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(cosmeticIdKey, PersistentDataType.INTEGER, id);
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        List<String> strings = new ArrayList<>(cmd.getStrings());
        strings.removeIf(s -> COSMETIC_STRING.matcher(s).matches());
        strings.add("cosmetic_" + id);
        cmd.setStrings(strings);
        meta.setCustomModelDataComponent(cmd);
        item.setItemMeta(meta);
        return List.copyOf(strings);
    }

    private static String plainDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() ? PlainTextComponentSerializer.plainText().serialize(meta.displayName()) : null;
    }

    /** Every still-pending request, for an admin to triage. */
    void list(CommandSender sender) {
        var messages = config.get().messages();
        if (!sender.hasPermission("market.admin")) {
            messages.send(sender, "cosmetic-no-permission");
            return;
        }
        List<Request> pending = requests.values().stream().filter(r -> r.status() == Status.PENDING).toList();
        if (pending.isEmpty()) {
            messages.send(sender, "cosmetic-list-empty");
            return;
        }
        for (Request r : pending) {
            messages.send(sender, "cosmetic-list-line", Placeholder.unparsed("id", String.valueOf(r.id())),
                    Placeholder.unparsed("player", r.playerName()), Placeholder.unparsed("material", r.material()),
                    Placeholder.unparsed("idea", r.idea()));
        }
    }

    /** Marks a request live (Joe's looks ship at the next natural restart) and tells the player. */
    void done(CommandSender sender, int id) {
        finish(sender, id, Status.DONE, "cosmetic-done-notice", "cosmetic-done-admin");
    }

    /** Marks a request refunded and gives the player a fresh token. */
    void refund(CommandSender sender, int id) {
        finish(sender, id, Status.REFUNDED, "cosmetic-refund-notice", "cosmetic-refund-admin");
    }

    private void finish(CommandSender sender, int id, Status next, String noticeKey, String adminKey) {
        var messages = config.get().messages();
        if (!sender.hasPermission("market.admin")) {
            messages.send(sender, "cosmetic-no-permission");
            return;
        }
        Request request = requests.get(id);
        if (request == null) {
            messages.send(sender, "cosmetic-unknown-id", Placeholder.unparsed("id", String.valueOf(id)));
            return;
        }
        if (request.status() != Status.PENDING) {
            messages.send(sender, "cosmetic-wrong-status", Placeholder.unparsed("id", String.valueOf(id)),
                    Placeholder.unparsed("status", request.status().name().toLowerCase(Locale.ROOT)));
            return;
        }
        Request updated = request.withStatus(next);
        Player online = Bukkit.getPlayer(request.playerId());
        if (online != null) {
            if (next == Status.REFUNDED) {
                token.giveTo(online);
            }
            messages.send(online, noticeKey, Placeholder.unparsed("id", String.valueOf(id)));
            updated = updated.notifiedNow();
        }
        requests.put(id, updated);
        save();
        messages.send(sender, adminKey, Placeholder.unparsed("id", String.valueOf(id)));
    }

    /** Delivers any done/refund notification this player's requests are still owed. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var messages = config.get().messages();
        boolean changed = false;
        for (Request r : List.copyOf(requests.values())) {
            if (r.notified() || !r.playerId().equals(player.getUniqueId())) {
                continue;
            }
            if (r.status() == Status.DONE) {
                messages.send(player, "cosmetic-done-notice", Placeholder.unparsed("id", String.valueOf(r.id())));
                requests.put(r.id(), r.notifiedNow());
                changed = true;
            } else if (r.status() == Status.REFUNDED) {
                token.giveTo(player);
                messages.send(player, "cosmetic-refund-notice", Placeholder.unparsed("id", String.valueOf(r.id())));
                requests.put(r.id(), r.notifiedNow());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /** For tests: the request as currently stored. */
    Request get(int id) {
        return requests.get(id);
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + "; starting with no cosmetic requests", e);
            return;
        }
        counter = yaml.getInt("counter", 0);
        ConfigurationSection section = yaml.getConfigurationSection("requests");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection r = section.getConfigurationSection(key);
                UUID playerId = UUID.fromString(r.getString("player-id"));
                Status status = Status.valueOf(r.getString("status", "PENDING").toUpperCase(Locale.ROOT));
                requests.put(id, new Request(id, playerId, r.getString("player-name"), r.getString("material"),
                        r.getString("display-name", null), r.getStringList("model-strings"), r.getString("idea", ""),
                        r.getString("created"), status, r.getBoolean("notified", false)));
            } catch (RuntimeException e) {
                logger.warning("Bad cosmetic request " + key + " in " + stateFile + "; skipped");
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("counter", counter);
        for (Request r : requests.values()) {
            String path = "requests." + r.id();
            yaml.set(path + ".player-id", r.playerId().toString());
            yaml.set(path + ".player-name", r.playerName());
            yaml.set(path + ".material", r.material());
            yaml.set(path + ".display-name", r.displayName());
            yaml.set(path + ".model-strings", r.modelStrings());
            yaml.set(path + ".idea", r.idea());
            yaml.set(path + ".created", r.created());
            yaml.set(path + ".status", r.status().name().toLowerCase(Locale.ROOT));
            yaml.set(path + ".notified", r.notified());
        }
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would lose today's cosmetic requests", e);
        }
    }
}
