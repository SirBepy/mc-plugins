package dev.sirbepy.mc.dragonballs.effects;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The poses must keep each dragon in one piece, in front of where it faces, and drawable by the pack. */
class DragonPoseTest {

    private static final Path PACK_ITEMS =
            Path.of("..", "server", "resourcepacks", "claudesmp-items", "assets", "claudesmp", "items");

    private static double distance(DragonPose.Part a, DragonPose.Part b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void shenronsSegmentsStayJoinedAtEveryMoment() {
        for (double t = 0; t < 30; t += 1.7) {
            List<DragonPose.Part> parts = DragonPose.shenron(t, 1.4, 26);
            for (int i = 1; i < parts.size(); i++) {
                // Segments are 24 rig units = 2.1 blocks long at size 1.4; neighbours must overlap.
                double gap = distance(parts.get(i - 1), parts.get(i));
                assertTrue(gap < 2.2, "t=" + t + " segment " + i + " is " + gap + " blocks from the previous one");
            }
        }
    }

    @Test
    void shenronsHeadIsOnTopAndHisBodyHangsBelowIt() {
        List<DragonPose.Part> parts = DragonPose.shenron(0, 1.4, 26);
        assertEquals(26, parts.get(0).y(), 1e-6);
        for (DragonPose.Part part : parts.subList(1, parts.size())) {
            assertTrue(part.y() < 26, part.model() + " rises above the head");
        }
    }

    @Test
    void shenronsNeckIsAnSCurveNotAColumn() {
        // The S swings the neck sideways: across its top third, the horizontal distance from the
        // straight line between the head and the coil top must reach at least 1 block.
        DragonPose.Polyline line = DragonPose.shenronLine(0, 1.4, 26);
        double[] head = line.at(0);
        double[] coilTop = line.at(12.6 * 1.3);
        double widest = 0;
        for (double s = 0; s < 12.6; s += 0.5) {
            double[] p = line.at(s);
            double k = (head[1] - p[1]) / (head[1] - coilTop[1]);
            double lx = head[0] + (coilTop[0] - head[0]) * k;
            double lz = head[2] + (coilTop[2] - head[2]) * k;
            widest = Math.max(widest, Math.hypot(p[0] - lx, p[2] - lz));
        }
        assertTrue(widest > 1.0, "the neck strays only " + widest + " blocks from a straight line");
    }

    @Test
    void porungaFacesTheYawHeIsGiven() {
        // His head pivot is 12 rig units in front of his waist: yaw 0 faces +Z, yaw 90 faces -X.
        DragonPose.Part facingSouth = DragonPose.porunga(0, 2.2, 10, 0f).get(1);
        assertTrue(facingSouth.z() > 1.0 && Math.abs(facingSouth.x()) < 1e-6, facingSouth.toString());
        DragonPose.Part facingWest = DragonPose.porunga(0, 2.2, 10, 90f).get(1);
        assertTrue(facingWest.x() < -1.0 && Math.abs(facingWest.z()) < 1e-6, facingWest.toString());
        assertEquals(90f, facingWest.yaw());
    }

    @Test
    void porungasTailHangsBelowHisWaistAndStaysJoined() {
        List<DragonPose.Part> parts = DragonPose.porunga(3, 2.2, 20, 45f);
        List<DragonPose.Part> tail = parts.subList(4, parts.size());
        assertEquals(DragonPose.PORUNGA_TAIL_SEGMENTS, tail.size());
        for (DragonPose.Part part : tail) {
            assertTrue(part.y() < 20, "the tail hangs down: " + part);
        }
        for (int i = 1; i < tail.size(); i++) {
            assertTrue(distance(tail.get(i - 1), tail.get(i)) < 1.6, "tail segment " + i + " came loose");
        }
    }

    @Test
    void minecraftAnglesFollowTheGameConventions() {
        assertEquals(0f, DragonPose.yaw(0, 1), 1e-4);
        assertEquals(90f, DragonPose.yaw(-1, 0), 1e-4);
        assertEquals(90f, DragonPose.pitch(0, -1, 0), 1e-4, "straight down is pitch 90");
        assertEquals(-90f, DragonPose.pitch(0, 1, 0), 1e-4);
    }

    @Test
    void theServerPackDrawsEveryPartThePosesUse() {
        List<String> models = new ArrayList<>();
        DragonPose.shenron(0, 1.4, 26).forEach(p -> models.add(p.model()));
        DragonPose.porunga(0, 2.2, 20, 0f).forEach(p -> models.add(p.model()));
        for (String model : models) {
            Path definition = PACK_ITEMS.resolve(model + ".json");
            assertTrue(Files.isRegularFile(definition), "no item definition for " + model + " at " + definition);
        }
    }
}
