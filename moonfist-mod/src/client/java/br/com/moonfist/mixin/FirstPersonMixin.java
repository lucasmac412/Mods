package br.com.moonfist.mixin;

import br.com.moonfist.client.CombatControls;
import br.com.moonfist.client.CombatPose;
import br.com.moonfist.client.CombatView;
import br.com.moonfist.combat.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonMixin {
    @Invoker("renderPlayerArm") protected abstract void moonfist$arm(PoseStack pose,SubmitNodeCollector collector,int light,float equip,float swing,HumanoidArm arm);
    @Inject(method="submitHandsWithItems",at=@At("HEAD"),cancellable=true)
    private void moonfist$hands(float partial,PoseStack pose,SubmitNodeCollector collector,LocalPlayer player,int light,CallbackInfo ci) {
        if(!CombatControls.active(Minecraft.getInstance()))return;
        var s=CombatView.get(player.getId());if(s==null)s=CombatView.idle(player.getId());
        Move move=Move.byId(s.move());float a=CombatView.age(player.getId(),partial),strike=CombatPose.strike(move,a);
        for(var arm:HumanoidArm.values()) {
            boolean left=arm==HumanoidArm.LEFT;float sign=left?-1:1;
            boolean punch=move==Move.JAB||move==Move.PALM?left:!left;
            float push=punch?strike:0;
            pose.pushPose();pose.translate(sign*(-.06+push*.10),s.guard()?.18:.1,-push*.42);
            pose.mulPose(Axis.ZP.rotationDegrees(sign*(s.guard()?18:push*-15)));
            pose.mulPose(Axis.XP.rotationDegrees(-push*18));
            moonfist$arm(pose,collector,light,0,0,arm);pose.popPose();
        }
        ci.cancel();
    }
}
