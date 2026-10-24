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
        float[] motion=CombatView.pose(player.getId(),partial);
        float[] rest=br.com.moonfist.animation.MartialMotion.stance(s.stance(),0);
        for(var arm:HumanoidArm.values()) {
            boolean left=arm==HumanoidArm.LEFT;float sign=left?-1:1;int j=left?3:0;
            float extension=Math.max(-.5f,Math.min(1.1f,rest[j]-motion[j]));
            float yaw=motion[j+1]-rest[j+1],roll=motion[j+2]-rest[j+2];
            pose.pushPose();
            pose.translate(sign*(-.06+extension*.09)+yaw*.09,.1+extension*.08+motion[12]*.08,-extension*.48);
            pose.mulPose(Axis.YP.rotation(yaw*.38f));
            pose.mulPose(Axis.ZP.rotation(roll*.5f-sign*extension*.12f));
            pose.mulPose(Axis.XP.rotation((motion[j]-rest[j])*.32f));
            moonfist$arm(pose,collector,light,0,0,arm);pose.popPose();
        }
        ci.cancel();
    }
}
