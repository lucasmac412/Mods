package br.com.celestialdragon.mixin.world;

import br.com.celestialdragon.entity.CelestialDragonEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shift is boost on this mount. Dismount is explicitly requested by a double Ctrl tap. */
@Mixin(Player.class)
public abstract class DragonRidingMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void celestialDragon$keepRider(CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player)(Object)this;
        if (player.getVehicle() instanceof CelestialDragonEntity dragon
                && player.getUUID().equals(dragon.getOwnerId())) callback.setReturnValue(false);
    }
}
