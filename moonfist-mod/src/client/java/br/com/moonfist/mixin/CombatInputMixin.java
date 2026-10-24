package br.com.moonfist.mixin;

import br.com.moonfist.client.CombatControls;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class CombatInputMixin {
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void moonfist$light(CallbackInfoReturnable<Boolean> ci) {
        var c=(Minecraft)(Object)this;if(CombatControls.active(c)){CombatControls.attack(c,false);ci.setReturnValue(false);}
    }
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void moonfist$noMining(boolean attack,CallbackInfo ci) {if(CombatControls.active((Minecraft)(Object)this))ci.cancel();}
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void moonfist$heavy(CallbackInfo ci) {
        var c=(Minecraft)(Object)this;if(CombatControls.active(c)){CombatControls.attack(c,true);ci.cancel();}
    }
}
