package br.com.luma.client;

import br.com.luma.LumaMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

public final class LumaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(CompanionRenderer.MODEL_LAYER,
                CompanionRenderer::createBodyLayer);
        EntityRendererRegistry.register(LumaMod.COMPANION, CompanionRenderer::new);
    }
}
