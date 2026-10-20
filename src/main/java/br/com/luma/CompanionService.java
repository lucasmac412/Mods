package br.com.luma;

import br.com.luma.ai.Action;
import br.com.luma.ai.Intent;
import br.com.luma.ai.IntentParser;
import br.com.luma.ai.NativeBrain;
import br.com.luma.entity.CompanionEntity;
import br.com.luma.voice.VoiceBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** All Minecraft state is touched only on the server thread. */
final class CompanionService implements AutoCloseable {
    private final MinecraftServer server;
    private final Map<UUID, NativeBrain> brains = new HashMap<>();
    private final NativeBrain.Engine brainEngine = new NativeBrain.Engine();
    private final Map<UUID, Long> generations = new HashMap<>();
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    private VoiceBridge voice;
    private UUID voiceOwner;
    private long voiceSession;
    private volatile boolean closed;

    CompanionService(MinecraftServer server) { this.server = server; }

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("luma")
                .executes(ctx -> run(ctx.getSource(), service -> service.help(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("ajuda").executes(ctx -> run(ctx.getSource(), s -> s.help(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("criar").executes(ctx -> run(ctx.getSource(), s -> s.create(ctx.getSource().getPlayerOrException()))));
        Map<String, Action> actions = Map.of(
                "seguir", Action.FOLLOW, "ficar", Action.STAY, "autonomo", Action.AUTO,
                "proteger", Action.DEFEND, "minerar", Action.MINE, "construir", Action.BUILD,
                "parar", Action.STOP, "status", Action.STATUS);
        actions.forEach((name, action) -> root.then(Commands.literal(name).executes(ctx ->
                run(ctx.getSource(), s -> s.order(ctx.getSource().getPlayerOrException(), action)))));
        root.then(Commands.literal("falar").then(Commands.argument("mensagem", StringArgumentType.greedyString())
                .executes(ctx -> run(ctx.getSource(), s -> s.talk(ctx.getSource().getPlayerOrException(),
                        StringArgumentType.getString(ctx, "mensagem"))))));
        root.then(Commands.literal("entregar").executes(ctx -> run(ctx.getSource(), s -> s.withCompanion(
                ctx.getSource().getPlayerOrException(), entity -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!s.canExchange(player, entity)) return;
                    if (entity.giveItem(player)) s.speak(entity, "Recebi! Use /luma status para ver os materiais.");
                }))));
        root.then(Commands.literal("devolver").executes(ctx -> run(ctx.getSource(), s -> s.withCompanion(
                ctx.getSource().getPlayerOrException(), entity -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!s.canExchange(player, entity)) return;
                    s.bump(player.getUUID());
                    entity.returnItems(player);
                }))));
        root.then(Commands.literal("chamar").executes(ctx -> run(ctx.getSource(), s -> s.recall(ctx.getSource().getPlayerOrException()))));
        root.then(Commands.literal("localizar").executes(ctx -> run(ctx.getSource(), s -> s.locate(ctx.getSource().getPlayerOrException()))));
        root.then(Commands.literal("remover").executes(ctx -> run(ctx.getSource(), s -> s.remove(ctx.getSource().getPlayerOrException()))));
        root.then(Commands.literal("ia")
                .then(Commands.literal("ligar").executes(ctx -> run(ctx.getSource(), s -> s.enableBrain(ctx.getSource().getPlayerOrException(), true))))
                .then(Commands.literal("desligar").executes(ctx -> run(ctx.getSource(), s -> s.enableBrain(ctx.getSource().getPlayerOrException(), false)))));
        var voice = Commands.literal("voz").executes(ctx -> run(ctx.getSource(), s -> s.startVoice(ctx.getSource().getPlayerOrException())));
        for (String stop : new String[]{"parar", "desligar"}) {
            voice.then(Commands.literal(stop).executes(ctx -> run(ctx.getSource(), s -> s.stopVoice(ctx.getSource().getPlayerOrException()))));
        }
        root.then(voice);
        dispatcher.register(root);
    }

    @FunctionalInterface private interface Command { void run(CompanionService service) throws CommandSyntaxException; }
    private static int run(CommandSourceStack source, Command command) throws CommandSyntaxException {
        command.run(LumaMod.service(source.getServer())); return 1;
    }
    @FunctionalInterface private interface CompanionCommand { void run(CompanionEntity entity) throws CommandSyntaxException; }
    private void withCompanion(ServerPlayer player, CompanionCommand command) throws CommandSyntaxException {
        if (closed) return;
        CompanionEntity entity = find(player.getUUID());
        if (entity == null) { missing(player); return; }
        command.run(entity);
    }

    private boolean canExchange(ServerPlayer player, CompanionEntity entity) {
        if (!player.isAlive()) { tell(player, "Você precisa estar vivo para trocar os itens."); return false; }
        if (entity.level() != player.level() || entity.distanceToSqr(player) > 36) {
            tell(player, "Chegue a até 6 blocos de mim na mesma dimensão."); return false;
        }
        return true;
    }

    private void help(ServerPlayer player) {
        tell(player, "Olá! Crie sua companheira com /luma criar. Depois use seguir, ficar, autonomo, proteger, minerar, construir ou parar.");
        tell(player, "Segure um item e use /luma entregar. /luma devolver recupera itens. /luma falar <mensagem> conversa; /luma voz abre o painel de voz no seu computador.");
        tell(player, "Outros: status, chamar, localizar, remover e ia ligar/desligar. Você também pode escrever “Luma, me siga” no chat.");
    }

    private Map<String, String> bindings() { return server.overworld().getAttachedOrCreate(LumaMod.BINDINGS); }
    private String boundId(UUID owner) { return bindings().get(owner.toString()); }
    private void bind(UUID owner, UUID entity) {
        Map<String, String> next = new HashMap<>(bindings());
        if (entity == null) next.remove(owner.toString()); else next.put(owner.toString(), entity.toString());
        server.overworld().setAttached(LumaMod.BINDINGS, Map.copyOf(next));
    }
    void onLoad(CompanionEntity entity) {
        entity.setSpeech(text -> speak(entity, text));
        if (closed || !entity.isAlive()) return;
        UUID owner = entity.getOwnerId();
        if (owner == null) return;
        // Recover a previously saved entity before allowing another one to be created.
        if (boundId(owner) == null) bind(owner, entity.getUUID());
        rememberLocation(entity);
    }
    private CompanionEntity find(UUID owner) {
        String id = boundId(owner);
        for (ServerLevel level : server.getAllLevels()) {
            if (id != null) {
                try {
                    Entity entity = level.getEntity(UUID.fromString(id));
                    if (entity instanceof CompanionEntity companion && owner.equals(companion.getOwnerId()) && companion.isAlive()) return companion;
                } catch (IllegalArgumentException ignored) { /* corrupted metadata is not proof of death */ }
            } else {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof CompanionEntity companion && owner.equals(companion.getOwnerId()) && companion.isAlive()) {
                        bind(owner, companion.getUUID()); return companion;
                    }
                }
            }
        }
        return null;
    }
    private void missing(ServerPlayer player) {
        tell(player, boundId(player.getUUID()) == null ? "Use /luma criar primeiro." :
                "Minha região pode estar descarregada. Use /luma localizar e aproxime-se; não vou criar uma cópia que duplique itens.");
    }

    private void create(ServerPlayer player) {
        if (closed || !player.isAlive()) return;
        if (find(player.getUUID()) != null || boundId(player.getUUID()) != null) {
            tell(player, "Você já tem uma Luma. Use /luma chamar ou /luma localizar."); return;
        }
        ServerLevel level = (ServerLevel) player.level();
        CompanionEntity entity = new CompanionEntity(LumaMod.COMPANION, level);
        if (!placeNear(entity, player)) { tell(player, "Preciso de um chão seguro e espaço perto de você."); return; }
        entity.setOwner(player.getUUID());
        entity.setCustomName(Component.literal("Luma"));
        entity.setCustomNameVisible(true);
        entity.setPersistenceRequired();
        entity.setSpeech(text -> speak(entity, text));
        if (level.addFreshEntity(entity)) {
            bind(player.getUUID(), entity.getUUID());
            speak(entity, "Oi! Sou a Luma. Use /luma entregar para me dar ferramentas e blocos. Vamos nessa?");
            rememberLocation(entity);
        } else tell(player, "Não consegui aparecer aqui. Tente novamente em uma área aberta.");
    }

    private boolean placeNear(CompanionEntity entity, ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        for (int radius = 2; radius <= 4; radius++) for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) for (int dy = 0; dy <= 1; dy++) {
                if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                BlockPos pos = player.blockPosition().offset(x, dy, z);
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                        || !level.getFluidState(pos.below()).isEmpty()) continue;
                if (dangerous(level.getBlockState(pos)) || dangerous(level.getBlockState(pos.above()))
                        || dangerous(level.getBlockState(pos.below()))) continue;
                if (!level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())) continue;
                entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (level.noCollision(entity)) return true;
            }
        }
        return false;
    }
    private static boolean dangerous(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
    }
    private void recall(ServerPlayer player) {
        if (closed || !player.isAlive()) return;
        CompanionEntity entity = find(player.getUUID());
        if (entity == null) { missing(player); return; }
        if (entity.level() != player.level()) { tell(player, "Volte à minha dimensão para me chamar. Use /luma localizar."); return; }
        if (entity.isPassenger() || entity.isVehicle()) {
            tell(player, "Tire-me do veículo e remova passageiros antes de me chamar."); return;
        }
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        entity.getNavigation().stop();
        if (!placeNear(entity, player)) { entity.setPos(x, y, z); tell(player, "Não achei espaço seguro perto de você."); return; }
        entity.teleportTo(entity.getX(), entity.getY(), entity.getZ());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.command(Action.FOLLOW); bump(player.getUUID());
        speak(entity, "Estou aqui!"); rememberLocation(entity);
    }
    private void locate(ServerPlayer player) {
        CompanionEntity entity = find(player.getUUID());
        if (entity != null) rememberLocation(entity);
        String location = server.overworld().getAttachedOrCreate(LumaMod.LOCATIONS).get(player.getUUID().toString());
        tell(player, location == null ? "Ainda não tenho uma posição registrada. Use /luma criar." : "Última posição registrada: " + location);
    }
    private void remove(ServerPlayer player) throws CommandSyntaxException {
        withCompanion(player, entity -> {
            if (!canExchange(player, entity)) return;
            if (entity.isPassenger() || entity.isVehicle()) {
                tell(player, "Tire-me do veículo e remova passageiros antes de me remover."); return;
            }
            entity.returnItems(player); entity.discard(); bind(player.getUUID(), null); bump(player.getUUID());
            if (player.getUUID().equals(voiceOwner)) endVoice();
            tell(player, "Companheira removida e itens devolvidos. Use /luma criar para voltar.");
        });
    }
    private void order(ServerPlayer player, Action action) {
        if (closed) return;
        CompanionEntity entity = find(player.getUUID());
        if (entity == null) { missing(player); return; }
        bump(player.getUUID());
        if (action == Action.STATUS) { speak(entity, entity.describe()); return; }
        entity.command(action);
        speak(entity, switch (action) {
            case FOLLOW -> "Vou acompanhar você.";
            case STAY, STOP -> "Vou esperar aqui. Tarefa interrompida.";
            case AUTO -> "Vou acompanhar, defender e procurar madeira próxima com minha ferramenta.";
            case DEFEND -> "Vou acompanhar e defender você dos monstros próximos.";
            case MINE -> "Vou procurar madeira ou pedra próxima que minha ferramenta consiga coletar.";
            case BUILD -> "Vou tentar preparar um abrigo pequeno usando os blocos que você me entregou.";
            default -> "Certo.";
        });
    }
    private long bump(UUID owner) {
        long generation = generations.merge(owner, 1L, Long::sum);
        NativeBrain brain = brains.get(owner);
        if (brain != null) brain.cancelPending();
        return generation;
    }

    void talk(ServerPlayer player, String raw) {
        if (closed || raw == null || raw.isBlank()) return;
        int textEnd = Math.min(raw.length(), 500);
        if (textEnd < raw.length() && Character.isHighSurrogate(raw.charAt(textEnd - 1))) textEnd--;
        String text = raw.substring(0, textEnd).strip();
        CompanionEntity entity = find(player.getUUID());
        if (entity == null) { missing(player); return; }
        Intent local = IntentParser.parse(text);
        long now = System.nanoTime();
        Long previous = lastMessage.get(player.getUUID());
        if (local.action() != Action.STOP && previous != null && now - previous < 500_000_000L) {
            tell(player, "Espere um instante antes de outra mensagem."); return;
        }
        lastMessage.put(player.getUUID(), now);
        if (local.action() != Action.CHAT) { order(player, local.action()); return; }
        long generation = bump(player.getUUID());
        NativeBrain brain = brains.get(player.getUUID());
        if (brain == null) { speak(entity, local.reply()); return; }
        UUID owner = player.getUUID(), entityId = entity.getUUID();
        brain.respond(text, entity.describe()).thenAccept(intent -> {
            if (closed) return;
            server.execute(() -> {
                if (closed || generations.getOrDefault(owner, 0L) != generation) return;
                ServerPlayer current = server.getPlayerList().getPlayer(owner);
                CompanionEntity currentEntity = find(owner);
                if (current != player || !current.isAlive() || currentEntity == null
                        || !entityId.equals(currentEntity.getUUID())) return;
                // Learned text is conversation only. Explicit parser/commands control world changes.
                speak(currentEntity, intent.reply());
            });
        });
    }
    private void enableBrain(ServerPlayer player, boolean enabled) {
        if (closed) return;
        bump(player.getUUID());
        NativeBrain previous = brains.remove(player.getUUID());
        if (previous != null) previous.close();
        if (enabled) brains.put(player.getUUID(), new NativeBrain(brainEngine));
        tell(player, enabled ? "IA própria com memória local ligada nesta sessão, sem serviço externo. "
                + "Ela ainda é experimental e pode errar ou produzir frases confusas; não conversa como modelos grandes. "
                + "As ações continuam por ordens simples. /luma ia desligar desativa." :
                "IA própria desligada. As ordens e respostas locais continuam disponíveis.");
    }
    private void startVoice(ServerPlayer player) {
        if (closed) return;
        if (server.isDedicatedServer() || server.getSingleplayerProfile() == null ||
                !server.getSingleplayerProfile().id().equals(player.getUUID())) {
            tell(player, "O painel de voz desta versão funciona no computador do dono de um mundo de um jogador. Aqui use /luma falar."); return;
        }
        if (find(player.getUUID()) == null) { missing(player); return; }
        endVoice();
        long session = voiceSession;
        UUID owner = player.getUUID();
        VoiceBridge candidate = null;
        try {
            candidate = new VoiceBridge(text -> server.execute(() -> {
                ServerPlayer current = server.getPlayerList().getPlayer(owner);
                if (!closed && current == player && current.isAlive() && session == voiceSession
                        && owner.equals(voiceOwner)) talk(current, text);
            }));
            String url = candidate.start();
            voice = candidate; voiceOwner = owner;
            tell(player, "Abra este endereço no Chrome ou Edge do mesmo computador; o link é privado e temporário:");
            player.sendSystemMessage(Component.literal("[Luma] Abrir painel de voz")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Abrir o painel privado neste computador")))));
            tell(player, "No painel, ative a voz e use o botão do microfone. Para encerrar: /luma voz parar.");
        } catch (IOException | RuntimeException ignored) {
            if (candidate != null) candidate.close();
            endVoice();
            tell(player, "Não consegui abrir o painel de voz local. O chat continua disponível.");
        } catch (NoClassDefFoundError missingModule) {
            // Custom Java images may omit this optional module; preserve unrelated linkage failures.
            if (missingModule.getMessage() == null
                    || !missingModule.getMessage().startsWith("com/sun/net/httpserver/")) throw missingModule;
            endVoice();
            tell(player, "Este Java não inclui o módulo jdk.httpserver. Use um JDK 25 completo no perfil do jogo para abrir o painel de voz.");
        }
    }
    private void stopVoice(ServerPlayer player) {
        if (player.getUUID().equals(voiceOwner)) {
            endVoice(); tell(player, "Painel de voz encerrado.");
        } else tell(player, "Você não tem um painel de voz aberto.");
    }
    private void endVoice() {
        voiceSession++;
        if (voice != null) voice.close();
        voice = null; voiceOwner = null;
    }
    void onDisconnect(ServerPlayer player) {
        if (closed) return;
        UUID owner = player.getUUID();
        bump(owner);
        NativeBrain brain = brains.remove(owner);
        if (brain != null) brain.close();
        lastMessage.remove(owner);
        if (owner.equals(voiceOwner)) endVoice();
    }
    void speak(CompanionEntity entity, String text) {
        if (closed) return;
        UUID owner = entity.getOwnerId();
        if (owner == null || text == null || !entity.getUUID().toString().equals(boundId(owner))) return;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) tell(player, text);
        if (voice != null && owner.equals(voiceOwner)) voice.say(text);
    }
    private void tell(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal("[Luma] " + text)); }
    void onDeath(CompanionEntity entity) {
        UUID owner = entity.getOwnerId();
        if (owner != null && entity.getUUID().toString().equals(boundId(owner))) {
            speak(entity, "Caí nesta aventura. Meus itens estão no chão; você pode me chamar novamente com /luma criar.");
            bind(owner, null); bump(owner);
            if (owner.equals(voiceOwner)) endVoice();
        }
    }
    private void rememberLocation(CompanionEntity entity) {
        UUID owner = entity.getOwnerId();
        if (owner == null || !entity.getUUID().toString().equals(boundId(owner))) return;
        String key = owner.toString();
        BlockPos p = entity.blockPosition();
        String location = entity.level().dimension().identifier() + " — X " + p.getX() + ", Y " + p.getY() + ", Z " + p.getZ();
        Map<String, String> existing = server.overworld().getAttachedOrCreate(LumaMod.LOCATIONS);
        if (!location.equals(existing.get(key))) {
            Map<String, String> next = new HashMap<>(existing); next.put(key, location);
            server.overworld().setAttached(LumaMod.LOCATIONS, Map.copyOf(next));
        }
    }
    void tick() {
        if (closed) return;
        if (voice != null && server.getPlayerList().getPlayer(voiceOwner) == null) {
            endVoice();
        }
        for (ServerLevel level : server.getAllLevels()) for (Entity entity : level.getAllEntities()) {
            if (entity instanceof CompanionEntity companion && companion.isAlive()) rememberLocation(companion);
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        endVoice();
        brains.values().forEach(NativeBrain::close);
        brainEngine.close();
        brains.clear(); generations.clear(); lastMessage.clear();
    }
}
