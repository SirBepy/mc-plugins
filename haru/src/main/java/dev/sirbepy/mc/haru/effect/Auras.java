package dev.sirbepy.mc.haru.effect;

import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.rep.Progression;
import dev.sirbepy.mc.haru.shop.catalog.PetalExchange;
import dev.sirbepy.mc.haru.shop.catalog.SecretStock;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Cosmetic particle auras the owner can wear, one at a time; everyone nearby sees them. */
public final class Auras {

    public record Aura(String unlock, String name, String description, Material icon, Particle particle, int count,
                       double spread, boolean hidden) {
    }

    public static final List<Aura> ALL = List.of(
            new Aura(PetalExchange.AURA_SAKURA, "Sakura Aura", "Cherry petals drift around you.",
                    Material.CHERRY_LEAVES, Particle.CHERRY_LEAVES, 2, 0.6, false),
            new Aura(PetalExchange.AURA_FIREFLIES, "Firefly Aura", "Fireflies follow you through the dusk.",
                    Material.FIREFLY_BUSH, Particle.FIREFLY, 1, 0.8, false),
            new Aura(PetalExchange.AURA_WISPS, "Spirit Wisp Aura", "Little lights of the sanctuary keep you company.",
                    Material.END_ROD, Particle.END_ROD, 1, 0.7, false),
            new Aura(Progression.AURA_PETAL_STORM, "Petal Storm", "A whole spring swirls around you.",
                    Material.PINK_PETALS, Particle.CHERRY_LEAVES, 6, 1.0, true),
            new Aura(SecretStock.AURA_SPIRIT_GLOW, "Spirit Glow Aura", "The sanctuary's own light follows you.",
                    Material.GLOW_INK_SAC, Particle.GLOW, 2, 0.6, true));

    private Auras() {
    }

    public static Aura byUnlock(String unlock) {
        return ALL.stream().filter(a -> a.unlock().equals(unlock)).findFirst().orElse(null);
    }

    /** One puff of the owner's worn aura; skipped while they're a spectator or invisible. */
    public static void tick(Player player, OwnerState state) {
        Aura aura = state.activeAura() == null ? null : byUnlock(state.activeAura());
        if (aura == null || !state.has(aura.unlock()) || player.getGameMode() == GameMode.SPECTATOR
                || player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }
        Location at = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(aura.particle(), at, aura.count(), aura.spread(), 0.6, aura.spread(),
                aura.particle() == Particle.END_ROD ? 0.01 : 0);
    }
}
