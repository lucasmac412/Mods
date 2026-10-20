package br.com.luma.voiceinput;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.text2speech.Narrator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client-only PTT; only a final transcript crosses the existing Luma command channel. */
public final class LumaVoiceClient implements ClientModInitializer {
    private VoiceEngine microphone;
    private VoiceSettings settings;
    private KeyMapping talkKey;
    private KeyMapping speakKey;
    private boolean wasDown;
    private Object currentWorld;
    private long request;
    private Narrator narrator;
    private boolean warnedVoice;

    @Override public void onInitializeClient() {
        var config = FabricLoader.getInstance().getConfigDir();
        microphone = new VoiceEngine(new VoskBackend(config.resolve("luma-voice/model")));
        settings = new VoiceSettings(config.resolve("luma-voice.json"));
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("luma_voice", "voice"));
        talkKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.luma_voice.talk",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
        speakKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.luma_voice.speak",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> cancel());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (!overlay && text.startsWith("[Luma] ")) speak(text.substring(7));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            cancel();
            microphone.close();
            // NarratorWindows initializes COM; destroy on its creating client thread.
            if (narrator != null) { narrator.destroy(); narrator = null; }
        });
    }

    private void tick(Minecraft client) {
        boolean down = talkKey.isDown();
        boolean eligible = client.player != null && client.player.isAlive() && client.level != null && client.isWindowActive()
                && client.gui.screen() == null && client.gui.overlay() == null;
        if (currentWorld != client.level) {
            cancel();
            currentWorld = client.level;
            wasDown = down; // Crossing a world while holding V never opens the next microphone.
        }
        while (speakKey.consumeClick()) {
            if (eligible) {
                boolean saved = settings.toggle();
                if (!settings.speakReplies()) clearSpeech();
                tell(client, "Leitura das respostas " + (settings.speakReplies() ? "ligada." : "desligada.")
                        + (saved ? "" : " A preferência não pôde ser salva; vale nesta sessão."));
            }
        }
        if (!eligible) {
            if (microphone.busy()) cancel();
            wasDown = down;
            return;
        }
        if (down && !wasDown) {
            if (microphone.busy()) {
                tell(client, "Ainda estou preparando ou reconhecendo a fala anterior. Aguarde e tente novamente.");
                wasDown = down;
                return;
            }
            clearSpeech(); // Avoid speaking over the player's recording without changing global narration.
            Object world = client.level;
            Object connection = client.player.connection;
            long token = ++request;
            boolean started = microphone.start(outcome -> client.execute(() -> {
                if (token != request || client.player == null || !client.player.isAlive() || client.level != world
                        || client.player.connection != connection || !client.isWindowActive()
                        || client.gui.screen() != null || client.gui.overlay() != null) return;
                if (!outcome.error().isBlank()) tell(client, outcome.error());
                else {
                    tell(client, "Você disse: " + outcome.text());
                    client.player.connection.sendCommand(VoiceText.command(outcome.text()));
                }
            }), () -> client.execute(() -> {
                if (token == request && client.player != null && client.player.isAlive() && client.level == world
                        && client.player.connection == connection && talkKey.isDown()
                        && client.isWindowActive() && client.gui.screen() == null && client.gui.overlay() == null)
                    tell(client, "Pode falar agora! Solte a tecla para enviar (até 15 s).");
            }));
            tell(client, started ? "Preparando microfone. Continue segurando a tecla e aguarde o aviso “Pode falar agora”."
                    : "Ainda estou preparando ou reconhecendo a fala anterior. Aguarde e tente novamente.");
        } else if (!down && wasDown) microphone.release();
        wasDown = down;
    }

    private void cancel() {
        request++;
        microphone.cancel();
        clearSpeech();
    }
    private void clearSpeech() {
        if (narrator != null) {
            try { narrator.clear(); } catch (RuntimeException | LinkageError ignored) { /* unavailable TTS */ }
        }
    }
    private void speak(String text) {
        Minecraft client = Minecraft.getInstance();
        if (!settings.speakReplies() || client.player == null || microphone.busy()) return;
        try {
            if (narrator == null) narrator = Narrator.getNarrator();
            if (!narrator.active()) { voiceUnavailable(client); return; }
            String clean = VoiceText.clean(text);
            // Replace earlier speech instead of building an unbounded native narration queue.
            if (!clean.isBlank()) narrator.say(clean, true, 1.0f);
        } catch (RuntimeException | LinkageError ignored) { voiceUnavailable(client); }
    }
    private void voiceUnavailable(Minecraft client) {
        if (!warnedVoice) {
            warnedVoice = true;
            tell(client, "A voz do sistema não está disponível. Texto e microfone continuam independentes; confira o guia ou desligue a leitura com K.");
        }
    }
    private static void tell(Minecraft client, String text) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal("[Voz da Luma] " + text));
    }
}
