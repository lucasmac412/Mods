package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalMemoryTest {
    @TempDir Path temporary;

    @Test
    void exactAndAccentEquivalentQuestionsRecoverOnlyAuthoredTrainingInformation() {
        LocalMemory memory = LocalMemory.fromExamples(List.of(
                new DataSet.Example("Qual ferramenta coleta troncos?", "Um machado ajuda a coletar troncos."),
                new DataSet.Example("Quantos espaços tem a mochila?", "A mochila tem 27 espaços.")), 0.25);
        LocalMemory.Match result = memory.lookup("QUANTOS ESPACOS TEM A MOCHILA?");
        assertTrue(result.found());
        assertEquals("A mochila tem 27 espaços.", result.information());
        assertEquals("Quantos espaços tem a mochila?", result.sourcePrompt());
        assertEquals(1.0, result.score(), 1e-12);
        assertEquals(2, memory.size());
        assertEquals(0.25, memory.threshold());
    }

    @Test
    void paraphrasedWordsAndCharacterFragmentsCanFindTheRelevantSource() {
        LocalMemory memory = LocalMemory.fromExamples(List.of(
                new DataSet.Example("Ferramenta para coletar troncos", "Use um machado."),
                new DataSet.Example("Como guardar ferramentas", "Guarde ferramentas em um baú.")), 0.25);
        LocalMemory.Match match = memory.lookup("Luma, eu queria coletar um tronco.");
        assertTrue(match.found());
        assertEquals("Use um machado.", match.information());
        assertTrue(match.score() > 0.25 && match.score() <= 1);
    }

    @Test
    void optionalAnswerFeaturesCanRetrieveInformationMentionedOnlyInTheTrainingReply() {
        List<DataSet.Example> examples = List.of(
                new DataSet.Example("Qual instrumento preferido?", "Um machado corta troncos."),
                new DataSet.Example("Como lidar com pedra?", "Uma picareta quebra pedras."));
        LocalMemory old = LocalMemory.fromExamples(examples, 0.1);
        LocalMemory zero = LocalMemory.fromExamples(examples, 0.1, 0);
        assertEquals(0, old.answerWeight());
        assertEquals(old.lookup("troncos"), zero.lookup("troncos"));
        LocalMemory weighted = LocalMemory.fromExamples(examples, 0.1, 0.7);
        LocalMemory.Match match = weighted.lookup("troncos");
        assertTrue(match.found());
        assertEquals("Um machado corta troncos.", match.information());
        assertEquals("Qual instrumento preferido?", match.sourcePrompt());
        assertEquals(0.7, weighted.answerWeight());
        assertTrue(match.score() > old.lookup("troncos").score());
        for (double value : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> LocalMemory.fromExamples(examples, 0.1, value));
    }

    @Test
    void emptyUnrelatedAndBelowThresholdQueriesProduceAnExplicitUnknownNote() {
        LocalMemory memory = LocalMemory.fromExamples(List.of(new DataSet.Example("pedra picareta", "Uma picareta quebra pedra.")), 0.95);
        for (String query : List.of("", "eu e voce", "abacaxi ultravioletazzz", "pedras")) {
            LocalMemory.Match match = memory.lookup(query);
            assertFalse(match.found(), query);
            assertEquals("", match.sourcePrompt());
            assertEquals(LocalMemory.UNKNOWN, match.information());
            assertTrue(Double.isFinite(match.score()));
        }
        assertThrows(NullPointerException.class, () -> memory.lookup(null));
    }

    @Test
    void thresholdsAndKnowledgeSizeAreValidatedAndInputOrderBreaksExactTies() {
        var first = new DataSet.Example("Qual picareta?", "Primeira informação.");
        var second = new DataSet.Example("Qual picareta", "Segunda informação.");
        for (double value : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> LocalMemory.fromExamples(List.of(first), value));
        assertThrows(IllegalArgumentException.class, () -> LocalMemory.fromExamples(List.of(), 0.25));
        assertEquals("Primeira informação.", LocalMemory.fromExamples(List.of(first, second), 0.25).lookup("picareta").information());
    }

    @Test
    void formattedPromptIsSingleLineBoundedAndNeverSplitsASurrogatePair() {
        String result = LocalMemory.format("x".repeat(499) + "😀\n", "y".repeat(399) + "😀\t");
        assertEquals("Pergunta: " + "x".repeat(499) + " | Informação: " + "y".repeat(399), result);
        assertTrue(result.length() <= 1000);
        assertFalse(result.contains("\n"));
        assertEquals("Pergunta: oi você | Informação: tudo bem", LocalMemory.format("oi\nvocê", "tudo\tbem"));
    }

    @Test
    void preparingGroundingNeverUsesHeldOutRepliesAsIndexEntriesOrNotes() throws Exception {
        Path input = temporary.resolve("input"), first = temporary.resolve("first"), second = temporary.resolve("second");
        Files.createDirectories(input);
        String training = "Qual ferramenta coleta troncos?\tUm machado coleta troncos.\nQuantos espaços tem a mochila?\tMinha mochila tem 27 espaços.\n";
        Files.writeString(input.resolve("train.tsv"), training);
        Files.writeString(input.resolve("validation.tsv"), "Como eu pego troncos?\tREFERENCIA_VALIDACAO_NUNCA_NOTA\n");
        Files.writeString(input.resolve("test.tsv"), "A mochila leva quantos itens?\tREFERENCIA_TESTE_NUNCA_NOTA\n");
        Main.run(new String[]{"prepare-grounded", input.toString(), first.toString(), "--answer-weight", "0.35"});
        Files.writeString(input.resolve("validation.tsv"), "Como eu pego troncos?\tREF_VALIDACAO_SUBSTITUIDA\n");
        Files.writeString(input.resolve("test.tsv"), "A mochila leva quantos itens?\tREF_TESTE_SUBSTITUIDA\n");
        Main.run(new String[]{"prepare-grounded", input.toString(), second.toString(), "--answer-weight", "0.35"});
        assertEquals(training, Files.readString(first.resolve("knowledge.tsv")));
        assertArrayEquals(Files.readAllBytes(first.resolve("knowledge.tsv")), Files.readAllBytes(second.resolve("knowledge.tsv")));
        assertArrayEquals(Files.readAllBytes(first.resolve("train.tsv")), Files.readAllBytes(second.resolve("train.tsv")));
        for (String split : List.of("validation", "test")) {
            List<DataSet.Example> before = DataSet.read(first.resolve(split + ".tsv")), after = DataSet.read(second.resolve(split + ".tsv"));
            assertEquals(before.size(), after.size());
            for (int i = 0; i < before.size(); i++) {
                assertEquals(before.get(i).prompt(), after.get(i).prompt());
                assertFalse(before.get(i).prompt().contains("REFERENCIA"));
                assertFalse(after.get(i).prompt().contains("SUBSTITUIDA"));
                assertNotEquals(before.get(i).reply(), after.get(i).reply());
            }
        }
        String manifest = Files.readString(first.resolve("grounding.json"));
        assertTrue(manifest.contains("\"index_source\": \"train.tsv only\""));
        assertTrue(manifest.contains("\"held_out_replies_used_in_index_or_notes\": false"));
        assertTrue(manifest.contains("\"neural_generation_required\": true"));
        assertTrue(manifest.contains("\"answer_weight\": 0.35"));
        assertFalse(manifest.contains("matched_test"), "Do not expose retrieval test statistics during model selection");
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"prepare-grounded", input.toString(), input.toString()}));
    }
}
