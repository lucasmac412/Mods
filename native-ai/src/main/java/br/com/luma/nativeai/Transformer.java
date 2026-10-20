package br.com.luma.nativeai;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A causal decoder Transformer implemented with Java arrays, including reverse-mode
 * differentiation. Pre-LayerNorm, learned positions, multi-head attention, GELU,
 * residual connections, and a separate vocabulary projection are trained from scratch.
 *
 * Public operations synchronize on this model: inference waits for an ongoing update,
 * so a caller cannot observe half-updated weights. There is no GPU or external runtime.
 * A checkpoint contains weights only; AdamW moments are intentionally not serialized.
 */
public final class Transformer {
    private static final long WEIGHT_MAGIC = 0x4c554d4154463031L; // LUMATF01
    private static final int WEIGHT_VERSION = 1;
    private static final double NORM_EPSILON = 1e-5;
    private static final double GELU_FACTOR = Math.sqrt(2 / Math.PI);
    private final ModelConfig config;
    private final List<Parameter> parameters = new ArrayList<>();
    private final Parameter tokens;
    private final Parameter positions;
    private final Block[] blocks;
    private final Norm finalNorm;
    private final Parameter outputWeight;
    private final Parameter outputBias;

    public Transformer(ModelConfig config, long seed) {
        this.config = Objects.requireNonNull(config, "config");
        Random random = new Random(seed);
        int d = config.embeddingSize();
        tokens = matrix("embedding.token", config.vocabSize(), d, random, 0.02);
        positions = matrix("embedding.position", config.contextLength(), d, random, 0.02);
        blocks = new Block[config.layers()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = new Block("block." + i, random);
        finalNorm = new Norm("norm.final", d);
        outputWeight = matrix("output.weight", d, config.vocabSize(), random, 0.02);
        outputBias = zeros("output.bias", config.vocabSize(), false);
        if (parameterCount() != config.parameterCount()) throw new IllegalStateException("parameter shape mismatch");
    }

    public ModelConfig config() { return config; }

    public long parameterCount() {
        long count = 0;
        for (Parameter parameter : parameters) count += parameter.value.length;
        return count;
    }

    /** Mean next-token cross entropy before the update; a target of -1 masks that position. */
    public synchronized double trainBatch(int[][] input, int[][] targets, AdamW optimizer) {
        Objects.requireNonNull(optimizer, "optimizer");
        double result = gradients(input, targets);
        optimizer.update(parameters);
        return result;
    }

    /** Mean next-token cross entropy; does not change weights or accumulated gradients. */
    public synchronized double loss(int[][] input, int[][] targets) {
        int count = validateBatch(input, targets);
        double total = 0;
        for (int i = 0; i < input.length; i++) {
            Sequence sequence = forward(input[i], true);
            total += crossEntropy(sequence.logits, targets[i], null, 0);
        }
        return checkedLoss(total / count);
    }

    /** Raw vocabulary logits for the token following the last context position. */
    public synchronized double[] nextLogits(int[] context) {
        validateContext(context);
        Sequence sequence = forward(context, false);
        int d = config.embeddingSize();
        int vocabulary = config.vocabSize();
        double[] result = Arrays.copyOf(outputBias.value, vocabulary);
        int offset = (context.length - 1) * d;
        for (int in = 0; in < d; in++) {
            double value = sequence.finalNorm.output[offset + in];
            int row = in * vocabulary;
            for (int out = 0; out < vocabulary; out++) result[out] += value * outputWeight.value[row + out];
        }
        for (double value : result) if (!Double.isFinite(value)) throw new ArithmeticException("non-finite inference logits");
        return result;
    }

    /** Versioned, shape-checked tensor stream. It contains no training data or secrets. */
    public synchronized void writeWeights(DataOutput output) throws IOException {
        Objects.requireNonNull(output, "output");
        output.writeLong(WEIGHT_MAGIC);
        output.writeInt(WEIGHT_VERSION);
        writeConfig(output, config);
        output.writeInt(parameters.size());
        for (Parameter parameter : parameters) {
            output.writeUTF(parameter.name);
            output.writeInt(parameter.value.length);
            for (double value : parameter.value) {
                if (!Double.isFinite(value)) throw new IOException("non-finite weight: " + parameter.name);
                output.writeDouble(value);
            }
        }
    }

    /** Rejects unknown formats, mismatched shapes, truncated streams, and NaN/Infinity. */
    public static Transformer readWeights(ModelConfig config, DataInput input) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(input, "input");
        if (input.readLong() != WEIGHT_MAGIC) throw new IOException("invalid Transformer weight signature");
        if (input.readInt() != WEIGHT_VERSION) throw new IOException("unsupported Transformer weight version");
        if (input.readInt() != config.vocabSize() || input.readInt() != config.contextLength()
                || input.readInt() != config.embeddingSize() || input.readInt() != config.heads()
                || input.readInt() != config.layers() || input.readInt() != config.feedForwardSize())
            throw new IOException("checkpoint architecture does not match the requested model");
        Transformer model = new Transformer(config, 0);
        if (input.readInt() != model.parameters.size()) throw new IOException("checkpoint tensor count mismatch");
        for (Parameter parameter : model.parameters) {
            if (!input.readUTF().equals(parameter.name)) throw new IOException("checkpoint tensor name mismatch");
            if (input.readInt() != parameter.value.length) throw new IOException("checkpoint tensor size mismatch");
            for (int i = 0; i < parameter.value.length; i++) {
                double value = input.readDouble();
                if (!Double.isFinite(value)) throw new IOException("non-finite checkpoint weight: " + parameter.name);
                parameter.value[i] = value;
            }
        }
        return model;
    }

    private static void writeConfig(DataOutput output, ModelConfig config) throws IOException {
        output.writeInt(config.vocabSize());
        output.writeInt(config.contextLength());
        output.writeInt(config.embeddingSize());
        output.writeInt(config.heads());
        output.writeInt(config.layers());
        output.writeInt(config.feedForwardSize());
    }

    private double gradients(int[][] input, int[][] targets) {
        int count = validateBatch(input, targets);
        for (Parameter parameter : parameters) Arrays.fill(parameter.gradient, 0);
        double total = 0;
        for (int i = 0; i < input.length; i++) {
            Sequence sequence = forward(input[i], true);
            double[] logitGradient = new double[sequence.logits.length];
            total += crossEntropy(sequence.logits, targets[i], logitGradient, 1.0 / count);
            backward(input[i], sequence, logitGradient);
        }
        return checkedLoss(total / count);
    }

    private Sequence forward(int[] input, boolean computeLogits) {
        int length = input.length;
        int d = config.embeddingSize();
        double[] hidden = new double[length * d];
        for (int t = 0; t < length; t++) {
            int tokenOffset = input[t] * d;
            int offset = t * d;
            for (int i = 0; i < d; i++) hidden[offset + i] = tokens.value[tokenOffset + i] + positions.value[offset + i];
        }
        LayerCache[] caches = new LayerCache[blocks.length];
        for (int layer = 0; layer < blocks.length; layer++) {
            Block block = blocks[layer];
            NormCache norm1 = normForward(hidden, length, block.norm1);
            double[] query = linear(norm1.output, length, d, d, block.queryWeight, block.queryBias);
            double[] key = linear(norm1.output, length, d, d, block.keyWeight, block.keyBias);
            double[] value = linear(norm1.output, length, d, d, block.valueWeight, block.valueBias);
            Attention attention = attentionForward(query, key, value, length);
            double[] attentionOutput = linear(attention.joined, length, d, d, block.attentionWeight, block.attentionBias);
            double[] residual = add(hidden, attentionOutput);
            NormCache norm2 = normForward(residual, length, block.norm2);
            double[] ffPre = linear(norm2.output, length, d, config.feedForwardSize(), block.ff1Weight, block.ff1Bias);
            double[] ffActivated = new double[ffPre.length];
            for (int i = 0; i < ffPre.length; i++) ffActivated[i] = gelu(ffPre[i]);
            double[] ffOutput = linear(ffActivated, length, config.feedForwardSize(), d, block.ff2Weight, block.ff2Bias);
            hidden = add(residual, ffOutput);
            caches[layer] = new LayerCache(norm1, query, key, value, attention.probabilities,
                    attention.joined, norm2, ffPre, ffActivated);
        }
        NormCache last = normForward(hidden, length, finalNorm);
        double[] logits = computeLogits ? linear(last.output, length, d, config.vocabSize(), outputWeight, outputBias) : null;
        return new Sequence(caches, last, logits);
    }

    private void backward(int[] input, Sequence sequence, double[] logitGradient) {
        int length = input.length;
        int d = config.embeddingSize();
        double[] hiddenGradient = linearBackward(sequence.finalNorm.output, logitGradient, length, d,
                config.vocabSize(), outputWeight, outputBias);
        hiddenGradient = normBackward(sequence.finalNorm, hiddenGradient, length, finalNorm);
        for (int layer = blocks.length - 1; layer >= 0; layer--) {
            Block block = blocks[layer];
            LayerCache cache = sequence.layers[layer];
            double[] ffGradient = linearBackward(cache.ffActivated, hiddenGradient, length,
                    config.feedForwardSize(), d, block.ff2Weight, block.ff2Bias);
            for (int i = 0; i < ffGradient.length; i++) ffGradient[i] *= geluDerivative(cache.ffPre[i]);
            double[] norm2Gradient = linearBackward(cache.norm2.output, ffGradient, length,
                    d, config.feedForwardSize(), block.ff1Weight, block.ff1Bias);
            double[] residualGradient = add(hiddenGradient, normBackward(cache.norm2, norm2Gradient, length, block.norm2));
            double[] joinedGradient = linearBackward(cache.joined, residualGradient, length,
                    d, d, block.attentionWeight, block.attentionBias);
            AttentionGradient attentionGradient = attentionBackward(cache, joinedGradient, length);
            double[] norm1Gradient = linearBackward(cache.norm1.output, attentionGradient.query, length,
                    d, d, block.queryWeight, block.queryBias);
            addInPlace(norm1Gradient, linearBackward(cache.norm1.output, attentionGradient.key, length,
                    d, d, block.keyWeight, block.keyBias));
            addInPlace(norm1Gradient, linearBackward(cache.norm1.output, attentionGradient.value, length,
                    d, d, block.valueWeight, block.valueBias));
            hiddenGradient = add(residualGradient, normBackward(cache.norm1, norm1Gradient, length, block.norm1));
        }
        for (int t = 0; t < length; t++) {
            int tokenOffset = input[t] * d;
            int offset = t * d;
            for (int i = 0; i < d; i++) {
                tokens.gradient[tokenOffset + i] += hiddenGradient[offset + i];
                positions.gradient[offset + i] += hiddenGradient[offset + i];
            }
        }
    }

    private Attention attentionForward(double[] query, double[] key, double[] value, int length) {
        int d = config.embeddingSize();
        int headSize = d / config.heads();
        double scale = 1.0 / Math.sqrt(headSize);
        double[] probabilities = new double[config.heads() * length * length];
        double[] joined = new double[length * d];
        for (int head = 0; head < config.heads(); head++) {
            int headOffset = head * headSize;
            for (int t = 0; t < length; t++) {
                int queryOffset = t * d + headOffset;
                int row = (head * length + t) * length;
                double maximum = Double.NEGATIVE_INFINITY;
                for (int source = 0; source <= t; source++) {
                    int keyOffset = source * d + headOffset;
                    double score = 0;
                    for (int i = 0; i < headSize; i++) score += query[queryOffset + i] * key[keyOffset + i];
                    score *= scale;
                    probabilities[row + source] = score;
                    maximum = Math.max(maximum, score);
                }
                double sum = 0;
                for (int source = 0; source <= t; source++) {
                    double exponential = Math.exp(probabilities[row + source] - maximum);
                    probabilities[row + source] = exponential;
                    sum += exponential;
                }
                for (int source = 0; source <= t; source++) {
                    double probability = probabilities[row + source] / sum;
                    probabilities[row + source] = probability;
                    int valueOffset = source * d + headOffset;
                    for (int i = 0; i < headSize; i++) joined[queryOffset + i] += probability * value[valueOffset + i];
                }
            }
        }
        return new Attention(joined, probabilities);
    }

    private AttentionGradient attentionBackward(LayerCache cache, double[] joinedGradient, int length) {
        int d = config.embeddingSize();
        int headSize = d / config.heads();
        double scale = 1.0 / Math.sqrt(headSize);
        double[] queryGradient = new double[length * d];
        double[] keyGradient = new double[length * d];
        double[] valueGradient = new double[length * d];
        double[] probabilityGradient = new double[length];
        for (int head = 0; head < config.heads(); head++) {
            int headOffset = head * headSize;
            for (int t = 0; t < length; t++) {
                int queryOffset = t * d + headOffset;
                int row = (head * length + t) * length;
                double weightedGradient = 0;
                for (int source = 0; source <= t; source++) {
                    int valueOffset = source * d + headOffset;
                    double gradient = 0;
                    double probability = cache.probabilities[row + source];
                    for (int i = 0; i < headSize; i++) {
                        gradient += joinedGradient[queryOffset + i] * cache.value[valueOffset + i];
                        valueGradient[valueOffset + i] += probability * joinedGradient[queryOffset + i];
                    }
                    probabilityGradient[source] = gradient;
                    weightedGradient += probability * gradient;
                }
                for (int source = 0; source <= t; source++) {
                    int keyOffset = source * d + headOffset;
                    double scoreGradient = cache.probabilities[row + source]
                            * (probabilityGradient[source] - weightedGradient) * scale;
                    for (int i = 0; i < headSize; i++) {
                        queryGradient[queryOffset + i] += scoreGradient * cache.key[keyOffset + i];
                        keyGradient[keyOffset + i] += scoreGradient * cache.query[queryOffset + i];
                    }
                }
            }
        }
        return new AttentionGradient(queryGradient, keyGradient, valueGradient);
    }

    private NormCache normForward(double[] input, int length, Norm norm) {
        int d = config.embeddingSize();
        double[] normalized = new double[input.length];
        double[] output = new double[input.length];
        double[] inverseStd = new double[length];
        for (int t = 0; t < length; t++) {
            int row = t * d;
            double mean = 0;
            for (int i = 0; i < d; i++) mean += input[row + i];
            mean /= d;
            double variance = 0;
            for (int i = 0; i < d; i++) {
                double centered = input[row + i] - mean;
                variance += centered * centered;
            }
            double inverse = 1.0 / Math.sqrt(variance / d + NORM_EPSILON);
            inverseStd[t] = inverse;
            for (int i = 0; i < d; i++) {
                double value = (input[row + i] - mean) * inverse;
                normalized[row + i] = value;
                output[row + i] = value * norm.scale.value[i] + norm.bias.value[i];
            }
        }
        return new NormCache(normalized, output, inverseStd);
    }

    private double[] normBackward(NormCache cache, double[] gradient, int length, Norm norm) {
        int d = config.embeddingSize();
        double[] result = new double[gradient.length];
        for (int t = 0; t < length; t++) {
            int row = t * d;
            double sum = 0;
            double normalizedSum = 0;
            for (int i = 0; i < d; i++) {
                double normalized = cache.normalized[row + i];
                double value = gradient[row + i];
                norm.scale.gradient[i] += value * normalized;
                norm.bias.gradient[i] += value;
                double normalizedGradient = value * norm.scale.value[i];
                sum += normalizedGradient;
                normalizedSum += normalizedGradient * normalized;
            }
            double factor = cache.inverseStd[t] / d;
            for (int i = 0; i < d; i++) {
                double normalizedGradient = gradient[row + i] * norm.scale.value[i];
                result[row + i] = factor * (d * normalizedGradient - sum
                        - cache.normalized[row + i] * normalizedSum);
            }
        }
        return result;
    }

    private static double[] linear(double[] input, int length, int inputs, int outputs, Parameter weight, Parameter bias) {
        double[] result = new double[length * outputs];
        for (int t = 0; t < length; t++) {
            int outRow = t * outputs;
            System.arraycopy(bias.value, 0, result, outRow, outputs);
            for (int in = 0; in < inputs; in++) {
                double value = input[t * inputs + in];
                int weightRow = in * outputs;
                for (int out = 0; out < outputs; out++) result[outRow + out] += value * weight.value[weightRow + out];
            }
        }
        return result;
    }

    private static double[] linearBackward(double[] input, double[] gradient, int length,
                                            int inputs, int outputs, Parameter weight, Parameter bias) {
        double[] result = new double[length * inputs];
        for (int t = 0; t < length; t++) {
            int outRow = t * outputs;
            int inRow = t * inputs;
            for (int out = 0; out < outputs; out++) bias.gradient[out] += gradient[outRow + out];
            for (int in = 0; in < inputs; in++) {
                double value = input[inRow + in];
                int weightRow = in * outputs;
                double inputGradient = 0;
                for (int out = 0; out < outputs; out++) {
                    double upstream = gradient[outRow + out];
                    inputGradient += weight.value[weightRow + out] * upstream;
                    weight.gradient[weightRow + out] += value * upstream;
                }
                result[inRow + in] = inputGradient;
            }
        }
        return result;
    }

    private double crossEntropy(double[] logits, int[] targets, double[] gradient, double scale) {
        int vocabulary = config.vocabSize();
        double total = 0;
        for (int t = 0; t < targets.length; t++) {
            if (targets[t] == -1) continue;
            int row = t * vocabulary;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < vocabulary; i++) maximum = Math.max(maximum, logits[row + i]);
            double sum = 0;
            for (int i = 0; i < vocabulary; i++) sum += Math.exp(logits[row + i] - maximum);
            total += maximum + Math.log(sum) - logits[row + targets[t]];
            if (gradient != null) {
                for (int i = 0; i < vocabulary; i++) gradient[row + i] = Math.exp(logits[row + i] - maximum) / sum * scale;
                gradient[row + targets[t]] -= scale;
            }
        }
        return total;
    }

    private int validateBatch(int[][] input, int[][] targets) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(targets, "targets");
        if (input.length == 0 || input.length > 256 || input.length != targets.length)
            throw new IllegalArgumentException("batch must contain 1 to 256 matching input/target sequences");
        int count = 0;
        for (int i = 0; i < input.length; i++) {
            validateContext(input[i]);
            if (targets[i] == null || targets[i].length != input[i].length)
                throw new IllegalArgumentException("each target sequence must match its input length");
            for (int token : targets[i]) {
                if (token == -1) continue;
                validateToken(token);
                count++;
            }
        }
        if (count == 0) throw new IllegalArgumentException("batch must contain at least one unmasked target");
        return count;
    }

    private void validateContext(int[] context) {
        Objects.requireNonNull(context, "context");
        if (context.length == 0 || context.length > config.contextLength())
            throw new IllegalArgumentException("context length must be between 1 and " + config.contextLength());
        for (int token : context) validateToken(token);
    }

    private void validateToken(int token) {
        if (token < 0 || token >= config.vocabSize()) throw new IllegalArgumentException("token outside vocabulary: " + token);
    }

    private static double checkedLoss(double loss) {
        if (!Double.isFinite(loss)) throw new ArithmeticException("non-finite cross-entropy loss");
        return loss;
    }

    private static double[] add(double[] first, double[] second) {
        double[] result = first.clone();
        addInPlace(result, second);
        return result;
    }

    private static void addInPlace(double[] first, double[] second) {
        for (int i = 0; i < first.length; i++) first[i] += second[i];
    }

    private static double gelu(double x) {
        if (x > 10) return x;
        if (x < -10) return 0;
        return 0.5 * x * (1 + Math.tanh(GELU_FACTOR * (x + 0.044715 * x * x * x)));
    }

    private static double geluDerivative(double x) {
        if (x > 10) return 1;
        if (x < -10) return 0;
        double tanh = Math.tanh(GELU_FACTOR * (x + 0.044715 * x * x * x));
        return 0.5 * (1 + tanh) + 0.5 * x * (1 - tanh * tanh) * GELU_FACTOR * (1 + 3 * 0.044715 * x * x);
    }

    private Parameter matrix(String name, int inputs, int outputs, Random random, double deviation) {
        Parameter result = zeros(name, Math.multiplyExact(inputs, outputs), true);
        for (int i = 0; i < result.value.length; i++) result.value[i] = random.nextGaussian() * deviation;
        return result;
    }

    private Parameter zeros(String name, int length, boolean decay) {
        Parameter parameter = new Parameter(name, length, decay);
        parameters.add(parameter);
        return parameter;
    }

    // Package-private numerical diagnostics, inaccessible to application consumers.
    List<Parameter> parametersForTesting() { return List.copyOf(parameters); }
    synchronized double gradientsForTesting(int[][] input, int[][] targets) { return gradients(input, targets); }
    synchronized double[] logitsForTesting(int[] input) {
        validateContext(input);
        return forward(input, true).logits;
    }

    static final class Parameter {
        final String name;
        final double[] value;
        final double[] gradient;
        final boolean decay;
        Parameter(String name, int length, boolean decay) {
            this.name = name;
            value = new double[length];
            gradient = new double[length];
            this.decay = decay;
        }
    }

    private final class Norm {
        final Parameter scale;
        final Parameter bias;
        Norm(String prefix, int size) {
            scale = zeros(prefix + ".scale", size, false);
            Arrays.fill(scale.value, 1);
            bias = zeros(prefix + ".bias", size, false);
        }
    }

    private final class Block {
        final Norm norm1;
        final Norm norm2;
        final Parameter queryWeight, queryBias, keyWeight, keyBias, valueWeight, valueBias;
        final Parameter attentionWeight, attentionBias, ff1Weight, ff1Bias, ff2Weight, ff2Bias;
        Block(String prefix, Random random) {
            int d = config.embeddingSize();
            int f = config.feedForwardSize();
            norm1 = new Norm(prefix + ".norm1", d);
            queryWeight = matrix(prefix + ".query.weight", d, d, random, 0.02);
            queryBias = zeros(prefix + ".query.bias", d, false);
            keyWeight = matrix(prefix + ".key.weight", d, d, random, 0.02);
            keyBias = zeros(prefix + ".key.bias", d, false);
            valueWeight = matrix(prefix + ".value.weight", d, d, random, 0.02);
            valueBias = zeros(prefix + ".value.bias", d, false);
            double residualDeviation = 0.02 / Math.sqrt(2 * config.layers());
            attentionWeight = matrix(prefix + ".attention.weight", d, d, random, residualDeviation);
            attentionBias = zeros(prefix + ".attention.bias", d, false);
            norm2 = new Norm(prefix + ".norm2", d);
            ff1Weight = matrix(prefix + ".ff1.weight", d, f, random, 0.02);
            ff1Bias = zeros(prefix + ".ff1.bias", f, false);
            ff2Weight = matrix(prefix + ".ff2.weight", f, d, random, residualDeviation);
            ff2Bias = zeros(prefix + ".ff2.bias", d, false);
        }
    }

    private record NormCache(double[] normalized, double[] output, double[] inverseStd) {}
    private record Attention(double[] joined, double[] probabilities) {}
    private record AttentionGradient(double[] query, double[] key, double[] value) {}
    private record LayerCache(NormCache norm1, double[] query, double[] key, double[] value,
                              double[] probabilities, double[] joined, NormCache norm2,
                              double[] ffPre, double[] ffActivated) {}
    private record Sequence(LayerCache[] layers, NormCache finalNorm, double[] logits) {}
}
