package br.com.luma.nativeai;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Adam with decoupled weight decay and global gradient-norm clipping. */
public final class AdamW {
    private static final double BETA1 = 0.9;
    private static final double BETA2 = 0.999;
    private static final double EPSILON = 1e-8;
    // Keep squared, bias-corrected moments below the finite double range with rounding headroom.
    private static final double MAX_SAFE_GRADIENT = Math.sqrt(Double.MAX_VALUE) / 2;
    private final double learningRate;
    private final double weightDecay;
    private final double gradientClip;
    private final Map<Transformer.Parameter, State> state = new IdentityHashMap<>();
    private long step;

    public AdamW(double learningRate, double weightDecay, double gradientClip) {
        if (!Double.isFinite(learningRate) || learningRate <= 0 || learningRate > 1)
            throw new IllegalArgumentException("learningRate must be finite, greater than 0, and at most 1");
        if (!Double.isFinite(weightDecay) || weightDecay < 0 || weightDecay > 1)
            throw new IllegalArgumentException("weightDecay must be finite and between 0 and 1");
        if (!Double.isFinite(gradientClip) || gradientClip <= 0)
            throw new IllegalArgumentException("gradientClip must be finite and greater than 0");
        this.learningRate = learningRate;
        this.weightDecay = weightDecay;
        this.gradientClip = gradientClip;
    }

    public synchronized long step() { return step; }

    synchronized void update(List<Transformer.Parameter> parameters) {
        // Check the complete gradient before touching any weight or optimizer state.
        double sumSquares = 0;
        double maxAbsolute = 0;
        for (Transformer.Parameter parameter : parameters) {
            for (double gradient : parameter.gradient) {
                if (!Double.isFinite(gradient)) throw new ArithmeticException("non-finite gradient: " + parameter.name);
                sumSquares += gradient * gradient;
                maxAbsolute = Math.max(maxAbsolute, Math.abs(gradient));
            }
        }
        double norm = Math.sqrt(sumSquares);
        double gradientScale;
        if (Double.isInfinite(norm) || (sumSquares < Double.MIN_NORMAL && maxAbsolute > 0)) {
            double scaledSquares = 0;
            for (Transformer.Parameter parameter : parameters) {
                for (double gradient : parameter.gradient) {
                    double scaled = gradient / maxAbsolute;
                    scaledSquares += scaled * scaled;
                }
            }
            gradientScale = Math.min(1, (gradientClip / maxAbsolute) / Math.sqrt(scaledSquares));
        } else {
            gradientScale = norm > gradientClip ? gradientClip / norm : 1;
        }
        if (maxAbsolute * gradientScale > MAX_SAFE_GRADIENT)
            throw new ArithmeticException("gradient is too large for finite Adam moments; lower gradientClip");
        if (step == Long.MAX_VALUE) throw new IllegalStateException("optimizer step overflow");
        step++;
        double correction1 = 1 - Math.pow(BETA1, step);
        double correction2 = 1 - Math.pow(BETA2, step);
        for (Transformer.Parameter parameter : parameters) {
            State moments = state.computeIfAbsent(parameter, p -> new State(p.value.length));
            double decay = parameter.decay ? 1 - learningRate * weightDecay : 1;
            for (int i = 0; i < parameter.value.length; i++) {
                double gradient = parameter.gradient[i] * gradientScale;
                moments.first[i] = BETA1 * moments.first[i] + (1 - BETA1) * gradient;
                moments.second[i] = BETA2 * moments.second[i] + (1 - BETA2) * gradient * gradient;
                double adaptive = (moments.first[i] / correction1)
                        / (Math.sqrt(moments.second[i] / correction2) + EPSILON);
                parameter.value[i] = parameter.value[i] * decay - learningRate * adaptive;
            }
        }
    }

    private static final class State {
        final double[] first;
        final double[] second;
        State(int length) { first = new double[length]; second = new double[length]; }
    }
}
