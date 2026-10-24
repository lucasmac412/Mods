package br.com.moonfist.mixin;

import br.com.moonfist.client.FighterRenderInfo;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public class HumanoidInfoMixin implements FighterRenderInfo {
    @Unique private int moonfist$id=-1;
    @Unique private boolean moonfist$active;
    @Unique private float moonfist$partial;
    public int moonfist$id(){return moonfist$id;}
    public boolean moonfist$active(){return moonfist$active;}
    public float moonfist$partial(){return moonfist$partial;}
    public void moonfist$set(int id,boolean active,float partial){moonfist$id=id;moonfist$active=active;moonfist$partial=partial;}
}
