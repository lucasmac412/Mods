package br.com.moonfist.mixin;

import br.com.moonfist.client.CombatEffects;
import br.com.moonfist.client.FighterRenderInfo;
import br.com.moonfist.client.CombatView;
import br.com.moonfist.client.CombatPose;
import br.com.moonfist.combat.Move;
import com.mojang.math.Axis;
import br.com.moonfist.combat.CombatService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingRenderMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void moonfist$info(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo ci) {
        if(state instanceof FighterRenderInfo info)info.moonfist$set(entity.getId(),CombatService.equipped(entity),partial);
    }
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"))
    private void moonfist$energy(LivingEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci) {
        CombatEffects.submit(state,pose,collector);
    }
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void moonfist$roll(LivingEntityRenderState state,PoseStack pose,float bodyRot,float scale,CallbackInfo ci) {
        if(!(state instanceof FighterRenderInfo info)||!info.moonfist$active())return;
        var combat=CombatView.get(info.moonfist$id());if(combat==null)return;
        if(Move.byId(combat.move())==Move.SPIN_KICK){
            pose.mulPose(Axis.YP.rotationDegrees(-360*CombatPose.smooth(CombatView.age(info.moonfist$id(),info.moonfist$partial())/Move.SPIN_KICK.duration)));return;
        }
        if(Move.byId(combat.move())!=Move.DODGE)return;
        float phase=CombatPose.smooth(CombatView.age(info.moonfist$id(),info.moonfist$partial())/Move.DODGE.duration);
        pose.translate(0,.75,0);pose.mulPose(Axis.XP.rotationDegrees(-360*phase));pose.translate(0,-.75,0);
    }
}
