package dev.sirbepy.mc.minibosses;

import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MinibossesPlugin extends JavaPlugin {

    private GuardianRegistry guardians;
    private GuardianAbilityRegistry abilities;

    @Override
    public void onEnable() {
        File guardiansDir = new File(getDataFolder(), "guardians");
        // Bundled placeholder, copied once so the directory is never empty on a fresh install;
        // real Guardians get their own saveResource line here as each one's todo lands.
        if (!new File(guardiansDir, "test-guardian.yml").exists()) {
            saveResource("guardians/test-guardian.yml", false);
        }

        guardians = new GuardianRegistry(guardiansDir, getLogger());
        guardians.load();
        abilities = new GuardianAbilityRegistry();

        getLogger().info("Minibosses ready: " + guardians.all().size()
                + " Guardian(s) loaded (scaffold only, no boss logic yet).");
    }

    @Override
    public void onDisable() {
        getLogger().info("Minibosses disabled.");
    }
}
