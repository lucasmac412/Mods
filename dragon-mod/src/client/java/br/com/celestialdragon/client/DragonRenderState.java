package br.com.celestialdragon.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class DragonRenderState extends LivingEntityRenderState {
    public boolean tamed;
    public boolean ridden;
    public float biteTicks;
    public float beamTicks;
    public float movementAmount;
    public float spiritTicks;
    public boolean dormant, boosting;
    public net.minecraft.world.phys.Vec3 beamStart = net.minecraft.world.phys.Vec3.ZERO;
    public net.minecraft.world.phys.Vec3 beamEnd = net.minecraft.world.phys.Vec3.ZERO;
}
