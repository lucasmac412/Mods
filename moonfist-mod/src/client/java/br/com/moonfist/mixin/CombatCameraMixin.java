package br.com.moonfist.mixin;

import br.com.moonfist.combat.CombatService;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class CombatCameraMixin {
    @Shadow private Entity entity;
    @Shadow public abstract Vec3 position();
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void move(float forward,float up,float left);
    @ModifyArg(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;getMaxZoom(F)F"),index=0)
    private float moonfist$distance(float distance) {
        var player=Minecraft.getInstance().player;return entity==player&&player!=null&&CombatService.equipped(player)?5.3f:distance;
    }
    @Inject(method="alignWithEntity",at=@At("TAIL"))
    private void moonfist$shoulder(float partial,CallbackInfo ci) {
        var c=Minecraft.getInstance();
        if(entity!=c.player||c.player==null||!CombatService.equipped(c.player)||c.options.getCameraType()!=net.minecraft.client.CameraType.THIRD_PERSON_BACK)return;
        Vec3 from=position();move(0,1f,-1.2f);Vec3 to=position();
        var hit=c.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
        if(hit.getType()!=HitResult.Type.MISS)setPosition(hit.getLocation().add(from.subtract(to).normalize().scale(.08)));
    }
}
