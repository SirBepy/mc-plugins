package dev.sirbepy.mc.endfight.reward;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetic dragon auras unlocked by Dragon Hoard rolls (see {@link RollEngine}): one worn at a
 * time, toggled with {@code /dragonaura <name|off>} through {@link #toggle} (the altar menu a
 * later phase adds will call the same method directly), ticked as a particle puff at the
 * wearer's feet - modelled on haru's {@code effect.Auras}.
 *
 * <p>{@link #toggle} and {@link #ownedAuraNames} go straight to {@link RewardProgressStore}
 * (the source of truth); {@link #tick}/{@link #tickAll} read a small in-memory cache instead of
 * re-reading the YAML file every tick - {@link #reapplyOnJoin} (and every {@link #toggle} call)
 * keeps that cache in sync.
 */
public final class DragonAuraService {

    public enum ToggleResult { OFF, SWITCHED, NOT_OWNED }

    public record ToggleOutcome(ToggleResult result, DragonAura aura) {
    }

    private final RewardProgressStore progress;
    private final Map<UUID, DragonAura> activeCache = new ConcurrentHashMap<>();

    public DragonAuraService(RewardProgressStore progress) {
        this.progress = progress;
    }

    public ToggleOutcome toggle(Player player, String arg) {
        UUID id = player.getUniqueId();
        PlayerProgress current = progress.load(id);
        if ("off".equalsIgnoreCase(arg)) {
            progress.save(id, withActiveAura(current, null));
            activeCache.remove(id);
            return new ToggleOutcome(ToggleResult.OFF, null);
        }
        DragonAura aura = DragonAura.byName(arg);
        if (aura == null || !current.auras().contains(aura)) {
            return new ToggleOutcome(ToggleResult.NOT_OWNED, aura);
        }
        progress.save(id, withActiveAura(current, aura));
        activeCache.put(id, aura);
        return new ToggleOutcome(ToggleResult.SWITCHED, aura);
    }

    public List<String> ownedAuraNames(Player player) {
        return progress.load(player.getUniqueId()).auras().stream().map(DragonAura::displayName).toList();
    }

    /** Loads this player's worn aura from disk into the tick cache - call on join. */
    public void reapplyOnJoin(Player player) {
        DragonAura aura = progress.load(player.getUniqueId()).activeAura();
        if (aura != null) {
            activeCache.put(player.getUniqueId(), aura);
        } else {
            activeCache.remove(player.getUniqueId());
        }
    }

    /** One tick of every given online player's worn aura - register on a repeating timer. */
    public void tickAll(Iterable<? extends Player> online) {
        for (Player player : online) {
            tick(player);
        }
    }

    private void tick(Player player) {
        DragonAura aura = activeCache.get(player.getUniqueId());
        if (aura == null || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location at = player.getLocation().add(0, 0.1, 0);
        player.getWorld().spawnParticle(aura.particle(), at, aura.count(), aura.spread(), 0.4, aura.spread());
    }

    private static PlayerProgress withActiveAura(PlayerProgress current, DragonAura aura) {
        return new PlayerProgress(current.armor(), current.auras(), aura, current.heads(), current.weeklyHoards());
    }
}
