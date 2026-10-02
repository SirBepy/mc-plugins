package dev.sirbepy.mc.safechests.bolt;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.source.Source;
import org.popcraft.bolt.util.Permission;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thin wrapper over {@link BoltAPI}, fetched once via the services manager. SafeChests never touches
 * Bolt's internal storage (AccessList/Store), only the public API: owner-wide grants are implemented
 * by adding {@code player:<uuid>} access entries to every {@link BlockProtection} an owner has, see
 * {@link dev.sirbepy.mc.safechests.grant.GrantManager} for why.
 */
public final class BoltAccess {

    private final BoltAPI api;

    public BoltAccess(BoltAPI api) {
        this.api = api;
    }

    public static BoltAccess load() {
        BoltAPI api = Bukkit.getServicesManager().load(BoltAPI.class);
        if (api == null) {
            throw new IllegalStateException("Bolt's BoltAPI service is not registered, is Bolt enabled?");
        }
        return new BoltAccess(api);
    }

    public BoltAPI api() {
        return api;
    }

    /** Resolves the protection backing an inventory, matching Bolt's own container/double-chest handling. */
    public Protection protectionFor(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        return switch (holder) {
            case DoubleChest doubleChest -> api.findProtection(doubleChest.getLocation().getBlock());
            case BlockInventoryHolder blockHolder -> api.findProtection(blockHolder.getBlock());
            case Entity entity -> api.findProtection(entity);
            case null, default -> null;
        };
    }

    public Protection protectionLookingAt(org.bukkit.entity.Player player, int maxDistance) {
        Block block = player.getTargetBlockExact(maxDistance);
        if (block == null || !(block.getState() instanceof Container)) {
            return null;
        }
        return api.findProtection(block);
    }

    public boolean canAccess(Protection protection, UUID uuid, String... permissions) {
        return api.canAccess(protection, uuid, permissions);
    }

    public boolean canOpen(Protection protection, UUID uuid) {
        return api.canAccess(protection, uuid, Permission.OPEN);
    }

    /** All block protections owned by the given UUID. Entity protections (boats, item frames) are out of scope. */
    public List<BlockProtection> blockProtectionsOwnedBy(UUID owner) {
        List<BlockProtection> owned = new ArrayList<>();
        for (Protection protection : api.loadProtections()) {
            if (protection instanceof BlockProtection blockProtection && owner.equals(protection.getOwner())) {
                owned.add(blockProtection);
            }
        }
        return owned;
    }

    public void grantAccess(Protection protection, UUID grantee, String accessType) {
        protection.getAccess().put(Source.player(grantee).toString(), accessType);
        api.saveProtection(protection);
    }

    public void revokeAccess(Protection protection, UUID grantee) {
        if (protection.getAccess().remove(Source.player(grantee).toString()) != null) {
            api.saveProtection(protection);
        }
    }

    public String ownerDisplayName(UUID owner) {
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
        String name = offline.getName();
        return name != null ? name : owner.toString();
    }
}
