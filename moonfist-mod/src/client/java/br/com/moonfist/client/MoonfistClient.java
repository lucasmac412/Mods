package br.com.moonfist.client;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.network.CombatSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;

public final class MoonfistClient implements ClientModInitializer {
    @Override @SuppressWarnings({"unchecked","rawtypes"}) public void onInitializeClient() {
        CombatControls.register();
        ClientPlayNetworking.registerGlobalReceiver(CombatSnapshot.TYPE,(s,c)->c.client().execute(()->CombatView.receive(s)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->CombatView.clear());
        HudElementRegistry.addLast(MoonfistMod.id("combat_hud"),CombatHud::draw);
        EntityRendererRegistry.register(MoonfistMod.DISCIPLE,DiscipleRenderer::new);
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type,renderer,helper,context)->{
            if(renderer instanceof AvatarRenderer<?> avatar)helper.register(new GauntletLayer<>(avatar));
            else if(renderer instanceof DiscipleRenderer disciple)helper.register(new GauntletLayer<>(disciple));
        });
    }
}
