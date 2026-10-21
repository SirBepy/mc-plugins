package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Consumer;

/**
 * One warned area attack in flight: draws its {@link Telegraph} for the whole warning window, then
 * hands whoever is inside the shape at that moment to a callback. Two outlines are drawn every 2
 * ticks: the finished shape in its own colour, and a brighter one growing from the centre that
 * reaches the full shape exactly when the strike lands, so players can read when to move.
 *
 * <p>Nothing is ever drawn or hit outside the fight area, even where the shape overhangs it.
 */
public final class TelegraphedStrike {

    /** Blocks between particles along an edge; coarser than this reads as dotted rather than as a line. */
    private static final double SPACING = 0.8;
    private static final int DRAW_EVERY_TICKS = 2;
    /** Lifts particles off the floor so they are not buried in the surface block. */
    private static final double LIFT = 0.15;

    private final FightContext ctx;
    private final Telegraph telegraph;
    private final Consumer<List<Player>> onResolve;
    private int age;
    private boolean done;

    private TelegraphedStrike(FightContext ctx, Telegraph telegraph, Consumer<List<Player>> onResolve) {
        this.ctx = ctx;
        this.telegraph = telegraph;
        this.onResolve = onResolve;
    }

    /** Starts the warning now: plays the start sound and draws the first frame. */
    public static TelegraphedStrike start(FightContext ctx, Telegraph telegraph, Consumer<List<Player>> onResolve) {
        TelegraphedStrike strike = new TelegraphedStrike(ctx, telegraph, onResolve);
        if (telegraph.startSound() != null) {
            ctx.effects().sound(telegraph.shape().anchor(), telegraph.startSound(), 1.5f, 1.0f);
        }
        strike.draw();
        return strike;
    }

    public Telegraph telegraph() {
        return telegraph;
    }

    public boolean done() {
        return done;
    }

    /** Stops the warning without hitting anyone. */
    public void cancel() {
        done = true;
    }

    /** Advances one tick; true once the strike has landed or been cancelled. */
    public boolean tick() {
        if (done) {
            return true;
        }
        age++;
        if (age >= telegraph.warnTicks()) {
            resolve();
        } else if (age % DRAW_EVERY_TICKS == 0) {
            draw();
        }
        return done;
    }

    private void resolve() {
        done = true;
        Shape shape = telegraph.shape();
        if (telegraph.resolveSound() != null) {
            ctx.effects().sound(shape.anchor(), telegraph.resolveSound(), 1.5f, 1.0f);
        }
        List<Player> hit = ctx.fighters().stream()
                .filter(player -> ctx.area().contains(player.getLocation()) && shape.contains(player.getLocation()))
                .filter(player -> !telegraph.grounded() || ctx.isGrounded(player))
                .toList();
        onResolve.accept(hit);
    }

    private void draw() {
        Shape shape = telegraph.shape();
        double progress = telegraph.warnTicks() <= 0 ? 1.0 : Math.min(1.0, (double) age / telegraph.warnTicks());
        drawOutline(shape.outline(SPACING), telegraph.color());
        drawOutline(shape.grown(progress).outline(SPACING), brighter(telegraph.color()));
    }

    private void drawOutline(List<Vector> points, Color color) {
        for (Vector point : points) {
            Location at = new Location(ctx.area().world(), point.getX(), point.getY(), point.getZ());
            if (!ctx.area().contains(at)) {
                continue;
            }
            double y = ctx.area().surfaceY(point.getX(), point.getZ(), point.getY()) + LIFT;
            ctx.effects().dust(ctx.area().world(), point.getX(), y, point.getZ(), color, telegraph.size());
        }
    }

    private static Color brighter(Color color) {
        return Color.fromRGB((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2, (color.getBlue() + 255) / 2);
    }
}
