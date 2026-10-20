package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdamWTest {
    @Test
    void overflowingGlobalNormNeverEnlargesIndividuallySafeGradients() {
        Transformer.Parameter parameter = new Transformer.Parameter("large-vector", 32, false);
        Arrays.fill(parameter.gradient, 3e153);
        // The real norm is greater than sqrt(MAX_VALUE), but much less than this clip.
        // Each individual squared moment remains representable without scaling.
        AdamW optimizer = new AdamW(0.01, 0, 1e300);
        assertDoesNotThrow(() -> optimizer.update(List.of(parameter)));
        assertEquals(1, optimizer.step());
        for (double value : parameter.value) assertEquals(-0.01, value, 1e-14);
    }

    @Test
    void unsafeEffectiveGradientsAreRejectedBeforeAnyWeightOrMomentChanges() {
        Transformer.Parameter parameter = new Transformer.Parameter("unsafe", 2, true);
        parameter.value[0] = 7;
        parameter.value[1] = 11;
        parameter.gradient[0] = 1;
        parameter.gradient[1] = 1e200;
        AdamW optimizer = new AdamW(0.01, 0.1, 1e300);
        ArithmeticException failure = assertThrows(ArithmeticException.class,
                () -> optimizer.update(List.of(parameter)));
        assertTrue(failure.getMessage().contains("finite Adam moments"));
        assertEquals(0, optimizer.step());
        assertArrayEquals(new double[]{7, 11}, parameter.value);

        // A later valid step must behave exactly like a fresh optimizer.
        Transformer.Parameter fresh = new Transformer.Parameter("fresh", 2, true);
        System.arraycopy(parameter.value, 0, fresh.value, 0, 2);
        parameter.gradient[1] = -2;
        System.arraycopy(parameter.gradient, 0, fresh.gradient, 0, 2);
        optimizer.update(List.of(parameter));
        new AdamW(0.01, 0.1, 1e300).update(List.of(fresh));
        assertArrayEquals(fresh.value, parameter.value);
        assertEquals(1, optimizer.step());
    }

    @Test
    void hugeRawGradientsStillWorkWithOrdinaryClipping() {
        Transformer.Parameter parameter = new Transformer.Parameter("clipped", 2, false);
        parameter.gradient[0] = Double.MAX_VALUE / 4;
        parameter.gradient[1] = -Double.MAX_VALUE / 8;
        AdamW optimizer = new AdamW(0.01, 0, 1);
        optimizer.update(List.of(parameter));
        assertEquals(-0.01, parameter.value[0], 1e-8);
        assertEquals(0.01, parameter.value[1], 1e-8);
    }

    @Test
    void underflowingSquaredNormStillClipsTinyGradients() {
        Transformer.Parameter parameter = new Transformer.Parameter("tiny", 1, false);
        parameter.gradient[0] = 1e-170;
        new AdamW(0.01, 0, 1e-200).update(List.of(parameter));
        // The second moment underflows to zero; epsilon controls this first update.
        assertEquals(-1e-194, parameter.value[0], 1e-207);
    }
}
