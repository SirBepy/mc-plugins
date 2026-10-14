package dev.sirbepy.mc.minibosses;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.sentinel.HeartOfTheWild;
import dev.sirbepy.mc.minibosses.sentinel.RegrowthAbility;
import dev.sirbepy.mc.minibosses.sentinel.RootAbility;
import dev.sirbepy.mc.minibosses.sentinel.SentinelDiscovery;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import dev.sirbepy.mc.minibosses.sentinel.SentinelLoot;
import dev.sirbepy.mc.minibosses.sentinel.SummonCooldown;
import dev.sirbepy.mc.minibosses.sentinel.ThornVinesAbility;
import dev.sirbepy.mc.minibosses.tyrant.AshenCore;
import dev.sirbepy.mc.minibosses.tyrant.AshenStormAbility;
import dev.sirbepy.mc.minibosses.tyrant.BlazeSummonAbility;
import dev.sirbepy.mc.minibosses.tyrant.BlazeWaveAbility;
import dev.sirbepy.mc.minibosses.tyrant.TyrantDiscovery;
import dev.sirbepy.mc.minibosses.tyrant.TyrantFight;
import dev.sirbepy.mc.minibosses.tyrant.TyrantLoot;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Random;

public final class MinibossesPlugin extends JavaPlugin implements Listener {

    private GuardianRegistry guardians;
    private GuardianAbilityRegistry abilities;
    private HallOfFame hallOfFame;
    private HeartOfTheWild heartOfTheWild;
    private SentinelFight sentinelFight;
    private SentinelDiscovery sentinelDiscovery;
    private AshenCore ashenCore;
    private TyrantFight tyrantFight;
    private TyrantDiscovery tyrantDiscovery;

    @Override
    public void onEnable() {
        File guardiansDir = new File(getDataFolder(), "guardians");
        // Bundled placeholder, copied once so the directory is never empty on a fresh install;
        // real Guardians get their own saveResource line here as each one's todo lands.
        if (!new File(guardiansDir, "test-guardian.yml").exists()) {
            saveResource("guardians/test-guardian.yml", false);
        }
        if (!new File(guardiansDir, "overgrown_sentinel.yml").exists()) {
            saveResource("guardians/overgrown_sentinel.yml", false);
        }
        if (!new File(guardiansDir, "ashen_tyrant.yml").exists()) {
            saveResource("guardians/ashen_tyrant.yml", false);
        }

        guardians = new GuardianRegistry(guardiansDir, getLogger());
        guardians.load();
        abilities = new GuardianAbilityRegistry();
        abilities.register(new RootAbility());
        abilities.register(new ThornVinesAbility(this));
        abilities.register(new RegrowthAbility(this));
        abilities.register(new BlazeSummonAbility(this, new Random()));
        abilities.register(new BlazeWaveAbility(new Random()));
        abilities.register(new AshenStormAbility(this));
        hallOfFame = new HallOfFame(getDataFolder(), getLogger());

        DragonFighters sentinelFighters = new DragonFighters(new NamespacedKey(this, "sentinel-fighters"),
                System::currentTimeMillis);
        heartOfTheWild = new HeartOfTheWild(this);
        SentinelLoot loot = new SentinelLoot(heartOfTheWild);
        sentinelFight = new SentinelFight(this, guardians, abilities, sentinelFighters, loot, hallOfFame, getLogger());
        SummonCooldown cooldown = new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, System::currentTimeMillis);
        sentinelDiscovery = new SentinelDiscovery(this, sentinelFight, cooldown, new Random());

        // Ashen Tyrant: all 3 tiers now real (Phase B), wired into Hall of Fame/Guild the same way
        // the Sentinel's own follow-up dispatch did.
        DragonFighters tyrantFighters = new DragonFighters(new NamespacedKey(this, "tyrant-fighters"),
                System::currentTimeMillis);
        ashenCore = new AshenCore(this);
        TyrantLoot tyrantLoot = new TyrantLoot(ashenCore);
        tyrantFight = new TyrantFight(this, guardians, abilities, tyrantFighters, tyrantLoot, hallOfFame, getLogger());
        dev.sirbepy.mc.minibosses.tyrant.SummonCooldown tyrantCooldown =
                new dev.sirbepy.mc.minibosses.tyrant.SummonCooldown(TyrantDiscovery.COOLDOWN_MILLIS, System::currentTimeMillis);
        tyrantDiscovery = new TyrantDiscovery(this, tyrantFight, tyrantCooldown, new Random());

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(sentinelFight, this);
        getServer().getPluginManager().registerEvents(sentinelDiscovery, this);
        getServer().getPluginManager().registerEvents(tyrantFight, this);
        getServer().getPluginManager().registerEvents(tyrantDiscovery, this);

        // Reapplies Heart of the Wild's passive Regeneration and the Ashen Core's passive Fire
        // Resistance every second to whoever still holds either relic.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                heartOfTheWild.tick(player);
                ashenCore.tick(player);
            }
        }, 20L, 20L);

        getLogger().info("Minibosses ready: " + guardians.all().size() + " Guardian(s) loaded.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        hallOfFame.joined(event.getPlayer());
    }

    public HallOfFame hallOfFame() {
        return hallOfFame;
    }

    @Override
    public void onDisable() {
        getLogger().info("Minibosses disabled.");
    }
}
