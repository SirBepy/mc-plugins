package dev.sirbepy.mc.market;

import org.bukkit.Server;

import java.util.UUID;

/** The real {@link HomeShop.HomeGrant}: LuckPerms has no Java API dependency here, so this just
 * dispatches the same console commands Joe would type by hand. */
final class ConsoleHomeGrant implements HomeShop.HomeGrant {

    private final Server server;

    ConsoleHomeGrant(Server server) {
        this.server = server;
    }

    @Override
    public boolean grant(UUID playerId, int next) {
        boolean granted = server.dispatchCommand(server.getConsoleSender(),
                "lp user " + playerId + " permission set huskhomes.max_homes." + next + " true");
        // Only a node this shop itself granted (4+); the default group's own huskhomes.max_homes.3
        // is never touched.
        if (granted && next - 1 >= 4) {
            server.dispatchCommand(server.getConsoleSender(),
                    "lp user " + playerId + " permission unset huskhomes.max_homes." + (next - 1));
        }
        return granted;
    }
}
