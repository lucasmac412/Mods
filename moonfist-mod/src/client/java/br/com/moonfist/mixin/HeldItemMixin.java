package br.com.moonfist.mixin;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.client.FighterRenderInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class HeldItemMixin {
    @Inject(method="submitArmWithItem",at=@At("HEAD"),cancellable=true)
    private void moonfist$glove(ArmedEntityRenderState state,ItemStackRenderState itemState,ItemStack item,HumanoidArm arm,
            PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo ci) {
        if(item.is(MoonfistMod.FISTS)&&state instanceof FighterRenderInfo info&&info.moonfist$active())ci.cancel();
    }
}
