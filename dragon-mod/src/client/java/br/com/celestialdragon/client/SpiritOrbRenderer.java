package br.com.celestialdragon.client;

import br.com.celestialdragon.entity.CelestialOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpiritOrbRenderer extends EntityRenderer<CelestialOrbEntity, SpiritOrbRenderer.State> {
    public SpiritOrbRenderer(EntityRendererProvider.Context context) { super(context); }
    public static final class State extends EntityRenderState { double radius; }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(CelestialOrbEntity orb, State state, float partial) {
        super.extractRenderState(orb, state, partial); state.radius = orb.radius(partial);
    }
    @Override protected AABB getBoundingBoxForCulling(CelestialOrbEntity entity) { return new AABB(entity.position().subtract(34,34,34), entity.position().add(34,34,34)); }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        double radius = state.radius; float age = state.ageInTicks;
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (matrix, vertices) -> {
            DragonEnergyGeometry.sphere(matrix, vertices, Vec3.ZERO, radius * 1.13, 0x2258BEFF);
            DragonEnergyGeometry.sphere(matrix, vertices, Vec3.ZERO, radius, 0x8849DFFF);
            DragonEnergyGeometry.sphere(matrix, vertices, Vec3.ZERO, radius * 0.90, 0xDDA5F6FF);
            DragonEnergyGeometry.sphere(matrix, vertices, Vec3.ZERO, radius * 0.74, 0xF0EFFFFF);
            DragonEnergyGeometry.electricity(matrix, vertices, Vec3.ZERO, radius * 1.12, age);
        });
    }
}
