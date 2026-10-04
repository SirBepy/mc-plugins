package dev.sirbepy.mc.safechests;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.command.SafeChestsCommands;
import dev.sirbepy.mc.safechests.grant.GrantManager;
import dev.sirbepy.mc.safechests.grant.GrantStore;
import dev.sirbepy.mc.safechests.listener.ChestDenialListener;
import dev.sirbepy.mc.safechests.listener.CopperGolemAccessListener;
import dev.sirbepy.mc.safechests.listener.GrantRevokeListener;
import dev.sirbepy.mc.safechests.listener.NewProtectionListener;
import dev.sirbepy.mc.safechests.request.RequestManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public final class SafeChestsPlugin extends JavaPlugin {

    private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(15);

    private final AtomicReference<SafeChestsConfig> config = new AtomicReference<>();
    private BoltAccess bolt;
    private GrantManager grants;
    private RequestManager requests;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        config.set(new SafeChestsConfig(getConfig()));

        bolt = BoltAccess.load();
        GrantStore store = new GrantStore(new File(getDataFolder(), "grants.yml"), getLogger());
        grants = new GrantManager(bolt, store, () -> config.get().accessType());
        grants.loadAndSweep();
        requests = new RequestManager();
        BedrockForms bedrockForms = BedrockForms.create(this);

        getServer().getPluginManager().registerEvents(new ChestDenialListener(bolt, requests, config::get, bedrockForms), this);
        getServer().getPluginManager().registerEvents(new GrantRevokeListener(bolt, grants), this);
        getServer().getPluginManager().registerEvents(new CopperGolemAccessListener(bolt), this);
        new NewProtectionListener(bolt, grants, this).register();

        new SafeChestsCommands(bolt, grants, requests, config::get, this::reload, bedrockForms).register(this);

        long intervalTicks = SWEEP_INTERVAL.toMillis() / 50;
        getServer().getScheduler().runTaskTimer(this, this::sweep, intervalTicks, intervalTicks);
    }

    private void sweep() {
        // Finishes any revoke the boot pass could not see yet (e.g. Bolt's own protection load was still
        // empty/incomplete at that moment): idempotent, so replaying an already-applied tombstone is a no-op.
        grants.replayPendingTombstones();
        grants.sweepExpiredWhitelist(java.time.Instant.now());
        grants.pruneTombstones(java.time.Instant.now());
        requests.sweepExpired().forEach(request -> {
            org.bukkit.entity.Player requester = getServer().getPlayer(request.requester());
            org.bukkit.entity.Player owner = getServer().getPlayer(request.owner());
            String ownerName = owner != null ? owner.getName() : bolt.ownerDisplayName(request.owner());
            if (requester != null) {
                config.get().messages().send(requester, "request-expired",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("owner", ownerName));
            }
        });
    }

    public void reload() {
        reloadConfig();
        config.set(new SafeChestsConfig(getConfig()));
    }

    public SafeChestsConfig config() {
        return config.get();
    }

    public GrantManager grants() {
        return grants;
    }

    public RequestManager requests() {
        return requests;
    }
}
