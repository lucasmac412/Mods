package br.com.moonfist.mixin;

import br.com.moonfist.client.CombatPose;
import br.com.moonfist.client.CombatView;
import br.com.moonfist.client.FighterRenderInfo;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"))
    private void moonfist$pose(HumanoidRenderState state,CallbackInfo ci) {
        var info=(FighterRenderInfo)state;if(!info.moonfist$active())return;
        CombatPose.apply((HumanoidModel<?>)(Object)this,CombatView.pose(info.moonfist$id(),info.moonfist$partial()));
    }
}
