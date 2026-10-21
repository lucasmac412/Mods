package br.com.celestialdragon.mixin.client;

import br.com.celestialdragon.client.DragonControls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class DragonRiderPoseMixin {
    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void celestialDragon$boostPose(CallbackInfoReturnable<Boolean> callback) {
        if (DragonControls.isControllingDragon(Minecraft.getInstance())) callback.setReturnValue(false);
    }
}
