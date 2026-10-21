package br.com.celestialdragon.mixin.client;

import br.com.celestialdragon.client.DragonControls;
import br.com.celestialdragon.content.DragonSpawnEggItem;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
        // The large dragon's core can intercept the click before vanilla ever uses the egg.
        // Keep vanilla block reach, packets and spawning; bypass only this entity obstruction.
        if (minecraft.player != null && minecraft.player.isCreative()
                && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof CelestialDragonEntity) {
            var main = minecraft.player.getMainHandItem();
            var held = main.isEmpty() ? minecraft.player.getOffhandItem() : main;
            if (held.getItem() instanceof SpawnEggItem && !(held.getItem() instanceof DragonSpawnEggItem)) {
                HitResult block = minecraft.player.pick(minecraft.player.blockInteractionRange(), 1.0F, false);
                if (block.getType() == HitResult.Type.BLOCK) minecraft.hitResult = block;
            }
        }
        if (DragonControls.shouldUseBeamControls(minecraft)) {
            DragonControls.requestAttack(minecraft, true);
            callback.cancel();
        }
    }
}
