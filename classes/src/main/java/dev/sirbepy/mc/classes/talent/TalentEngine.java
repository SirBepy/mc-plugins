package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Abilities;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.kits.Kits;
import dev.sirbepy.mc.core.bedrock.BedrockForms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * The talent trees (docs/classes.md, Talent trees): builds every service and the seven kits, wires
 * them to the plugin, and runs the two timers (the spell bar or the mod's HUD every 5 ticks, the resource drain
 * every second). Resources, kit cooldowns and summons reset when a player dies, logs out or
 * changes class.
 */
public final class TalentEngine implements Listener {

    private final Plugin plugin;
    private final Classes classes;
    private final Talents talents;
    private final Resources resources;
    private final Cooldowns cooldowns;
    private final CombatTracker combat;
    private final Summons summons;
    private final Fx fx = new Fx();
    private final AbilitySlots slots;
    private final ComboListener combos;
    private final ResourceBar bar;
    private final TreeMenu treeMenu;
    private final Map<RpgClass, ClassKit> kits = new EnumMap<>(RpgClass.class);
    /** The class each online player had at the last bar update, to notice class changes. */
    private final Map<UUID, RpgClass> lastSeen = new HashMap<>();

    public TalentEngine(Plugin plugin, Classes classes, Abilities abilities, DoubleSupplier random,
                        boolean treeBackground, boolean abilityIcons, BedrockForms bedrock) {
        this(plugin, classes, abilities, random, treeBackground, abilityIcons, bedrock, Kits::create);
    }

    /** {@code kitFactory} builds each class's kit; tests pass fakes. */
    public TalentEngine(Plugin plugin, Classes classes, Abilities abilities, DoubleSupplier random,
                        boolean treeBackground, boolean abilityIcons, BedrockForms bedrock,
                        BiFunction<RpgClass, KitContext, ClassKit> kitFactory) {
        this.plugin = plugin;
        this.classes = classes;
        this.talents = new Talents(classes);
        this.resources = new Resources(classes::now);
        this.cooldowns = new Cooldowns(classes::now);
        this.combat = new CombatTracker(resources, classes::now);
        this.summons = new Summons(plugin);
        Map<RpgClass, ClassKit> view = Collections.unmodifiableMap(kits);
        this.slots = new AbilitySlots(plugin, classes, abilities, talents, resources, cooldowns, view);
        this.combos = new ComboListener(plugin, classes, slots, classes::now, p -> p.hasActiveItem() || p.isBlocking());
        this.bar = new ResourceBar(classes, resources, view, slots, combos, fx, new AbilityIcons(abilityIcons, bedrock));
        this.treeMenu = new TreeMenu(classes, talents, view, slots, combos, bar, resources, new TreeLook(treeBackground, bedrock));
        // A click redraws at once rather than at the next 5-tick update, so the combo so far keeps up with the clicks.
        combos.setOnProgress(this::update);
        abilities.setCooldownAdjuster((player, c, base) -> cooldowns.adjust(player, Slot.ONE, base));
        for (RpgClass c : RpgClass.values()) {
            KitContext ctx = new KitContext(c, plugin, classes, talents, resources, cooldowns, summons, combat, fx, random);
            ClassKit kit = kitFactory.apply(c, ctx);
            talents.register(c, kit.talents());
            kits.put(c, kit);
        }
    }

    /** Registers the listeners, enables every kit and starts the timers. */
    public void start() {
        var events = plugin.getServer().getPluginManager();
        events.registerEvents(combat, plugin);
        events.registerEvents(summons, plugin);
        events.registerEvents(combos, plugin);
        events.registerEvents(this, plugin);
        TreeChannel channel = TreeChannel.register(plugin, classes, talents, kits, slots, combos, resources, treeMenu);
        treeMenu.setChannel(channel);
        bar.setDrawnByMod(channel::supportsHud);
        kits.values().forEach(ClassKit::enable);
        var scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskTimer(plugin, () -> plugin.getServer().getOnlinePlayers().forEach(this::update), 5L, 5L);
        scheduler.runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (classes.active(p.getUniqueId()) != null) resources.tick(p);
            }
        }, 20L, 20L);
    }

    public void stop() {
        kits.values().forEach(ClassKit::disable);
        summons.dismissEveryone();
        plugin.getServer().getOnlinePlayers().forEach(bar::hide);
    }

    void update(Player player) {
        UUID id = player.getUniqueId();
        RpgClass now = classes.active(id);
        if (lastSeen.containsKey(id) && lastSeen.get(id) != now) reset(id);
        lastSeen.put(id, now);
        bar.update(player);
        TreeChannel channel = treeMenu.channel();
        if (channel != null) channel.sendHud(player);
    }

    private void reset(UUID player) {
        resources.reset(player);
        cooldowns.clear(player);
        summons.dismissAll(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (talents.repair(player.getUniqueId())) {
            player.sendMessage(Component.text("A class of yours lost levels, so its talent points were refunded. /class tree",
                    NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        resources.reset(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        resources.reset(id);
        combat.forget(id);
        lastSeen.remove(id);
        bar.hide(event.getPlayer());
        if (treeMenu.channel() != null) treeMenu.channel().forget(id);
    }

    public Talents talents() {
        return talents;
    }

    public Resources resources() {
        return resources;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public AbilitySlots slots() {
        return slots;
    }

    public TreeMenu treeMenu() {
        return treeMenu;
    }

    /** Replaces the tree mod channel with one whose player check and sender are fakes (tests). */
    TreeChannel fakeTreeChannel(Predicate<Player> supports, BiConsumer<Player, byte[]> sender) {
        TreeChannel channel = new TreeChannel(classes, talents, kits, slots, combos, resources, treeMenu, supports, sender);
        treeMenu.setChannel(channel);
        bar.setDrawnByMod(channel::supportsHud);
        return channel;
    }

    public Summons summons() {
        return summons;
    }

    public CombatTracker combat() {
        return combat;
    }

    public ClassKit kit(RpgClass c) {
        return kits.get(c);
    }

    public ResourceBar bar() {
        return bar;
    }

    public ComboListener combos() {
        return combos;
    }
}
