package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tweaks.changelog.ChangelogCommand;
import dev.sirbepy.mc.tweaks.listener.DailyMotdListener;
import dev.sirbepy.mc.tweaks.listener.EarthbreakerListener;
import dev.sirbepy.mc.tweaks.listener.LoyaltyTridentListener;
import dev.sirbepy.mc.tweaks.listener.MinecartSpeedListener;
import dev.sirbepy.mc.tweaks.listener.PendingTridents;
import dev.sirbepy.mc.tweaks.listener.PhantomBuffListener;
import dev.sirbepy.mc.tweaks.listener.PollosGunListener;
import dev.sirbepy.mc.tweaks.listener.ScytheMasterListener;
import dev.sirbepy.mc.tweaks.listener.SpawnGuardListener;
import dev.sirbepy.mc.tweaks.listener.SpawnSafeListener;
import dev.sirbepy.mc.tweaks.listener.TownyAdminBypass;
import dev.sirbepy.mc.tweaks.listener.TownyAdminOffListener;
import dev.sirbepy.mc.tweaks.listener.WitherGuardListener;
import dev.sirbepy.mc.tweaks.netherwishes.AutoReplant;
import dev.sirbepy.mc.tweaks.netherwishes.HeartyFood;
import dev.sirbepy.mc.tweaks.netherwishes.NetherWishesCommand;
import dev.sirbepy.mc.tweaks.netherwishes.ZombieTruce;
import dev.sirbepy.mc.tweaks.seagifts.SeaGifts;
import dev.sirbepy.mc.tweaks.seagifts.SeaGiftsCommand;
import dev.sirbepy.mc.tweaks.seagifts.SeaGiftsStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class TweaksPlugin extends JavaPlugin {

    private static final String SEA_GIFTS_OWNERS = "sea-gifts.owners";
    private static final String SEA_GIFTS_OWNER = "sea-gifts.owner";
    static final String TOWNY_ADMIN_OFF = "towny-admin-off";

    private LoyaltyTridentListener tridents;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Live's config.yml predates the messages: section; copyDefaults(true) makes getConfigurationSection
        // ("messages") see the jar's keys for any the server's file lacks. Nothing saves here, so the file
        // only gains those keys if an owner command's saveConfig() later writes it.
        getConfig().options().copyDefaults(true);
        Messages messages = new Messages(getConfig().getConfigurationSection("messages"));
        // The server's copy is the one kept up to date at each restart; the jar's only seeds a fresh server.
        File changelog = new File(getDataFolder(), "changelog.yml");
        if (!changelog.exists()) {
            saveResource("changelog.yml", false);
        }
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new ChangelogCommand(changelog, messages).registerInto(event.registrar()));
        getServer().getPluginManager().registerEvents(new MinecartSpeedListener(), this);
        MinecartSpeedListener.applyToLoaded(getServer().getWorlds());
        getServer().getPluginManager().registerEvents(new PollosGunListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnGuardListener(), this);
        SpawnSafeListener spawnSafe = new SpawnSafeListener();
        getServer().getPluginManager().registerEvents(spawnSafe, this);
        getServer().getScheduler().runTaskTimer(this, () -> spawnSafe.sweep(getServer().getWorlds().getFirst()), 20L, 20L);
        if (getConfig().getBoolean("motd.enabled", true)) {
            getServer().getPluginManager().registerEvents(new DailyMotdListener(
                    getConfig().getString("motd.first-line", "The Claude SMP"),
                    getConfig().getStringList("motd.daily-lines"),
                    LocalDate::now), this);
        }
        if (getConfig().getBoolean("phantoms.enabled", true)) {
            PhantomBuffListener phantoms = new PhantomBuffListener(this, ThreadLocalRandom.current());
            getServer().getPluginManager().registerEvents(phantoms, this);
            phantoms.trackLoaded(getServer().getWorlds());
            getServer().getScheduler().runTaskTimer(this, phantoms::tick, 1L, 1L);
        }
        // The key predates the stray-trident return; kept so live's config.yml needs no edit.
        if (getConfig().getBoolean("void-tridents.enabled", true)) {
            tridents = new LoyaltyTridentListener(getServer(),
                    new PendingTridents(new File(getDataFolder(), "loyalty-tridents.yml"), getLogger()));
            getServer().getPluginManager().registerEvents(tridents, this);
            getServer().getScheduler().runTaskTimer(this, tridents::tick, 1L, 1L);
        }
        if (getConfig().getBoolean("earthbreaker.enabled", true)) {
            EarthbreakerListener earthbreaker = new EarthbreakerListener(getServer());
            getServer().getPluginManager().registerEvents(earthbreaker, this);
            getServer().getScheduler().runTaskTimer(this, earthbreaker::tick, 1L, 1L);
        }
        if (getConfig().getBoolean("wither-guard.enabled", true)) {
            // A lambda keeps TownyTowns (and so Towny's classes) unloaded when Towny isn't there.
            Predicate<Location> inTown = getServer().getPluginManager().isPluginEnabled("Towny")
                    ? location -> TownyTowns.inAnyTown(location)
                    : location -> false;
            WitherGuardListener witherGuard = new WitherGuardListener(inTown, messages);
            getServer().getPluginManager().registerEvents(witherGuard, this);
            witherGuard.trackLoaded(getServer().getWorlds());
            getServer().getScheduler().runTaskTimer(this, witherGuard::tick, 20L, 20L);
        }
        // ScytheMasterListener references AuraSkills API classes, so it must not load without it.
        if (getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            getServer().getPluginManager().registerEvents(new ScytheMasterListener(), this);
        }
        // TownyAdminBypass references Towny classes, so it must not load without it.
        if (getServer().getPluginManager().isPluginEnabled("Towny")) {
            getServer().getPluginManager().registerEvents(new TownyAdminOffListener(this,
                    () -> uuidsAt(getConfig(), TOWNY_ADMIN_OFF), new TownyAdminBypass(getLogger())), this);
        }
        if (getConfig().getBoolean("sea-gifts.enabled", true)) {
            SeaGifts seaGifts = new SeaGifts(getServer(),
                    new SeaGiftsStore(new File(getDataFolder(), "sea-gifts.yml"), getLogger()),
                    () -> ownersAt(getConfig(), SEA_GIFTS_OWNERS, SEA_GIFTS_OWNER), System::currentTimeMillis, messages);
            getServer().getPluginManager().registerEvents(seaGifts, this);
            getServer().getScheduler().runTaskTimer(this, seaGifts::tick, 20L, 20L);
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                    new SeaGiftsCommand(seaGifts, this::setSeaGiftsOwner, messages).registerInto(event.registrar()));
        }
        if (getConfig().getBoolean("nether-wishes.enabled", true)) {
            Supplier<UUID> coffie = () -> ownerAt("nether-wishes.owner");
            getServer().getPluginManager().registerEvents(new AutoReplant(this, coffie), this);
            ZombieTruce truce = new ZombieTruce(getServer(), coffie, System::currentTimeMillis);
            getServer().getPluginManager().registerEvents(truce, this);
            getServer().getScheduler().runTaskTimer(this, truce::tick, 20L, 20L);
            getServer().getPluginManager().registerEvents(new HeartyFood(this, coffie, messages), this);
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                    new NetherWishesCommand(owner -> setOwnerAt("nether-wishes.owner", owner), messages).registerInto(event.registrar()));
        }
    }

    @Override
    public void onDisable() {
        // Paper disables plugins before it disconnects and saves players, so owners still online get theirs now.
        if (tridents != null) tridents.returnAll();
    }

    private UUID ownerAt(String path) {
        return ownerAt(getConfig(), path);
    }

    /**
     * saveDefaultConfig() never touches an existing config.yml, so a live file older than a wish lacks its
     * owner key. getString(path) without a fallback reads the jar's config.yml defaults for it; an explicit
     * fallback would skip them and leave the wish with no owner.
     */
    static UUID ownerAt(Configuration config, String path) {
        return parse(config.getString(path));
    }

    /**
     * Every UUID in the list at {@code listPath} (the jar's list when the server's file has none), plus the
     * single owner at {@code legacyPath} from before the wish had more than one owner.
     */
    static Set<UUID> ownersAt(Configuration config, String listPath, String legacyPath) {
        Set<UUID> owners = uuidsAt(config, listPath);
        UUID legacy = ownerAt(config, legacyPath);
        if (legacy != null) owners.add(legacy);
        return owners;
    }

    /** Every UUID in the list at {@code path}, the jar's list when the server's file has none; bad entries skipped. */
    static Set<UUID> uuidsAt(Configuration config, String path) {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (String value : config.getStringList(path)) {
            UUID uuid = parse(value);
            if (uuid != null) uuids.add(uuid);
        }
        return uuids;
    }

    private static UUID parse(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void setOwnerAt(String path, UUID owner) {
        getConfig().set(path, owner.toString());
        saveConfig();
    }

    /** Adds or removes one sea gifts owner; false when that changed nothing. Folds the legacy key into the list. */
    private boolean setSeaGiftsOwner(UUID owner, boolean isOwner) {
        Set<UUID> owners = ownersAt(getConfig(), SEA_GIFTS_OWNERS, SEA_GIFTS_OWNER);
        if (!(isOwner ? owners.add(owner) : owners.remove(owner))) return false;
        getConfig().set(SEA_GIFTS_OWNERS, owners.stream().map(UUID::toString).toList());
        getConfig().set(SEA_GIFTS_OWNER, null);
        saveConfig();
        return true;
    }
}
