package br.com.luma;

import br.com.luma.entity.CompanionEntity;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LumaMod implements ModInitializer {
    public static final String ID = "luma";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final EntityType<CompanionEntity> COMPANION = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("companion"),
            EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("companion"))));

    // Immutable maps on the overworld survive player logout, death and world restart.
    static final AttachmentType<Map<String, String>> BINDINGS = AttachmentRegistry.create(
            id("owners"), builder -> builder.initializer(Map::of)
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
    static final AttachmentType<Map<String, String>> LOCATIONS = AttachmentRegistry.create(
            id("locations"), builder -> builder.initializer(Map::of)
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
    private static final Map<MinecraftServer, CompanionService> SERVICES = new WeakHashMap<>();

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    static CompanionService service(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, CompanionService::new);
    }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(COMPANION, CompanionEntity.createAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                CompanionService.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(LumaMod::service);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Disconnect and unload callbacks can still run while the server is stopping.
            CompanionService service = SERVICES.get(server);
            if (service != null) service.close();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVICES::remove);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CompanionService service = SERVICES.get(server);
            if (service != null) service.onDisconnect(handler.player);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof CompanionEntity companion) {
                service(level.getServer()).onLoad(companion);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof CompanionEntity companion && entity.level().getServer() != null) {
                service(entity.level().getServer()).onDeath(companion);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) service(server).tick();
        });
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, type) -> {
            String text = message.signedContent().strip();
            if (text.matches("(?is)^luma(?:\\s|[,!:]).*")) {
                String content = text.replaceFirst("(?i)^luma[\\s,!:]*", "");
                service(sender.level().getServer()).talk(sender, content);
            }
        });
        LOGGER.info("Luma carregada. Use /luma ajuda para começar.");
    }
}
