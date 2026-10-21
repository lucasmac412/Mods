package br.com.celestialdragon.mixin.client;

import br.com.celestialdragon.client.DragonControls;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The distant spirit camera must not hide the whole sphere inside render-distance fog. */
@Mixin(FogRenderer.class)
public abstract class DragonEnergyFogMixin {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void celestialDragon$spiritVisibility(Camera camera,int distance,DeltaTracker tracker,float partial,
            ClientLevel level,CallbackInfoReturnable<FogData> callback) {
        if(camera.entity()==Minecraft.getInstance().player && DragonControls.isWideCamera()
                && DragonControls.isControllingDragon(Minecraft.getInstance())) {
            FogData fog=callback.getReturnValue();
            fog.renderDistanceStart=Math.max(fog.renderDistanceStart,160);
            fog.renderDistanceEnd=Math.max(fog.renderDistanceEnd,220);
            // Environmental fog (water, blindness, etc.) remains intact. No chunks are loaded.
        }
    }
}
