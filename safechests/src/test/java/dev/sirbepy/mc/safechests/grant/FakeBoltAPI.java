package dev.sirbepy.mc.safechests.grant;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.event.Event;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.EntityProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.source.PlayerSourceResolver;
import org.popcraft.bolt.source.Source;
import org.popcraft.bolt.source.SourceResolver;
import org.popcraft.bolt.source.SourceTransformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Minimal {@link BoltAPI} test double: only {@link #loadProtections()} and {@link #saveProtection}
 * are exercised by {@link GrantManager}, so that is all that is backed by real state. Every method
 * GrantManager never calls throws, so a test that reaches one is a signal the production code grew
 * a new dependency this fake needs to catch up with.
 */
// Public so SafeChestsCommandsBedrockTest (a different package) can reuse this fake too, rather
// than duplicating it just to construct a GrantManager for the Bedrock pending-command tests.
public final class FakeBoltAPI implements BoltAPI {

    public final List<Protection> protections = new ArrayList<>();

    @Override
    public Collection<Protection> loadProtections() {
        return List.copyOf(protections);
    }

    @Override
    public void saveProtection(Protection protection) {
        protections.removeIf(p -> p.getId().equals(protection.getId()));
        protections.add(protection);
    }

    @Override
    public boolean isProtectable(Block block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isProtectable(Entity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isProtected(Block block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isProtected(Entity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isProtectedExact(Block block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isProtectedExact(Entity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockProtection createProtection(Block block, UUID owner, String type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityProtection createProtection(Entity entity, UUID owner, String type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockProtection loadProtection(Block block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityProtection loadProtection(Entity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeProtection(Protection protection) {
        protections.removeIf(p -> p.getId().equals(protection.getId()));
    }

    // Minimal real implementation (not a throw): ChestDenialListenerBedrockTest needs a BlockProtection
    // lookup by location, matching Bolt's own block-coordinate identity for a BlockProtection.
    @Override
    public Protection findProtection(Block block) {
        for (Protection protection : protections) {
            if (protection instanceof BlockProtection blockProtection
                    && blockProtection.getWorld().equals(block.getWorld().getName())
                    && blockProtection.getX() == block.getX()
                    && blockProtection.getY() == block.getY()
                    && blockProtection.getZ() == block.getZ()) {
                return protection;
            }
        }
        return null;
    }

    @Override
    public Protection findProtection(Entity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Protection> findProtections(World world, BoundingBox boundingBox) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canAccess(Block block, Player player, String... permissions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canAccess(Entity entity, Player player, String... permissions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canAccess(Protection protection, Player player, String... permissions) {
        throw new UnsupportedOperationException();
    }

    // Minimal real implementation: ChestDenialListenerBedrockTest needs the owner/no-access distinction
    // handleDenied relies on (canOpen must be false for a requester with no grant, true for the owner).
    @Override
    public boolean canAccess(Protection protection, UUID uuid, String... permissions) {
        return protection.getOwner().equals(uuid) || protection.getAccess().containsKey(Source.player(uuid).toString());
    }

    @Override
    public boolean canAccess(Protection protection, SourceResolver sourceResolver, String... permissions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerPlayerSourceResolver(PlayerSourceResolver playerSourceResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Event> void registerListener(Class<T> clazz, Consumer<? super T> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerSourceTransformer(String sourceType, SourceTransformer sourceTransformer) {
        throw new UnsupportedOperationException();
    }
}
