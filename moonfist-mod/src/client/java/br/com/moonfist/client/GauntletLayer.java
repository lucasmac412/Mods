package br.com.moonfist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class GauntletLayer<S extends HumanoidRenderState,M extends HumanoidModel<S>> extends RenderLayer<S,M> {
    public GauntletLayer(RenderLayerParent<S,M> parent){super(parent);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,S state,float yRot,float xRot) {
        if(!((FighterRenderInfo)state).moonfist$active())return;
        M model=getParentModel();model.setupAnim(state);
        pose.pushPose();model.rightArm.translateAndRotate(pose);
        collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(p,v)->EnergyGeometry.glove(p,v,false));pose.popPose();
        pose.pushPose();model.leftArm.translateAndRotate(pose);
        collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(p,v)->EnergyGeometry.glove(p,v,true));pose.popPose();
    }
}
