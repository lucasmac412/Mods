package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataSetTest {
    @TempDir Path temporary;

    @Test
    void tsvPreservesPortugueseAndRejectsMalformedOrEmptyData() throws IOException {
        Path file = temporary.resolve("examples.tsv");
        Files.writeString(file, "# comentário\n\n  Olá, Lu!  \t  Vamos explorar? 🧭  \n");
        List<DataSet.Example> examples = DataSet.read(file);
        assertEquals(List.of(new DataSet.Example("Olá, Lu!", "Vamos explorar? 🧭")), examples);

        Files.writeString(file, "pergunta\tresposta\tcampo extra\n");
        assertThrows(IOException.class, () -> DataSet.read(file));
        Files.writeString(file, "# sem exemplos\n\n");
        assertThrows(IOException.class, () -> DataSet.read(file));
        Files.writeString(file, "pergunta\t   \n");
        assertThrows(IllegalArgumentException.class, () -> DataSet.read(file));
    }

    @Test
    void duplicateDetectionPreventsNormalizedQuestionsAcrossDataSplits() throws IOException {
        Path file = temporary.resolve("duplicates.tsv");
        Files.writeString(file, "Como está você?\tBem, obrigada.\nCOMO ESTA VOCE!\tTudo certo.\n");
        assertThrows(IOException.class, () -> DataSet.read(file));

        List<DataSet.Example> train = List.of(new DataSet.Example("Como está você?", "Estou bem."));
        List<DataSet.Example> heldOut = List.of(new DataSet.Example("COMO ESTA VOCE!", "Outra resposta."));
        assertThrows(IllegalArgumentException.class, () -> DataSet.requireDisjoint(train, heldOut));
        assertDoesNotThrow(() -> DataSet.requireDisjoint(train,
                List.of(new DataSet.Example("O que faremos depois?", "Vamos planejar."))));
    }

    @Test
    void teacherForcingScoresAnswerAndEosWhileMaskingEveryPromptTarget() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("Jogador: Olá!\nLuma: Vamos?\n"), 270);
        DataSet.Example example = new DataSet.Example("Olá!", "Vamos?");
        DataSet.Encoded encoded = example.encode(tokenizer);
        int[] prefix = tokenizer.encode(DataSet.prefix(example.prompt()));
        int[] answer = tokenizer.encode(example.reply());
        assertEquals(1 + prefix.length, encoded.responseStart());
        assertEquals(ByteTokenizer.BOS, encoded.tokens()[0]);
        assertEquals(ByteTokenizer.EOS, encoded.tokens()[encoded.tokens().length - 1]);
        assertArrayEquals(answer, Arrays.copyOfRange(encoded.tokens(), encoded.responseStart(),
                encoded.tokens().length - 1));

        DataSet.Window window = encoded.window(encoded.tokens().length);
        assertFalse(window.truncated());
        assertEquals(answer.length + 1, window.scoredTokens());
        for (int i = 0; i < window.targets().length; i++) {
            int absoluteTarget = i + 1;
            if (absoluteTarget < encoded.responseStart()) assertEquals(-1, window.targets()[i]);
            else assertEquals(encoded.tokens()[absoluteTarget], window.targets()[i]);
            assertEquals(encoded.tokens()[i], window.input()[i]);
        }
        assertEquals(ByteTokenizer.EOS, window.targets()[window.targets().length - 1]);
    }

    @Test
    void leftTruncationReportsTheExactRetainedSuffixAndScoredTargets() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("a"), 259);
        DataSet.Encoded encoded = new DataSet.Example("Uma pergunta mais longa", "resposta").encode(tokenizer);
        DataSet.Window window = encoded.window(5);
        int start = encoded.tokens().length - 6;
        assertTrue(window.truncated());
        assertEquals(5, window.input().length);
        assertArrayEquals(Arrays.copyOfRange(encoded.tokens(), start, start + 5), window.input());
        assertArrayEquals(Arrays.copyOfRange(encoded.tokens(), start + 1, start + 6), window.targets());
        assertEquals(5, window.scoredTokens());
        assertEquals(ByteTokenizer.EOS, window.targets()[4]);
    }

    @Test
    void veryLongPromptsNeverBecomeScoredResponseTokens() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("a"), 259);
        DataSet.Encoded encoded = new DataSet.Example("a".repeat(80), "oi").encode(tokenizer);
        DataSet.Window window = encoded.window(8);
        assertTrue(window.truncated());
        assertEquals(3, window.scoredTokens()); // UTF-8 bytes o, i, then EOS.
        assertEquals(5, Arrays.stream(window.targets()).filter(token -> token == -1).count());
        assertArrayEquals(new int[]{'o' + 3, 'i' + 3, ByteTokenizer.EOS},
                Arrays.copyOfRange(window.targets(), 5, 8));
    }

    @Test
    void rejectsControlCharactersAndOversizedExamples() {
        assertThrows(IllegalArgumentException.class, () -> new DataSet.Example("oi\tLu", "Olá!"));
        assertThrows(IllegalArgumentException.class, () -> new DataSet.Example("Oi", "duas\nlinhas"));
        assertThrows(IllegalArgumentException.class, () -> new DataSet.Example("x".repeat(1001), "Olá!"));
        assertThrows(IllegalArgumentException.class, () -> new DataSet.Example("Oi", "x".repeat(1001)));
    }
}
