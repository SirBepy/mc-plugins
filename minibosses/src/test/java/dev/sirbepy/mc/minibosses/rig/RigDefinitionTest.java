package dev.sirbepy.mc.minibosses.rig;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigDefinitionTest {

    private static final Logger LOG = Logger.getAnonymousLogger();

    private static final String MINIMAL = """
            {"id":"t","modelScale":2.0,"tiers":["normal","heroic","mythic"],
             "hitbox":{"entity":"ravager","scaleAttribute":1.4},
             "palettes":{"normal":[1,2,3],"heroic":[4,5,6],"mythic":[7,8,9]},
             "parts":[{"name":"a","model":"claudesmp:x/a","parent":null,"pivot":[0,1,0],"scale":1.0,"tiers":["normal"]},
                      {"name":"b","model":"claudesmp:x/b","parent":"a","pivot":[0,1,0],"scale":1.0,"tiers":["mythic"]}],
             "poses":{"idle":{"b":{"rot":[10,0,0]}}},
             "anchors":{"tip":{"part":"b","local":[0,0,-1]}}}
            """;

    @Test
    void theShippedSentinelRigLoadsWithItsPartsPosesAnchorsAndPalettes() {
        RigDefinition def = RigDefinition.load("overgrown_sentinel", LOG).orElseThrow();

        assertEquals(29, def.parts().size());
        assertEquals(2.0, def.modelScale());
        assertEquals("ravager", def.hitboxEntity());
        assertEquals(1.4, def.hitboxScale());
        assertTrue(def.poseNames().containsAll(java.util.List.of("idle", "walk_a", "walk_b", "slam_windup", "slam_hit",
                "sweep_windup", "sweep_hit", "rooted", "heartguard", "stagger")));
        assertTrue(def.anchorNames().containsAll(java.util.List.of("heart", "eyes", "slam_fist", "sweep_claw")));
        assertEquals(3, def.palette(Tier.MYTHIC).length);
        assertEquals(0xFFC62E, def.palette(Tier.MYTHIC)[2]);
    }

    @Test
    void aPoseLeavesUnmentionedPartsAtTheIdentity() {
        RigDefinition def = RigDefinition.parse(MINIMAL);

        assertEquals(RigDefinition.PartPose.IDENTITY, def.poseOf("idle", "a"));
        assertEquals(10.0, def.poseOf("idle", "b").rot().x);
        assertEquals(1.0, def.poseOf("idle", "b").scale());
    }

    @Test
    void aMissingRigFileIsEmptyNotAnException() {
        assertTrue(RigDefinition.load("no_such_guardian", LOG).isEmpty());
    }

    @Test
    void aMalformedRigIsRejected() {
        assertThrows(RuntimeException.class, () -> RigDefinition.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> RigDefinition.parse(MINIMAL.replace("\"parent\":\"a\"", "\"parent\":\"zzz\"")));
        assertThrows(IllegalArgumentException.class, () -> RigDefinition.parse(MINIMAL.replace("\"idle\"", "\"stand\"")));
        assertThrows(IllegalArgumentException.class, () -> RigDefinition.parse(MINIMAL.replace("\"part\":\"b\"", "\"part\":\"q\"")));
        assertThrows(IllegalArgumentException.class, () -> RigDefinition.parse(MINIMAL.replace("[7,8,9]", "[7,8]")));
    }
}
