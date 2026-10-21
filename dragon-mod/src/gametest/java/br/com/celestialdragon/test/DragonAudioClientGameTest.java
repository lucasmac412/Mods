package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;

/** Real server events must resolve into audible-distance sounds in the loaded OpenAL engine. */
public final class DragonAudioClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Map<String, Float> heard = new ConcurrentHashMap<>();
        SoundEventListener listener = (sound, event, range) -> {
            if (sound.getIdentifier().getNamespace().equals(CelestialDragonMod.ID)) {
                if (event.getSound(net.minecraft.util.RandomSource.create()).getAttenuationDistance() < 48)
                    throw new AssertionError("Dragon attenuation must cover the chase camera");
                heard.put(sound.getIdentifier().getPath(), range);
            }
        };
        context.runOnClient(client -> {
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
            client.options.getSoundSourceOptionInstance(SoundSource.HOSTILE).set(1.0);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
            client.getSoundManager().addListener(listener);
        });
        try (var world=context.worldBuilder().adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            var server=world.getServer();
            UUID id=server.computeOnServer(ignored -> {
                var level=world.getConnection().getServerLevel();
                var player=world.getConnection().getServerPlayer();
                player.getAbilities().flying=true; player.onUpdateAbilities();
                player.teleportTo(level,0,140,-26,Set.of(),0,0,false);
                var dragon=CelestialDragonMod.DRAGON.create(level,EntitySpawnReason.COMMAND);
                dragon.tame(player.getUUID()); dragon.setNoAi(true); dragon.snapTo(0,140,0,0,0);
                level.addFreshEntity(dragon); return dragon.getUUID();
            });
            world.getConnection().waitForClientboundEntityUpdates(CelestialDragonMod.DRAGON);
            context.waitFor(client -> heard.containsKey("dragon.roar"));
            server.runOnServer(ignored -> ((CelestialDragonEntity)world.getConnection().getServerLevel().getEntity(id)).tryBite());
            context.waitFor(client -> heard.containsKey("dragon.bite"));
            server.waitFor(ignored -> ((CelestialDragonEntity)world.getConnection().getServerLevel().getEntity(id)).biteTicks()==0);
            server.runOnServer(ignored -> ((CelestialDragonEntity)world.getConnection().getServerLevel().getEntity(id)).tryBeam());
            context.waitFor(client -> heard.containsKey("dragon.beam"));
            server.runOnServer(ignored -> {
                var dragon=(CelestialDragonEntity)world.getConnection().getServerLevel().getEntity(id);
                dragon.hurtServer(world.getConnection().getServerLevel(),dragon.damageSources().generic(),1);
            });
            context.waitFor(client -> heard.containsKey("dragon.hurt"));
            context.waitTicks(22);
            server.runOnServer(ignored -> {
                var dragon=(CelestialDragonEntity)world.getConnection().getServerLevel().getEntity(id);
                dragon.hurtServer(world.getConnection().getServerLevel(),dragon.damageSources().generic(),10000);
            });
            context.waitFor(client -> heard.containsKey("dragon.death"));
            Path report=Path.of("../../reports/client-audio/sound-events.txt").toAbsolutePath().normalize();
            try { Files.createDirectories(report.getParent()); Files.writeString(report,"OpenAL engine received server-driven dragon events at 26 blocks: "+heard+"\n"); }
            catch(java.io.IOException e) { throw new RuntimeException(e); }
            System.out.println("Dragon audio regression passed at chase-camera distance: "+heard);
        } finally { context.runOnClient(client -> client.getSoundManager().removeListener(listener)); }
    }
}
