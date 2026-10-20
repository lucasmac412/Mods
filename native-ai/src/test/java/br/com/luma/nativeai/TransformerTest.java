package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformerTest {
    @Test
    void reverseModeGradientsMatchFiniteDifferencesAcrossTheNetwork() {
        Transformer model = new Transformer(new ModelConfig(7, 4, 8, 2, 2, 16), 73);
        int[][] input = {{1, 2, 3, 1}, {4, 1, 2}};
        int[][] targets = {{-1, 3, 1, 4}, {-1, 2, 3}};
        model.gradientsForTesting(input, targets);
        List<String> checks = List.of("embedding.token", "embedding.position", "block.0.query.weight",
                "block.0.key.weight", "block.0.value.weight", "block.0.attention.weight",
                "block.0.norm1.scale", "block.0.norm1.bias", "block.0.norm2.scale",
                "block.0.ff1.weight", "block.0.ff2.weight", "block.1.query.weight",
                "block.1.norm2.bias", "norm.final.scale", "output.weight", "output.bias");
        for (String name : checks) {
            Transformer.Parameter parameter = model.parametersForTesting().stream()
                    .filter(p -> p.name.equals(name)).findFirst().orElseThrow();
            int index = 0;
            for (int i = 1; i < parameter.gradient.length; i++) {
                if (Math.abs(parameter.gradient[i]) > Math.abs(parameter.gradient[index])) index = i;
            }
            double analytical = parameter.gradient[index];
            assertTrue(Math.abs(analytical) > 1e-10, "gradient must be informative: " + name);
            double original = parameter.value[index];
            double epsilon = 1e-5;
            parameter.value[index] = original + epsilon;
            double plus = model.loss(input, targets);
            parameter.value[index] = original - epsilon;
            double minus = model.loss(input, targets);
            parameter.value[index] = original;
            double numerical = (plus - minus) / (2 * epsilon);
            double tolerance = 2e-7 + 0.002 * Math.abs(numerical);
            assertEquals(numerical, analytical, tolerance, "finite-difference gradient: " + name);
        }
    }

    @Test
    void futureTokensCannotChangeEarlierPredictions() {
        Transformer model = new Transformer(new ModelConfig(11, 8, 16, 4, 2, 32), 18);
        double[] first = model.logitsForTesting(new int[]{1, 2, 3, 4, 5});
        double[] differentFuture = model.logitsForTesting(new int[]{1, 2, 3, 9, 10});
        for (int i = 0; i < 3 * 11; i++) assertEquals(first[i], differentFuture[i], 1e-12);
        double[] shortContext = model.nextLogits(new int[]{1, 2, 3});
        assertArrayEquals(Arrays.copyOfRange(first, 2 * 11, 3 * 11), shortContext, 1e-12);
        assertFalse(Arrays.equals(first, differentFuture), "changed future should affect its own predictions");
    }

    @Test
    void maskedPromptIsExcludedFromLossButStillReceivesAttentionGradients() {
        Transformer model = new Transformer(new ModelConfig(9, 6, 16, 2, 1, 32), 34);
        int[][] input = {{1, 2, 3, 4}};
        int[][] targets = {{-1, -1, 4, 5}};
        double[] logits = model.logitsForTesting(input[0]);
        double expected = (singleTokenLoss(logits, 2 * 9, 4, 9)
                + singleTokenLoss(logits, 3 * 9, 5, 9)) / 2;
        assertEquals(expected, model.loss(input, targets), 1e-12);
        model.gradientsForTesting(input, targets);
        Transformer.Parameter tokenEmbedding = model.parametersForTesting().stream()
                .filter(p -> p.name.equals("embedding.token")).findFirst().orElseThrow();
        double maskedTokenGradient = 0;
        for (int i = 16; i < 32; i++) maskedTokenGradient += Math.abs(tokenEmbedding.gradient[i]);
        assertTrue(maskedTokenGradient > 1e-8, "an unscored prompt must still influence the answer through attention");
        assertThrows(IllegalArgumentException.class, () -> model.loss(input, new int[][]{{-1, -1, -1, -1}}));
    }

    @Test
    void learnsARepeatedSequenceFromRandomWeights() {
        Transformer model = new Transformer(new ModelConfig(8, 7, 16, 2, 1, 32), 51);
        int[][] input = {{1, 2, 3, 4, 5, 6, 1}, {1, 2, 3, 4, 5, 6, 1}};
        int[][] targets = {{2, 3, 4, 5, 6, 1, 2}, {2, 3, 4, 5, 6, 1, 2}};
        double before = model.loss(input, targets);
        AdamW optimizer = new AdamW(0.015, 0.01, 1);
        for (int i = 0; i < 180; i++) assertTrue(Double.isFinite(model.trainBatch(input, targets, optimizer)));
        double after = model.loss(input, targets);
        assertTrue(after < 0.05, "the small sequence should be learned; loss=" + after);
        assertTrue(after < before / 20, "training must materially improve the loss");
        assertEquals(180, optimizer.step());
        assertEquals(4, argmax(model.nextLogits(new int[]{1, 2, 3})));
    }

    @Test
    void checkpointRoundTripPreservesPredictionsAndRejectsMalformedWeights() throws IOException {
        ModelConfig config = new ModelConfig(13, 5, 8, 2, 1, 16);
        Transformer original = new Transformer(config, 91);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        original.writeWeights(new DataOutputStream(buffer));
        byte[] encoded = buffer.toByteArray();
        Transformer restored = Transformer.readWeights(config, new DataInputStream(new ByteArrayInputStream(encoded)));
        assertArrayEquals(original.nextLogits(new int[]{1, 2, 3}), restored.nextLogits(new int[]{1, 2, 3}), 0);
        assertEquals(config.parameterCount(), original.parameterCount());
        assertThrows(IOException.class, () -> Transformer.readWeights(new ModelConfig(14, 5, 8, 2, 1, 16),
                new DataInputStream(new ByteArrayInputStream(encoded))));
        assertThrows(IOException.class, () -> Transformer.readWeights(config,
                new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(encoded, encoded.length - 1)))));
        byte[] invalid = encoded.clone();
        // Last tensor's final element: replace its double bits with quiet NaN.
        long nan = Double.doubleToRawLongBits(Double.NaN);
        for (int i = 0; i < 8; i++) invalid[invalid.length - 8 + i] = (byte) (nan >>> (56 - 8 * i));
        assertThrows(IOException.class, () -> Transformer.readWeights(config,
                new DataInputStream(new ByteArrayInputStream(invalid))));
    }

    @Test
    void invalidBatchesAreRejectedBeforeUpdatingTheModel() {
        Transformer model = new Transformer(new ModelConfig(8, 4, 8, 2, 1, 16), 0);
        AdamW optimizer = new AdamW(0.01, 0, 1);
        double[] before = model.nextLogits(new int[]{1});
        assertThrows(IllegalArgumentException.class, () -> model.trainBatch(new int[][]{{1}}, new int[][]{{-2}}, optimizer));
        assertThrows(IllegalArgumentException.class, () -> model.trainBatch(new int[][]{{1}}, new int[][]{{8}}, optimizer));
        assertThrows(IllegalArgumentException.class, () -> model.nextLogits(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> model.nextLogits(new int[]{1, 2, 3, 4, 5}));
        assertArrayEquals(before, model.nextLogits(new int[]{1}), 0);
        assertEquals(0, optimizer.step());
    }

    private static double singleTokenLoss(double[] logits, int offset, int target, int vocabulary) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < vocabulary; i++) maximum = Math.max(maximum, logits[offset + i]);
        double sum = 0;
        for (int i = 0; i < vocabulary; i++) sum += Math.exp(logits[offset + i] - maximum);
        return maximum + Math.log(sum) - logits[offset + target];
    }

    private static int argmax(double[] values) {
        int result = 0;
        for (int i = 1; i < values.length; i++) if (values[i] > values[result]) result = i;
        return result;
    }
}
