package br.com.celestialdragon.client;

import br.com.celestialdragon.entity.DragonAttackAnimation;

/** Shared attack curves also position the server muzzle, keeping the energy at the animated mouth. */
final class DragonAnimation {
    private DragonAnimation() { }
    static DragonAttackAnimation.AttackPose pose(float bite, float beam, float time) {
        return DragonAttackAnimation.pose(bite, beam, time);
    }
    static float smooth(float value) { return DragonAttackAnimation.smooth(value); }
}
