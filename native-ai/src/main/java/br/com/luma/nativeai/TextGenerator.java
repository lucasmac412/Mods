package br.com.luma.nativeai;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;

/** Autoregressive sampling from learned weights, without canned response rules. */
public final class TextGenerator {
    private static final int MAX_NEW_TOKENS = 128;
    private static final int MAX_PROMPT_CHARACTERS = 65_536;

    private TextGenerator() { }

    public static String generate(ModelBundle bundle, String prompt, int maxNewTokens,
                                  double temperature, int topK, long seed) {
        return bundle.tokenizer().decode(generateTokens(bundle, prompt, maxNewTokens,
                temperature, topK, seed));
    }

    /**
     * Returns newly generated tokens only, excluding the terminating EOS.
     * Zero temperature chooses the highest logit; ties choose the smaller ID.
     * Positive temperatures sample the top K candidates with a reproducible seed.
     * PAD and BOS are always masked; EOS is allowed to end an answer naturally.
     */
    public static int[] generateTokens(ModelBundle bundle, String prompt, int maxNewTokens,
                                       double temperature, int topK, long seed) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(prompt, "prompt");
        if (maxNewTokens < 0 || maxNewTokens > MAX_NEW_TOKENS) {
            throw new IllegalArgumentException("maxNewTokens must be between 0 and 128");
        }
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and nonnegative");
        }
        int vocabulary = bundle.model().config().vocabSize();
        if (topK < 1 || topK > vocabulary) {
            throw new IllegalArgumentException("topK must be between 1 and the vocabulary size");
        }
        if (prompt.length() > MAX_PROMPT_CHARACTERS) {
            throw new IllegalArgumentException("prompt exceeds 65536 characters");
        }
        if (maxNewTokens == 0) return new int[0];

        int[] prefix = bundle.tokenizer().encode("Jogador: " + prompt + "\nLuma: ");
        int[] history = new int[1 + prefix.length + maxNewTokens];
        history[0] = ByteTokenizer.BOS;
        System.arraycopy(prefix, 0, history, 1, prefix.length);
        int length = prefix.length + 1;
        int[] generated = new int[maxNewTokens];
        int count = 0;
        Random random = new Random(seed);
        int contextLength = bundle.model().config().contextLength();
        for (int i = 0; i < maxNewTokens; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Geração cancelada.");
            }
            int[] context = Arrays.copyOfRange(history, Math.max(0, length - contextLength), length);
            int next = sample(bundle.model().nextLogits(context), temperature, topK, random);
            if (next == ByteTokenizer.EOS) break;
            generated[count++] = next;
            history[length++] = next;
        }
        return Arrays.copyOf(generated, count);
    }

    private static int sample(double[] logits, double temperature, int topK, Random random) {
        for (double logit : logits) {
            if (!Double.isFinite(logit)) throw new ArithmeticException("Non-finite generation logits");
        }
        Integer[] candidates = new Integer[logits.length - 2];
        int offset = 0;
        for (int token = 0; token < logits.length; token++) {
            if (token != ByteTokenizer.PAD && token != ByteTokenizer.BOS) candidates[offset++] = token;
        }
        Arrays.sort(candidates, Comparator.<Integer>comparingDouble(token -> logits[token])
                .reversed().thenComparingInt(Integer::intValue));
        if (temperature == 0 || topK == 1) return candidates[0];

        int count = Math.min(topK, candidates.length);
        double maximum = logits[candidates[0]];
        double[] probabilities = new double[count];
        double total = 0;
        for (int i = 0; i < count; i++) {
            probabilities[i] = Math.exp((logits[candidates[i]] - maximum) / temperature);
            total += probabilities[i];
        }
        double target = random.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < count; i++) {
            cumulative += probabilities[i];
            if (target < cumulative) return candidates[i];
        }
        // Rounding at the final cumulative sum still selects a valid top-K ID.
        return candidates[count - 1];
    }
}
