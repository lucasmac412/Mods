package br.com.moonfist.mixin;

import br.com.moonfist.client.CombatControls;
import br.com.moonfist.client.EnergyGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class FirstPersonGloveMixin {
    @Inject(method="renderHand",at=@At("TAIL"))
    private void moonfist$knuckles(PoseStack pose,SubmitNodeCollector collector,int light,Identifier texture,ModelPart arm,boolean sleeve,CallbackInfo ci) {
        if(!CombatControls.active(Minecraft.getInstance()))return;
        pose.pushPose();arm.translateAndRotate(pose);boolean left=arm.x>0;
        collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(p,v)->EnergyGeometry.glove(p,v,left));pose.popPose();
    }
}
