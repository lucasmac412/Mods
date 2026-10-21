package br.com.celestialdragon.mixin.client;

import br.com.celestialdragon.client.DragonControls;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class DragonInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void celestialDragon$bite(CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (DragonControls.isControllingDragon(minecraft)) {
            DragonControls.requestAttack(minecraft, false);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void celestialDragon$stopBlockBreaking(boolean attacking, CallbackInfo callback) {
        if (DragonControls.isControllingDragon((Minecraft) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void celestialDragon$beam(CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (DragonControls.shouldUseBeamControls(minecraft)) {
            DragonControls.requestAttack(minecraft, true);
            callback.cancel();
        }
    }
}
