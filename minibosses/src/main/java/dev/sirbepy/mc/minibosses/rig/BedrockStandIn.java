package dev.sirbepy.mc.minibosses.rig;

import dev.sirbepy.mc.minibosses.engine.EngineKeys;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What Bedrock players see in place of the model. Geyser has no mapping for item displays, so a
 * Bedrock client would find an invisible boss; instead a plain Ravager follows the base mob and is
 * hidden from every Java player. Its only job is to be seen and hit: damage dealt to it is cancelled
 * and forwarded to the real boss by the Guardian's own listener (see {@link #ownerOf}).
 *
 * <p>It is a fight entity (spawned through the engine's {@code FightEntities}), carries no Guardian
 * data, and drops nothing.
 */
public final class BedrockStandIn {

    /** On the stand-in, so a listener tells it from the boss and from every other Ravager. */
    public static final String TAG = "minibosses.stand_in";

    /** Re-hides the stand-in from Java players that logged in or were hidden from late, and shows it to Bedrock ones. */
    static final int REFRESH_TICKS = 20;

    private final Plugin plugin;
    private final LivingEntity base;
    private final Predicate<UUID> isBedrock;
    private final Ravager standIn;
    private Location last;
    private long tick;

    BedrockStandIn(FightContext ctx, Predicate<UUID> isBedrock, double scale) {
        this.plugin = ctx.plugin();
        this.base = ctx.boss();
        this.isBedrock = isBedrock;
        this.last = base.getLocation();
        this.standIn = ctx.entities().spawn(last, Ravager.class, r -> {
            r.addScoreboardTag(TAG);
            r.setAI(false);
            r.setSilent(true);
            r.setCollidable(false);
            r.setGravity(false);
            r.setCustomNameVisible(false);
            AttributeInstance size = r.getAttribute(Attribute.SCALE);
            if (size != null) {
                size.setBaseValue(scale);
            }
        });
        // Hidden in the spawning tick, before the tracker has told anyone about it.
        refreshVisibility();
    }

    public Ravager entity() {
        return standIn;
    }

    /** The boss UUID that owns {@code entity} if it is a stand-in. */
    public static Optional<UUID> ownerOf(Plugin plugin, Entity entity) {
        if (!entity.getScoreboardTags().contains(TAG)) {
            return Optional.empty();
        }
        String raw = entity.getPersistentDataContainer().get(EngineKeys.owner(plugin), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Called every server tick: follows the base, and refreshes who can see it once a second. */
    void tick() {
        tick++;
        if (!standIn.isValid()) {
            return;
        }
        Location now = base.getLocation();
        if (!now.equals(last) || now.getYaw() != last.getYaw()) {
            standIn.teleport(now);
            last = now;
        }
        if (tick % REFRESH_TICKS == 0) {
            refreshVisibility();
        }
    }

    /** Hides the stand-in from {@code player} unless they are on Bedrock; for a player who just joined. */
    public void applyTo(Player player) {
        if (isBedrock.test(player.getUniqueId())) {
            if (!player.canSee(standIn)) {
                player.showEntity(plugin, standIn);
            }
        } else if (player.canSee(standIn)) {
            player.hideEntity(plugin, standIn);
        }
    }

    private void refreshVisibility() {
        for (Player player : standIn.getWorld().getPlayers()) {
            applyTo(player);
        }
    }
}
