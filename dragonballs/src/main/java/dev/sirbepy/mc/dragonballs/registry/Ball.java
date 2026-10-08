package dev.sirbepy.mc.dragonballs.registry;

import java.util.UUID;

/** Mutable internal state for one of the 7 balls; {@link BallRegistry} is the only writer. */
final class Ball {

    final int star;
    BallState state = BallState.PLACED;
    boolean stone;
    String worldName;
    double x;
    double y;
    double z;
    UUID holderId;
    String holderName;
    long heldSinceMillis;
    UUID entityId;

    Ball(int star) {
        this.star = star;
    }

    BallView snapshot() {
        return new BallView(star, state, stone, worldName, x, y, z, holderId, holderName, heldSinceMillis,
                entityId);
    }
}
