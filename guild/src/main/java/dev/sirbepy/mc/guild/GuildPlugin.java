package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.guild.command.GuildAdminCommand;
import dev.sirbepy.mc.guild.command.GuildCommand;
import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.econ.VaultBank;
import dev.sirbepy.mc.guild.events.FirstDragon;
import dev.sirbepy.mc.guild.gui.GuildChannel;
import dev.sirbepy.mc.guild.gui.GuildMenus;
import dev.sirbepy.mc.guild.hall.HallGuard;
import dev.sirbepy.mc.guild.staff.Dialogue;
import dev.sirbepy.mc.guild.staff.StaffListener;
import dev.sirbepy.mc.guild.staff.StaffNpcs;
import dev.sirbepy.mc.guild.listener.BossCredit;
import dev.sirbepy.mc.guild.listener.DragonSlayers;
import dev.sirbepy.mc.guild.listener.ExploreTracker;
import dev.sirbepy.mc.guild.listener.FarmGuard;
import dev.sirbepy.mc.guild.listener.ProgressListener;
import dev.sirbepy.mc.guild.listener.TitleListener;
import dev.sirbepy.mc.guild.time.ZoneClock;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class GuildPlugin extends JavaPlugin {

    private final AtomicReference<GuildConfig> config = new AtomicReference<>();
    private GuildStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(GuildConfig.from(getConfig()));

        store = new GuildStore(new File(getDataFolder(), "guild.yml"), getLogger());
        ZoneClock clock = new ZoneClock(config::get);
        Guild guild = new Guild(config::get, clock, store, new VaultBank(getServer()), new Random());
        GuildMenus menus = new GuildMenus(guild);
        // Players with the Claude SMP tree mod get every Guild menu as its walnut-and-gold screen
        // instead of the chest; everyone else is unaffected since the channel only fires once a
        // client registers it.
        menus.setChannel(GuildChannel.register(this, guild, menus));
        BossCredit bosses = new BossCredit(clock::nowMillis);
        FarmGuard farms = new FarmGuard(config::get, clock::nowMillis);
        ExploreTracker explore = new ExploreTracker(guild);
        DragonSlayers dragons = new DragonSlayers(new FirstDragon(guild, getLogger()), clock::nowMillis);

        BedrockForms bedrock = BedrockForms.create(this);
        StaffNpcs staff = new StaffNpcs(this, store, bedrock::isBedrock);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(bosses, this);
        pm.registerEvents(farms, this);
        pm.registerEvents(new ProgressListener(guild, bosses, farms), this);
        pm.registerEvents(new HallGuard(store), this);
        pm.registerEvents(dragons, this);
        getServer().getWorlds().forEach(dragons::checkEarlierKill);
        pm.registerEvents(new StaffListener(staff, guild, menus, new Dialogue(guild, new Random())), this);
        pm.registerEvents(staff.viewers(), this);
        pm.registerEvents(new TitleListener(guild), this);
        if (pm.isPluginEnabled("Names")) {
            guild.titleTags().attach(NamesTitleSink.load(getServer()));
            guild.titleTags().updateAll(store.members());
        } else {
            getLogger().info("Names isn't enabled; Guild title tags won't show beside names.");
        }

        var scheduler = getServer().getScheduler();
        scheduler.runTaskTimer(this, () -> explore.tick(getServer().getOnlinePlayers()), 40L, 40L);
        scheduler.runTaskTimer(this, bosses::sweep, 1200L, 1200L);
        scheduler.runTaskTimer(this, farms::sweep, 1200L, 1200L);
        scheduler.runTaskTimer(this, store::flush, 600L, 600L);
        scheduler.runTaskTimer(this, staff::ensure, 60L, 100L);
        scheduler.runTaskTimer(this, staff::lookAtVisitors, 20L, 10L);
        scheduler.runTaskTimer(this, guild.events()::tick, 100L, 100L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            new GuildCommand(guild, menus).registerInto(event.registrar());
            new GuildAdminCommand(guild, this::setGuildmaster, this::reload, staff).registerInto(event.registrar());
        });
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
    }

    private void setGuildmaster(String uuid) {
        getConfig().set("guildmaster", uuid);
        saveConfig();
        config.set(GuildConfig.from(getConfig()));
    }

    private void reload() {
        reloadConfig();
        config.set(GuildConfig.from(getConfig()));
    }
}
