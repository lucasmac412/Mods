package br.com.celestialdragon.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DragonAttackAnimationTest {
    private DragonAttackAnimation.AttackPose at(float elapsed) {
        return DragonAttackAnimation.pose(CelestialDragonEntity.BITE_DURATION - elapsed, 0, 0);
    }

    @Test void jawsCloseOnDamageTickAndStayClosedForTheImpact() {
        var preparing = at(CelestialDragonEntity.BITE_HIT_DELAY - 1.5F);
        var impact = at(CelestialDragonEntity.BITE_HIT_DELAY);
        assertTrue(preparing.jawOpening() > 0.5F, "Anticipation must visibly open the mouth");
        assertTrue(impact.jawOpening() < 0.06F, "Teeth must close when damage occurs");
        assertTrue(at(CelestialDragonEntity.BITE_HIT_DELAY + 0.5F).jawOpening() < 0.06F);
        assertTrue(preparing.pullBack() > 0 && impact.pullBack() < 0, "The strike moves forward");
    }

    @Test void headDoesNotTeleportAndJawCannotOverextend() {
        var previous = at(0);
        for (float elapsed = 0.025F; elapsed < CelestialDragonEntity.BITE_DURATION; elapsed += 0.025F) {
            var pose = at(elapsed);
            assertTrue(Float.isFinite(pose.jawOpening()) && Float.isFinite(pose.pullBack()));
            assertTrue(pose.jawOpening() >= 0 && pose.jawOpening() < Math.toRadians(45));
            assertTrue(Math.abs(pose.pullBack()) / 16 < 0.30, "Neck sliding stays below 0.3 blocks");
            assertTrue(Math.abs(pose.pullBack() - previous.pullBack()) < 0.20F, "No pose jumps");
            assertTrue(Math.abs(pose.waveDamping() - previous.waveDamping()) < 0.03F, "Body settles smoothly");
            previous = pose;
        }
    }

    @Test void recoveryReturnsToIdleAndAttacksDoNotAccumulateState() {
        var idle = DragonAttackAnimation.pose(0, 0, 0);
        var recovering = at(CelestialDragonEntity.BITE_DURATION - 0.001F);
        assertEquals(idle.jawOpening(), recovering.jawOpening(), 0.00001F);
        assertEquals(idle.pullBack(), recovering.pullBack(), 0.00001F);
        assertEquals(idle.waveDamping(), recovering.waveDamping(), 0.00001F);
        assertEquals(idle.jawOpening(), at(0).jawOpening(), 0.00001F);
        assertEquals(idle.pullBack(), at(0).pullBack(), 0.00001F);
        var first = at(3);
        DragonAttackAnimation.pose(0, 27, 100);
        assertEquals(first, at(3), "Rendering another dragon's beam cannot affect this bite");
    }
}
