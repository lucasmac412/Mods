package br.com.celestialdragon.mixin.client;

import br.com.celestialdragon.client.DragonControls;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class DragonCameraMixin {
    @Shadow private net.minecraft.world.entity.Entity entity;
    @Shadow protected abstract void move(float forward, float up, float left);

    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
    private float celestialDragon$chaseDistance(float original) {
        return entity == Minecraft.getInstance().player && DragonControls.isControllingDragon(Minecraft.getInstance()) ? (DragonControls.isWideCamera() ? 38.0F : 26.0F) : original;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"))
    private void celestialDragon$raiseBeforeCollision(float distance, CallbackInfoReturnable<Float> callback) {
        if (entity == Minecraft.getInstance().player && DragonControls.isControllingDragon(Minecraft.getInstance())) move(0, DragonControls.isWideCamera() ? 12.0F : 3.5F, 0);
    }
}
