package br.com.celestialdragon.client;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public final class DragonRenderer extends MobRenderer<CelestialDragonEntity, DragonRenderState, DragonModel> {
    public static final ModelLayerLocation MODEL_LAYER = DragonModel.MODEL_LAYER;
    private static final Identifier TEXTURE = CelestialDragonMod.id("textures/entity/celestial_dragon.png");

    public DragonRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonModel(context.bakeLayer(MODEL_LAYER)), 1.6F);
    }

    public static LayerDefinition createBodyLayer() {
        return DragonModel.createBodyLayer();
    }

    @Override
    public DragonRenderState createRenderState() {
        return new DragonRenderState();
    }

    @Override
    public void extractRenderState(CelestialDragonEntity entity, DragonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tamed = entity.isTamed();
        state.ridden = entity.isVehicle();
        state.biteTicks = Math.max(0, entity.biteTicks() - partialTick);
        state.beamTicks = Math.max(0, entity.beamTicks() - partialTick);
    }

    @Override
    public Identifier getTextureLocation(DragonRenderState state) {
        return TEXTURE;
    }

    @Override
    protected AABB getBoundingBoxForCulling(CelestialDragonEntity entity) {
        // The articulated tail extends well beyond the collision box.
        return entity.getBoundingBox().inflate(16.0, 5.0, 16.0);
    }
}
