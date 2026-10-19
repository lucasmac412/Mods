package br.com.luma.ai;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Small, deterministic Portuguese command parser; it needs no model or network. */
public final class IntentParser {
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern NEGATION = Pattern.compile("\\b(nao|nunca|jamais)\\b");

    private IntentParser() {}

    public static Intent parse(String text) {
        String command = normalize(text);
        command = command.replaceFirst("^(oi |ola )?luma\\b\\s*", "")
                .replaceFirst("^por favor\\s+", "").trim();

        if (command.isEmpty()) {
            return chat("Pode falar comigo! Use “ajuda” para conhecer as ordens.");
        }
        // An explicit leading stop remains useful even with a later restriction:
        // “pare e não construa”. Conversely, “não pare” never matches this branch.
        if (starts(command, "pare|parar|cancele|cancelar|stop")
                || command.equals("para") || command.startsWith("para de ")) {
            return new Intent(Action.STOP, "Certo, vou interromper a tarefa atual.");
        }
        if (command.matches("^nao (?:me siga|me segue|siga me|minere|minera|construa|constroi|ataque)(?:\\s.*)?$")) {
            return new Intent(Action.STOP, "Entendido, vou interromper a tarefa atual.");
        }
        // Do not turn a negated command or an ambiguous choice into an order.
        if (hasNegation(command)) {
            return chat("Entendi a restrição. Para interromper uma tarefa, diga “parar”.");
        }
        if (isAmbiguous(command)) {
            return chat("Escolha uma ação por vez: seguir, ficar, proteger, minerar, construir ou parar.");
        }
        if (starts(command, "modo autonomo|modo auto|seja autonomo|seja autonoma|fique autonomo|fique autonoma|ative autonomia|ativar autonomia|autonomo|autonoma|autonomia|auto|aja por conta propria")) {
            return new Intent(Action.AUTO, "Modo autônomo solicitado. Vou cuidar das tarefas que consigo executar.");
        }
        if (starts(command, "fique|fica|ficar|espere|espera|aguarde|modo parado")) {
            return new Intent(Action.STAY, "Vou esperar aqui.");
        }
        if (starts(command, "me siga|siga me|siga|seguir|segue|me segue|me acompanhe|acompanhe|vem comigo|venha comigo|modo seguir")) {
            return new Intent(Action.FOLLOW, "Certo, vou seguir você.");
        }
        if (starts(command, "me proteja|proteja|proteger|me defenda|defenda|defender|defesa|modo defesa")) {
            return new Intent(Action.DEFEND, "Vou tentar proteger você dos inimigos próximos.");
        }
        if (starts(command, "minere|minerar|minera|comece a minerar|colete minerios|coletar minerios")) {
            return new Intent(Action.MINE, "Vou procurar um bloco que eu consiga minerar.");
        }
        if (starts(command, "construa|construir|constroi|comece a construir|modo construcao")) {
            return new Intent(Action.BUILD, "Vou tentar construir com os materiais disponíveis.");
        }
        if (starts(command, "status|estado|situacao|como esta|o que esta fazendo|o que voce esta fazendo")) {
            return new Intent(Action.STATUS, "Vou consultar meu estado atual.");
        }
        if (starts(command, "ajuda|help|comandos|o que voce faz|o que voce pode fazer")) {
            return chat("Você pode pedir: seguir, ficar aqui, autônomo, proteger, minerar, construir, parar ou status.");
        }
        if (starts(command, "oi|ola|bom dia|boa tarde|boa noite")) {
            return chat("Oi! Sou a Luma. Vamos explorar juntos?");
        }
        if (starts(command, "obrigado|obrigada|valeu")) {
            return chat("De nada! Estou por aqui.");
        }
        if (starts(command, "quem e voce|qual e seu nome|qual seu nome")) {
            return chat("Sou a Luma, sua companheira no Minecraft. Entendo ordens simples em português.");
        }
        if (starts(command, "tudo bem|como vai|como voce esta")) {
            return chat("Estou pronta para acompanhar você! Qual é o próximo plano?");
        }
        return chat("Não entendi essa ordem. Tente “ajuda” para ver o que posso fazer.");
    }

    private static boolean starts(String text, String alternatives) {
        return text.matches("^(?:" + alternatives + ")(?:\\s.*)?$");
    }

    static boolean hasNegation(String text) {
        return NEGATION.matcher(normalize(text)).find();
    }

    static boolean isAmbiguous(String text) {
        String command = normalize(text);
        return command.matches(".*\\b(?:ou|talvez)\\b.*")
                || command.matches(".*\\b(?:e|depois|entao|mas)\\s+(?:me\\s+)?(?:siga|seguir|minere|minerar|construa|construir|proteja|defenda|fique|pare)\\b.*");
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return ACCENTS.matcher(decomposed).replaceAll("")
                .toLowerCase(PORTUGUESE)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Intent chat(String reply) {
        return new Intent(Action.CHAT, reply);
    }
}
