package br.com.moonfist.client;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.entity.LunarDisciple;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public final class DiscipleRenderer extends HumanoidMobRenderer<LunarDisciple,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
    public DiscipleRenderer(EntityRendererProvider.Context context){super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),.5f);}
    @Override public HumanoidRenderState createRenderState(){return new HumanoidRenderState();}
    @Override public Identifier getTextureLocation(HumanoidRenderState state){return MoonfistMod.id("textures/entity/lunar_disciple.png");}
}
