package br.com.luma.nativeai;

/** Dimensions of a small, trainable, causal decoder; all weights start from scratch. */
public record ModelConfig(int vocabSize, int contextLength, int embeddingSize,
                          int heads, int layers, int feedForwardSize) {
    public ModelConfig {
        require(vocabSize >= 2 && vocabSize <= 8192, "vocabSize must be between 2 and 8192");
        require(contextLength >= 1 && contextLength <= 512, "contextLength must be between 1 and 512");
        require(embeddingSize >= 8 && embeddingSize <= 512, "embeddingSize must be between 8 and 512");
        require(heads >= 1 && heads <= 16 && embeddingSize % heads == 0,
                "heads must divide embeddingSize and be between 1 and 16");
        require(layers >= 1 && layers <= 12, "layers must be between 1 and 12");
        require(feedForwardSize >= embeddingSize && feedForwardSize <= 8 * embeddingSize,
                "feedForwardSize must be between embeddingSize and 8 * embeddingSize");
        long parameterCount = estimate(vocabSize, contextLength, embeddingSize, layers, feedForwardSize);
        require(parameterCount <= 20_000_000L, "this CPU prototype is limited to 20 million parameters");
        long attentionCells = (long) layers * heads * contextLength * contextLength;
        require(attentionCells <= 32_000_000L, "attention workspace exceeds this CPU prototype's limit");
    }

    public long parameterCount() {
        return estimate(vocabSize, contextLength, embeddingSize, layers, feedForwardSize);
    }

    private static long estimate(int v, int t, int d, int l, int f) {
        return 2L * v * d + (long) t * d
                + (long) l * (4L * d * d + 2L * d * f + 9L * d + f) + 2L * d + v;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
