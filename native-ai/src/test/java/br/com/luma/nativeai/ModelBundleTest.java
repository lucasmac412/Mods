package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelBundleTest {
    @TempDir Path temporary;
    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    void roundTripPreservesLearnedWeightsTokenizerAndProvenance() throws Exception {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("Olá, Luma! Vamos explorar?", "Você está bem?"), 280);
        Transformer model = new Transformer(new ModelConfig(tokenizer.vocabSize(), 32, 8, 2, 1, 16), 53);
        int[][] input = {{ByteTokenizer.BOS, 3 + 'a', 3 + 'b'}};
        int[][] targets = {{-1, 3 + 'b', ByteTokenizer.EOS}};
        AdamW optimizer = new AdamW(0.01, 0.01, 1);
        double[] beforeTraining = model.nextLogits(input[0]);
        for (int step = 0; step < 5; step++) model.trainBatch(input, targets, optimizer);
        double[] expected = model.nextLogits(input[0]);
        assertFalse(Arrays.equals(beforeTraining, expected), "round-trip must exercise updated weights");

        Path file = temporary.resolve("nested/model.luma");
        new ModelBundle(tokenizer, model, SHA.toUpperCase()).save(file);
        ModelBundle loaded = ModelBundle.load(file);
        assertEquals(SHA, loaded.trainingDataSha256());
        assertEquals(model.config(), loaded.model().config());
        assertEquals(model.parameterCount(), loaded.model().parameterCount());
        assertArrayEquals(expected, loaded.model().nextLogits(input[0]), 0);
        String text = "Olá, Luma! Você viu o dragão? 🐉";
        assertArrayEquals(tokenizer.encode(text), loaded.tokenizer().encode(text));
        assertEquals(text, loaded.tokenizer().decode(loaded.tokenizer().encode(text)));
    }

    @Test
    void rejectsCorruptionAndTruncationBeforeUsingTheCheckpoint() throws Exception {
        Path valid = writeFixture();
        byte[] original = Files.readAllBytes(valid);
        byte[] changed = original.clone();
        changed[changed.length / 2] ^= 1;
        Path corrupt = temporary.resolve("corrupt.luma");
        Files.write(corrupt, changed);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(corrupt)).getMessage().contains("SHA-256"));
        Path truncated = temporary.resolve("truncated.luma");
        Files.write(truncated, Arrays.copyOf(original, original.length - 17));
        assertThrows(IOException.class, () -> ModelBundle.load(truncated));
        Files.write(truncated, new byte[3]);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(truncated)).getMessage().contains("between"));
    }

    @Test
    void rejectsUnknownFormatsAndArchitectureMismatchEvenWithValidChecksums() throws Exception {
        byte[] body = body(Files.readAllBytes(writeFixture()));
        byte[] badMagic = body.clone();
        badMagic[0] ^= 1;
        Path bad = writeSigned("magic.luma", badMagic);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(bad)).getMessage().contains("signature"));
        byte[] badVersion = body.clone();
        ByteBuffer.wrap(badVersion).putInt(8, 99);
        Path version = writeSigned("version.luma", badVersion);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(version)).getMessage().contains("version"));

        int configOffset = configOffset(body);
        int vocabulary = ByteBuffer.wrap(body).getInt(configOffset);
        byte[] mismatch = body.clone();
        ByteBuffer.wrap(mismatch).putInt(configOffset, vocabulary + 1);
        Path mismatchFile = writeSigned("vocabulary.luma", mismatch);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(mismatchFile)).getMessage().contains("vocabularies"));

        byte[] invalidConfig = body.clone();
        ByteBuffer.wrap(invalidConfig).putInt(configOffset + Integer.BYTES, 0);
        Path invalid = writeSigned("architecture.luma", invalidConfig);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(invalid)).getMessage().contains("architecture"));
    }

    @Test
    void rejectsAFileClaimingMoreWeightsThanItCanContainBeforeAllocatingThatModel() throws Exception {
        byte[] body = body(Files.readAllBytes(writeFixture()));
        int offset = configOffset(body);
        int vocabulary = ByteBuffer.wrap(body).getInt(offset);
        ModelConfig large = new ModelConfig(vocabulary, 32, 256, 8, 2, 512);
        ByteBuffer buffer = ByteBuffer.wrap(body);
        buffer.putInt(offset + 8, large.embeddingSize());
        buffer.putInt(offset + 12, large.heads());
        buffer.putInt(offset + 16, large.layers());
        buffer.putInt(offset + 20, large.feedForwardSize());
        Path file = writeSigned("false-size.luma", body);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(file)).getMessage().contains("Truncated model bundle weights"));
    }

    @Test
    void rejectsNonFiniteWeightsAndTrailingDataWithValidIntegrityFooters() throws Exception {
        byte[] body = body(Files.readAllBytes(writeFixture()));
        byte[] nonFinite = body.clone();
        ByteBuffer.wrap(nonFinite).putDouble(nonFinite.length - Double.BYTES, Double.NaN);
        Path weights = writeSigned("nan.luma", nonFinite);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(weights)).getMessage().contains("non-finite"));
        byte[] appended = Arrays.copyOf(body, body.length + 1);
        appended[appended.length - 1] = 42;
        Path trailing = writeSigned("trailing.luma", appended);
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(trailing)).getMessage().contains("trailing"));
    }

    @Test
    void fileSizeLimitRejectsOversizedSparseFileBeforeHashingOrParsing() throws Exception {
        Path file = temporary.resolve("oversized.luma");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(128L * 1024 * 1024);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertTrue(assertThrows(IOException.class, () -> ModelBundle.load(file)).getMessage().contains("between"));
    }

    @Test
    void failedSavePreservesExistingCheckpointAndRemovesItsTemporaryFile() throws Exception {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("Oi"), 259);
        Transformer model = new Transformer(new ModelConfig(tokenizer.vocabSize(), 16, 8, 2, 1, 16), 4);
        ModelBundle bundle = new ModelBundle(tokenizer, model, SHA);
        Path file = temporary.resolve("preserved.luma");
        bundle.save(file);
        byte[] original = Files.readAllBytes(file);
        model.parametersForTesting().getFirst().value[0] = Double.POSITIVE_INFINITY;
        assertThrows(IOException.class, () -> bundle.save(file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(SHA, ModelBundle.load(file).trainingDataSha256());
        try (var files = Files.list(temporary)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void constructorRejectsMismatchedVocabulariesAndInvalidCorpusFingerprints() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("Olá"), 259);
        Transformer model = new Transformer(new ModelConfig(260, 16, 8, 2, 1, 16), 1);
        assertThrows(IllegalArgumentException.class, () -> new ModelBundle(tokenizer, model, SHA));
        Transformer matching = new Transformer(new ModelConfig(259, 16, 8, 2, 1, 16), 1);
        assertThrows(IllegalArgumentException.class, () -> new ModelBundle(tokenizer, matching, "not-a-checksum"));
        assertThrows(NullPointerException.class, () -> new ModelBundle(tokenizer, matching, null));
    }

    private Path writeFixture() throws IOException {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("Olá, Luma!", "Vamos explorar juntos."), 270);
        Transformer model = new Transformer(new ModelConfig(tokenizer.vocabSize(), 32, 8, 2, 1, 16), 412);
        Path file = temporary.resolve("fixture.luma");
        new ModelBundle(tokenizer, model, SHA).save(file);
        return file;
    }

    private static byte[] body(byte[] complete) { return Arrays.copyOf(complete, complete.length - 32); }

    private Path writeSigned(String name, byte[] body) throws Exception {
        byte[] data = Arrays.copyOf(body, body.length + 32);
        byte[] checksum = MessageDigest.getInstance("SHA-256").digest(body);
        System.arraycopy(checksum, 0, data, body.length, checksum.length);
        Path file = temporary.resolve(name);
        Files.write(file, data);
        return file;
    }

    private static int configOffset(byte[] body) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(body);
        DataInputStream data = new DataInputStream(stream);
        data.readLong(); data.readInt(); data.skipNBytes(32);
        ByteTokenizer.read(data);
        return body.length - stream.available();
    }
}
