package br.com.luma.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class IntentParserTest {
    @ParameterizedTest
    @CsvSource({
            "Luma por favor me siga!, FOLLOW",
            "ME SIGA, FOLLOW",
            "venha comigo, FOLLOW",
            "fique aqui, STAY",
            "espere por mim, STAY",
            "modo autônomo, AUTO",
            "seja autônoma, AUTO",
            "me proteja, DEFEND",
            "defenda a casa, DEFEND",
            "minere pedra, MINE",
            "comece a minerar, MINE",
            "constrói uma casa, BUILD",
            "construa uma ponte, BUILD",
            "pare agora, STOP",
            "parar, STOP",
            "cancele a tarefa, STOP",
            "pare e não construa, STOP",
            "não me siga mais, STOP",
            "não minere aqui, STOP",
            "status, STATUS",
            "O que você está fazendo?, STATUS"
    })
    void recognizesExplicitPortugueseCommands(String text, Action expected) {
        Intent intent = IntentParser.parse(text);
        assertEquals(expected, intent.action());
        assertFalse(intent.reply().isBlank());
    }

    @ParameterizedTest
    @CsvSource({
            "não pare de me seguir",
            "não pare",
            "nunca construa aqui",
            "minerar ou construir?",
            "me siga e construa uma casa",
            "talvez minere pedra",
            "você pode explicar como construir?",
            "mineral é uma palavra",
            "o que significa proteger?",
            "para onde vamos?",
            "seguiríamos amanhã"
    })
    void doesNotInterpretNegationQuestionsOrWordFragmentsAsCommands(String text) {
        assertEquals(Action.CHAT, IntentParser.parse(text).action());
    }

    @Test
    void handlesMissingAndEmptyInput() {
        assertEquals(Action.CHAT, IntentParser.parse(null).action());
        assertEquals(Action.CHAT, IntentParser.parse("  \n\t ").action());
        assertFalse(IntentParser.parse(null).reply().isBlank());
    }

    @Test
    void normalizesDecomposedAccentsAndPunctuation() {
        assertEquals(Action.AUTO, IntentParser.parse("  LUMA: modo auto\u0302nomo!!! ").action());
    }

    @Test
    void providesLocalConversationAndUsefulHelp() {
        assertTrue(IntentParser.parse("olá").reply().contains("Luma"));
        assertTrue(IntentParser.parse("ajuda").reply().contains("status"));
        assertEquals(Action.CHAT, IntentParser.parse("obrigada").action());
        assertEquals(Action.CHAT, IntentParser.parse("desconhecido").action());
    }

    @Test
    void intentRequiresAnActionAndAReply() {
        assertThrows(NullPointerException.class, () -> new Intent(null, "reply"));
        assertThrows(NullPointerException.class, () -> new Intent(Action.CHAT, null));
    }
}
