package br.com.celestialdragon.entity;

import net.minecraft.util.Mth;

/** Absolute-time attack curves: poses never accumulate between frames or different dragons. */
public final class DragonAttackAnimation {
    private static final AttackPose IDLE = new AttackPose(0.08F, 0, 0, 0, 0, 0, 0);

    private DragonAttackAnimation() { }

    public static AttackPose pose(float biteRemaining, float beamRemaining, float time) {
        if (beamRemaining > 0) return beam(beamRemaining, time);
        if (biteRemaining > 0) return bite(biteRemaining);
        return IDLE;
    }

    private static AttackPose bite(float remaining) {
        float elapsed = Math.max(0, CelestialDragonEntity.BITE_DURATION - remaining);
        // Anticipation is small: do not slide the entire head almost a block along the neck.
        float windup = CelestialDragonEntity.BITE_HIT_DELAY - 1.5F;
        if (elapsed <= windup) {
            float amount = smooth(elapsed / windup);
            return new AttackPose(Mth.lerp(amount, 0.08F, 0.70F), 2.5F * amount,
                    -0.045F * amount, -0.025F * amount, amount, 0, 0.65F * amount);
        }
        // The jaws finish closing on the server damage tick, not one tick after it.
        float snapEnd = CelestialDragonEntity.BITE_HIT_DELAY;
        if (elapsed <= snapEnd) {
            float amount = smooth((elapsed - windup) / (snapEnd - windup));
            return new AttackPose(Mth.lerp(amount, 0.70F, 0.035F), Mth.lerp(amount, 2.5F, -4.5F),
                    Mth.lerp(amount, -0.045F, 0.065F), Mth.lerp(amount, -0.025F, 0.045F),
                    Mth.lerp(amount, 1, 0.65F), 0, 0.65F);
        }
        float holdEnd = snapEnd + 1;
        if (elapsed <= holdEnd) {
            float amount = smooth(elapsed - snapEnd);
            return new AttackPose(0.035F, Mth.lerp(amount, -4.5F, -3.6F),
                    Mth.lerp(amount, 0.065F, 0.045F), 0.045F, 0.65F, 0, 0.65F);
        }
        float recover = smooth((elapsed - holdEnd) / (CelestialDragonEntity.BITE_DURATION - holdEnd));
        return new AttackPose(Mth.lerp(recover, 0.035F, 0.08F), -3.6F * (1 - recover),
                0.045F * (1 - recover), 0.045F * (1 - recover), 0.65F * (1 - recover),
                0, 0.65F * (1 - recover));
    }

    private static AttackPose beam(float remaining, float time) {
        float elapsed = Math.max(0, CelestialDragonEntity.BEAM_DURATION - remaining);
        if (elapsed < CelestialDragonEntity.BEAM_CHARGE_TICKS) {
            float charge = smooth(elapsed / CelestialDragonEntity.BEAM_CHARGE_TICKS);
            return new AttackPose(0.08F + charge * 0.95F, charge * 6,
                    charge * -0.1F, charge * -0.02F, charge, 0.05F + charge * 1.05F, charge * 0.7F);
        }
        float recoveryStart = CelestialDragonEntity.BEAM_DURATION - CelestialDragonEntity.BEAM_RECOVERY_TICKS;
        if (elapsed < recoveryStart) {
            float launch = smooth((elapsed - CelestialDragonEntity.BEAM_CHARGE_TICKS) / 2);
            float pulse = Mth.sin(time * 1.3F);
            return new AttackPose(1.03F + pulse * 0.015F,
                    Mth.lerp(launch, 6, -2) + pulse * 0.35F * launch,
                    -0.1F * (1 - launch), -0.02F, 1, 1.05F + pulse * 0.045F, 0.7F);
        }
        float recover = smooth((elapsed - recoveryStart) / CelestialDragonEntity.BEAM_RECOVERY_TICKS);
        return new AttackPose(Mth.lerp(recover, 1.03F, 0.08F),
                -2 * (1 - recover) + Mth.sin(recover * Mth.PI) * 2,
                Mth.sin(recover * Mth.PI) * 0.04F, -0.02F * (1 - recover),
                1 - recover, 1.05F * (1 - recover), 0.7F * (1 - recover));
    }

    public static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public record AttackPose(float jawOpening, float pullBack, float neckPitch, float headPitch,
                      float brace, float energyScale, float waveDamping) { }
}
