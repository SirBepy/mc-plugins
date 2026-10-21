package dev.sirbepy.mc.minibosses;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.arena.ArenaCommand;
import dev.sirbepy.mc.minibosses.arena.ArenaFights;
import dev.sirbepy.mc.minibosses.arena.ArenaRegistry;
import dev.sirbepy.mc.minibosses.arena.GuardianTestCommand;
import dev.sirbepy.mc.minibosses.arena.SummonGate;
import dev.sirbepy.mc.minibosses.chronicle.Chronicle;
import dev.sirbepy.mc.minibosses.chronicle.ChronicleCipher;
import dev.sirbepy.mc.minibosses.chronicle.ChronicleCommand;
import dev.sirbepy.mc.minibosses.chronicle.ChronicleListener;
import dev.sirbepy.mc.minibosses.chronicle.ChronicleProgress;
import dev.sirbepy.mc.minibosses.chronicle.ChronicleText;
import dev.sirbepy.mc.minibosses.chronicle.ChroniclerNpc;
import dev.sirbepy.mc.minibosses.engine.GuardianRuntime;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.progress.KillRecorder;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.relic.RelicActiveListener;
import dev.sirbepy.mc.minibosses.relic.RelicRanks;
import dev.sirbepy.mc.minibosses.relic.RelicUpgradeListener;
import dev.sirbepy.mc.minibosses.relic.RelicUpgrades;
import dev.sirbepy.mc.minibosses.relic.Relics;
import dev.sirbepy.mc.minibosses.relic.SigilGuardListener;
import dev.sirbepy.mc.minibosses.relic.Sigils;
import dev.sirbepy.mc.minibosses.sentinel.HeartOfTheWild;
import dev.sirbepy.mc.minibosses.sentinel.NaturePulse;
import dev.sirbepy.mc.minibosses.sentinel.NaturePulseVisuals;
import dev.sirbepy.mc.minibosses.sentinel.SentinelDiscovery;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import dev.sirbepy.mc.minibosses.sentinel.SentinelLoot;
import dev.sirbepy.mc.minibosses.sentinel.SummonCooldown;
import dev.sirbepy.mc.minibosses.sovereign.HollowCrown;
import dev.sirbepy.mc.minibosses.sovereign.RiftZonesAbility;
import dev.sirbepy.mc.minibosses.sovereign.SovereignDiscovery;
import dev.sirbepy.mc.minibosses.sovereign.SovereignFight;
import dev.sirbepy.mc.minibosses.sovereign.SovereignLoot;
import dev.sirbepy.mc.minibosses.sovereign.VoidPullAbility;
import dev.sirbepy.mc.minibosses.sovereign.VoidStepAbility;
import dev.sirbepy.mc.minibosses.summon.TierPicker;
import dev.sirbepy.mc.minibosses.tyrant.AshenCore;
import dev.sirbepy.mc.minibosses.tyrant.AshenStormAbility;
import dev.sirbepy.mc.minibosses.tyrant.BlazeSummonAbility;
import dev.sirbepy.mc.minibosses.tyrant.BlazeWaveAbility;
import dev.sirbepy.mc.minibosses.tyrant.TyrantDiscovery;
import dev.sirbepy.mc.minibosses.tyrant.TyrantFight;
import dev.sirbepy.mc.minibosses.tyrant.TyrantLoot;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private HollowCrown hollowCrown;
    private SovereignFight sovereignFight;
    private SovereignDiscovery sovereignDiscovery;
    private ArenaFights arenaFights;
    private GuardianRuntime runtime;
    private BedrockForms bedrock;

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
        if (!new File(guardiansDir, "void_sovereign.yml").exists()) {
            saveResource("guardians/void_sovereign.yml", false);
        }

        guardians = new GuardianRegistry(guardiansDir, getLogger());
        guardians.load();
        abilities = new GuardianAbilityRegistry();
        abilities.register(new BlazeSummonAbility(this, new Random()));
        abilities.register(new BlazeWaveAbility(new Random()));
        abilities.register(new AshenStormAbility(this));
        abilities.register(new VoidStepAbility(this, new Random()));
        abilities.register(new RiftZonesAbility(this, new Random()));
        abilities.register(new VoidPullAbility(this));
        hallOfFame = new HallOfFame(getDataFolder(), getLogger());
        GuardianKills kills = new GuardianKills(getDataFolder(), getLogger());
        TierPicker picker = new TierPicker(guardians, kills);
        ArenaRegistry arenas = new ArenaRegistry(getDataFolder(), getLogger(), guardians);
        arenaFights = new ArenaFights(this, arenas, System::currentTimeMillis);
        SummonGate gate = new SummonGate(arenas, arenaFights);

        DragonFighters sentinelFighters = new DragonFighters(new NamespacedKey(this, "sentinel-fighters"),
                System::currentTimeMillis);
        heartOfTheWild = new HeartOfTheWild(this);
        RelicRanks relicRanks = new RelicRanks(this);
        Sigils sigils = new Sigils(this);
        SentinelLoot loot = new SentinelLoot(heartOfTheWild, relicRanks, sigils);
        // Created before the Guardians so each one can register how its bosses resume after a restart.
        runtime = new GuardianRuntime(this);
        bedrock = BedrockForms.create(this);
        sentinelFight = new SentinelFight(this, guardians, abilities, sentinelFighters, loot, hallOfFame, getLogger(),
                runtime, arenas, bedrock::isBedrock);
        SummonCooldown cooldown = new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, System::currentTimeMillis);
        sentinelDiscovery = new SentinelDiscovery(this, sentinelFight, cooldown, new Random(), picker, gate);

        // Ashen Tyrant: all 3 tiers now real (Phase B), wired into Hall of Fame/Guild the same way
        // the Sentinel's own follow-up dispatch did.
        DragonFighters tyrantFighters = new DragonFighters(new NamespacedKey(this, "tyrant-fighters"),
                System::currentTimeMillis);
        ashenCore = new AshenCore(this);
        TyrantLoot tyrantLoot = new TyrantLoot(ashenCore);
        tyrantFight = new TyrantFight(this, guardians, abilities, tyrantFighters, tyrantLoot, hallOfFame, getLogger());
        dev.sirbepy.mc.minibosses.tyrant.SummonCooldown tyrantCooldown =
                new dev.sirbepy.mc.minibosses.tyrant.SummonCooldown(TyrantDiscovery.COOLDOWN_MILLIS, System::currentTimeMillis);
        tyrantDiscovery = new TyrantDiscovery(this, tyrantFight, tyrantCooldown, new Random(), picker, gate);

        // Void Sovereign: all 3 tiers now real (Phase B), wired into Hall of Fame/Guild the same
        // way the Sentinel's and Tyrant's own follow-up dispatches did. Its own DragonFighters
        // key/PDC tags are fully independent of endfight's End-dimension Ender Dragon fight/arena
        // (see SovereignFight's own javadoc).
        DragonFighters sovereignFighters = new DragonFighters(new NamespacedKey(this, "sovereign-fighters"),
                System::currentTimeMillis);
        hollowCrown = new HollowCrown(this);
        SovereignLoot sovereignLoot = new SovereignLoot(hollowCrown);
        sovereignFight = new SovereignFight(this, guardians, abilities, sovereignFighters, sovereignLoot, hallOfFame, getLogger());
        dev.sirbepy.mc.minibosses.sovereign.SummonCooldown sovereignCooldown =
                new dev.sirbepy.mc.minibosses.sovereign.SummonCooldown(SovereignDiscovery.COOLDOWN_MILLIS, System::currentTimeMillis);
        sovereignDiscovery = new SovereignDiscovery(this, sovereignFight, sovereignCooldown, new Random(), picker, gate);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new KillRecorder(kills), this);
        getServer().getPluginManager().registerEvents(arenaFights, this);
        getServer().getScheduler().runTaskTimer(this, arenaFights::tick, 20L, 20L);
        GuardianTestCommand testCommand = new GuardianTestCommand(this, arenas, arenaFights, gate, Map.of(
                SentinelFight.GUARDIAN_ID, sentinelFight::spawn,
                TyrantFight.GUARDIAN_ID, tyrantFight::spawn,
                SovereignFight.GUARDIAN_ID, sovereignFight::spawn));
        ArenaCommand arenaCommand = new ArenaCommand(arenas, guardians, testCommand);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> arenaCommand.registerInto(event.registrar()));
        getServer().getPluginManager().registerEvents(sentinelFight, this);
        getServer().getPluginManager().registerEvents(sentinelDiscovery, this);
        getServer().getPluginManager().registerEvents(tyrantFight, this);
        getServer().getPluginManager().registerEvents(tyrantDiscovery, this);
        getServer().getPluginManager().registerEvents(sovereignFight, this);
        getServer().getPluginManager().registerEvents(sovereignDiscovery, this);

        // Relic ranks: sigil drops are in the loot tables; this block adds the smithing upgrade,
        // the sigil guard, and the sneak + swap-hands active (only the Heart has one so far).
        Relics relics = new Relics();
        relics.register(SentinelFight.GUARDIAN_ID, Material.HEART_OF_THE_SEA, heartOfTheWild::isHeartOfTheWild);
        relics.register(TyrantFight.GUARDIAN_ID, Material.NETHER_STAR, ashenCore::isAshenCore);
        relics.register(SovereignFight.GUARDIAN_ID, Material.SHULKER_SHELL, hollowCrown::isHollowCrown);
        RelicUpgradeListener relicUpgrades = new RelicUpgradeListener(this, relics,
                new RelicUpgrades(relics, relicRanks, sigils));
        relicUpgrades.registerRecipe();
        getServer().getPluginManager().registerEvents(relicUpgrades, this);
        getServer().getPluginManager().registerEvents(new SigilGuardListener(sigils), this);
        getServer().getPluginManager().registerEvents(new RelicActiveListener(relics, relicRanks,
                System::currentTimeMillis, List.of(new NaturePulse(new NaturePulseVisuals(this)))), this);

        // Shared boss-fight engine: one task ticks every live Guardian's attack loop. The Sentinel
        // registers its resume plan in its own constructor; the other Guardians have none yet.
        getServer().getPluginManager().registerEvents(runtime, this);
        getServer().getScheduler().runTaskTimer(this, runtime::tick, 1L, 1L);
        runtime.sweepStrays();

        // Reapplies Heart of the Wild's passive Regeneration, the Ashen Core's passive Fire
        // Resistance, and the Hollow Crown's passive Slow Falling every second to whoever still
        // holds any of the three relics.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                heartOfTheWild.tick(player);
                ashenCore.tick(player);
                hollowCrown.tick(player);
            }
        }, 20L, 20L);

        enableChronicles(kills);

        getLogger().info("Minibosses ready: " + guardians.all().size() + " Guardian(s) loaded.");
    }

    /** The Guardian Chronicles run only where the story key is present; without it the NPC stays away. */
    private void enableChronicles(GuardianKills kills) {
        ChroniclerNpc chronicler = new ChroniclerNpc(this, getDataFolder(), getLogger(), bedrock::isBedrock);
        Chronicle chronicle = null;
        ChronicleListener listener = null;
        Optional<ChronicleText> story = unlockChronicles();
        if (story.isPresent()) {
            ChronicleText text = story.get();
            TextColor color = TextColor.fromHexString(text.color());
            chronicler.setLook(text.name(), text.title(), color == null ? chronicler.color() : color);
            chronicle = new Chronicle(text, new ChronicleProgress(getDataFolder(), getLogger()), kills, new Random());
            listener = new ChronicleListener(this, chronicle, chronicler);
            getServer().getPluginManager().registerEvents(listener, this);
            getServer().getPluginManager().registerEvents(chronicler.viewers(), this);
            getServer().getScheduler().runTaskTimer(this, chronicler::ensure, 60L, 100L);
            getServer().getScheduler().runTaskTimer(this, chronicler::lookAtVisitors, 20L, 10L);
            getServer().getScheduler().runTaskTimer(this, listener::checkDiscoveries, 40L, 40L);
        }
        ChronicleCommand command = new ChronicleCommand(chronicler, chronicle, listener);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> command.registerInto(event.registrar()));
    }

    private Optional<ChronicleText> unlockChronicles() {
        File keyFile = new File(getDataFolder(), "chronicles.key");
        if (!keyFile.exists()) {
            getLogger().info("No chronicles.key; the Guardian Chronicles stay locked on this server.");
            return Optional.empty();
        }
        try (InputStream in = getResource("chronicles.enc")) {
            if (in == null) {
                getLogger().warning("This jar has no chronicles.enc; the Guardian Chronicles stay locked.");
                return Optional.empty();
            }
            byte[] plain = ChronicleCipher.decrypt(in.readAllBytes(), Files.readString(keyFile.toPath()));
            return Optional.of(ChronicleText.parse(new String(plain, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Could not unlock the Guardian Chronicles; they stay locked", e);
            return Optional.empty();
        }
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
        if (arenaFights != null) {
            arenaFights.releaseAll();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
        getLogger().info("Minibosses disabled.");
    }
}
