package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextGeneratorTest {
    private static final String SHA = "a".repeat(64);
    private static final int X = 3 + 'x';
    private static final int Y = 3 + 'y';

    @Test
    void excludesPaddingAndBosEvenWhenTheyHaveHigherLogitsThanRealText() {
        ModelBundle bundle = constantModel();
        double[] bias = outputBias(bundle);
        bias[ByteTokenizer.PAD] = 1000;
        bias[ByteTokenizer.BOS] = 900;
        bias[X] = 10;
        assertArrayEquals(new int[]{X, X, X, X}, TextGenerator.generateTokens(bundle, "oi", 4, 0, 20, 7));
        assertEquals("xxxx", TextGenerator.generate(bundle, "oi", 4, 0, 20, 7));
    }

    @Test
    void eosEndsGenerationAndIsNotExposedAsAnAnswerToken() {
        ModelBundle bundle = constantModel();
        double[] bias = outputBias(bundle);
        bias[ByteTokenizer.PAD] = 1000;
        bias[ByteTokenizer.BOS] = 900;
        bias[ByteTokenizer.EOS] = 10;
        assertArrayEquals(new int[0], TextGenerator.generateTokens(bundle, "quem é você?", 128, 0, 20, 7));
        assertEquals("", TextGenerator.generate(bundle, "quem é você?", 128, 0, 20, 7));
    }

    @Test
    void interruptionCancelsGenerationAndPreservesTheInterruptFlag() {
        ModelBundle bundle = constantModel();
        outputBias(bundle)[X] = 10;
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> TextGenerator.generate(bundle, "oi", 128, 0, 20, 7));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void seededSamplingIsReproducibleAndChoosesOnlyItsTopKCandidates() {
        ModelBundle bundle = constantModel();
        double[] bias = outputBias(bundle);
        bias[X] = 0;
        bias[Y] = 0;
        int[] first = TextGenerator.generateTokens(bundle, "oi", 32, 0.8, 2, 42);
        assertArrayEquals(first, TextGenerator.generateTokens(bundle, "oi", 32, 0.8, 2, 42));
        for (int token : first) assertTrue(token == X || token == Y);
        assertTrue(Arrays.stream(first).anyMatch(t -> t == X));
        assertTrue(Arrays.stream(first).anyMatch(t -> t == Y));
        Set<String> results = new HashSet<>();
        for (long seed = 0; seed < 4; seed++) {
            results.add(Arrays.toString(TextGenerator.generateTokens(bundle, "oi", 32, 0.8, 2, seed)));
        }
        assertTrue(results.size() > 1, "seeded stochastic sampling must allow distinct outcomes");
    }

    @Test
    void greedyAndTopOneIgnoreSeedAndResolveTiesByTheSmallerTokenId() {
        ModelBundle bundle = constantModel();
        double[] bias = outputBias(bundle);
        bias[X] = 1;
        bias[Y] = 1;
        int[] expected = {X, X, X};
        assertArrayEquals(expected, TextGenerator.generateTokens(bundle, "oi", 3, 0, 20, 1));
        assertArrayEquals(expected, TextGenerator.generateTokens(bundle, "oi", 3, 0, 20, 999));
        assertArrayEquals(expected, TextGenerator.generateTokens(bundle, "oi", 3, 0.7, 1, 4));
    }

    @Test
    void longPromptsAndGeneratedAnswersSlideWithinTheModelsContextWindow() {
        ModelBundle bundle = constantModel();
        outputBias(bundle)[X] = 1;
        int[] generated = TextGenerator.generateTokens(bundle, "Pergunta longa. ".repeat(50), 128, 0, 1, 1);
        assertEquals(128, generated.length);
        assertTrue(Arrays.stream(generated).allMatch(t -> t == X));
        assertArrayEquals(new int[0], TextGenerator.generateTokens(bundle, "oi", 0, 0, 1, 1));
    }

    @Test
    void rejectsInvalidSamplingArgumentsAndNonFiniteModelOutputs() {
        ModelBundle bundle = constantModel();
        for (int count : new int[]{-1, 129}) {
            assertThrows(IllegalArgumentException.class, () -> TextGenerator.generateTokens(bundle, "oi", count, 0.7, 20, 1));
        }
        for (double temperature : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> TextGenerator.generateTokens(bundle, "oi", 4, temperature, 20, 1));
        }
        for (int k : new int[]{0, 260}) {
            assertThrows(IllegalArgumentException.class, () -> TextGenerator.generateTokens(bundle, "oi", 4, 0.7, k, 1));
        }
        assertThrows(NullPointerException.class, () -> TextGenerator.generateTokens(bundle, null, 4, 0.7, 20, 1));
        assertThrows(IllegalArgumentException.class, () -> TextGenerator.generateTokens(bundle, "a".repeat(65_537), 4, 0.7, 20, 1));
        outputBias(bundle)[X] = Double.NaN;
        assertThrows(ArithmeticException.class, () -> TextGenerator.generateTokens(bundle, "oi", 4, 0.7, 20, 1));
    }

    private static ModelBundle constantModel() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("oi"), 259);
        Transformer model = new Transformer(new ModelConfig(259, 8, 8, 2, 1, 16), 1);
        for (Transformer.Parameter parameter : model.parametersForTesting()) Arrays.fill(parameter.value, 0);
        ModelBundle bundle = new ModelBundle(tokenizer, model, SHA);
        Arrays.fill(outputBias(bundle), -1000);
        return bundle;
    }

    private static double[] outputBias(ModelBundle bundle) {
        return bundle.model().parametersForTesting().stream().filter(p -> p.name.equals("output.bias"))
                .findFirst().orElseThrow().value;
    }
}
