package dev.sirbepy.mc.endfight.reward;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;

/**
 * The full-set aura "Dragon's Presence" (Joe's decision, 2026-10-09): automatic, no roll needed,
 * while a player wears the Dragonscale chestplate, leggings and boots plus a dragon helmet (the
 * Dragonscale helm or {@link DragonHeadHelm}) - slow purple {@code DRAGON_BREATH} wisps with an
 * occasional {@code END_ROD}/{@code PORTAL} sparkle, subtle and drawn in addition to whatever
 * {@link DragonAura} the player has won and worn through {@link DragonAuraService}. On by
 * default; {@link #toggle} persists an opt-out in {@link DragonPresenceStore}, hidden for
 * spectators and invisible players like haru's {@code effect.Auras}.
 */
public final class DragonPresence {

    // 1 in 4 ticks gets a sparkle on top of the torso wisps, so it reads as occasional, not constant.
    private static final int SPARKLE_CHANCE_DENOMINATOR = 4;

    private final DragonPresenceStore store;
    private final Random random;
    private final ParticleSink particles;

    public DragonPresence(DragonPresenceStore store, Random random, ParticleSink particles) {
        this.store = store;
        this.random = random;
        this.particles = particles;
    }

    public boolean isOn(UUID id) {
        return !store.isOff(id);
    }

    /** {@code /dragonaura presence} and the altar's Auras menu; returns whether it's now on. */
    public boolean toggle(Player player) {
        return store.toggle(player.getUniqueId());
    }

    /** One tick of every given online player's presence - register alongside
     * {@link DragonAuraService#tickAll}. */
    public void tickAll(Iterable<? extends Player> online) {
        for (Player player : online) {
            tick(player);
        }
    }

    private void tick(Player player) {
        if (!shouldDraw(player)) {
            return;
        }
        Location at = player.getLocation().add(0, 1.0, 0);
        particles.spawn(player, Particle.DRAGON_BREATH, at, 1 + random.nextInt(2), 0.3, 0.5, 0.3);
        if (random.nextInt(SPARKLE_CHANCE_DENOMINATOR) == 0) {
            Particle sparkle = random.nextBoolean() ? Particle.END_ROD : Particle.PORTAL;
            particles.spawn(player, sparkle, at, 1, 0.25, 0.45, 0.25);
        }
    }

    private boolean shouldDraw(Player player) {
        return player.getGameMode() != GameMode.SPECTATOR
                && !player.isInvisible()
                && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                && !store.isOff(player.getUniqueId())
                && wearsFullSet(player);
    }

    private static boolean wearsFullSet(Player player) {
        PlayerInventory inv = player.getInventory();
        return isDragonHelmet(inv.getHelmet())
                && isDragonscalePiece(inv.getChestplate(), ArmorPiece.CHESTPLATE)
                && isDragonscalePiece(inv.getLeggings(), ArmorPiece.LEGGINGS)
                && isDragonscalePiece(inv.getBoots(), ArmorPiece.BOOTS);
    }

    private static boolean isDragonHelmet(ItemStack item) {
        return isDragonscalePiece(item, ArmorPiece.HELMET) || DragonHeadHelm.is(item);
    }

    private static boolean isDragonscalePiece(ItemStack item, ArmorPiece piece) {
        if (item == null || item.getType() != piece.material() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
                && meta.getCustomModelDataComponent().getStrings().contains(DragonscaleArmor.CUSTOM_MODEL_DATA);
    }

    /** Lets tests observe spawned particles without reading MockBukkit's particle state (it
     * doesn't expose any) - the real implementation just calls {@code World#spawnParticle}. */
    @FunctionalInterface
    public interface ParticleSink {

        void spawn(Player player, Particle particle, Location at, int count, double spreadX, double spreadY,
                    double spreadZ);

        ParticleSink BUKKIT = (player, particle, at, count, spreadX, spreadY, spreadZ) ->
                AuraParticles.spawn(player.getWorld(), particle, at, count, spreadX, spreadY, spreadZ);
    }
}
