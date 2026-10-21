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
        super(context, new DragonModel(context.bakeLayer(MODEL_LAYER)), 2.2F);
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
        state.spiritTicks = entity.spiritTicks();
        state.dormant = entity.isDormant();
        state.boosting = entity.isBoosting();
        state.beamStart = entity.mouthPosition().subtract(entity.position());
        state.beamEnd = entity.beamEnd().subtract(entity.position());
        // Vanilla interpolates this speed between ticks, keeping the travelling wave smooth
        // when flight starts, stops or receives a network movement update.
        state.movementAmount = entity.flightEffort(partialTick);
        state.flightPhase = entity.flightPhase(partialTick);
        state.sleepAmount = entity.sleepPose(partialTick);
    }

    @Override public void submit(DragonRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        if (state.beamTicks > 4) {
            var start = state.beamStart; var end = state.beamEnd; float age = state.ageInTicks;
            boolean active = CelestialDragonEntity.BEAM_DURATION - state.beamTicks >= CelestialDragonEntity.BEAM_CHARGE_TICKS;
            collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.lightning(),
                    (matrix, vertices) -> DragonEnergyGeometry.beam(matrix, vertices, start, end, age, active));
        }
        if (state.boosting) {
            float age = state.ageInTicks;
            collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.lightning(),
                    (matrix, vertices) -> DragonEnergyGeometry.electricity(matrix, vertices, new net.minecraft.world.phys.Vec3(0, 3, 0), 3.6, age));
        }
    }

    @Override
    public Identifier getTextureLocation(DragonRenderState state) {
        return TEXTURE;
    }

    @Override
    protected AABB getBoundingBoxForCulling(CelestialDragonEntity entity) {
        // The articulated tail extends well beyond the collision box.
        return entity.getBoundingBox().inflate(32.0, 10.0, 32.0);
    }
}
