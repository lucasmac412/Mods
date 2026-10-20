package br.com.luma.nativeai;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** A self-contained tokenizer, architecture, learned weights and corpus fingerprint. */
public record ModelBundle(ByteTokenizer tokenizer, Transformer model, String trainingDataSha256) {
    private static final long MAGIC = 0x4c554d41424e4431L; // LUMABND1
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MIN_FILE_BYTES = 8 + 4 + 32 + 12 + 24 + DIGEST_BYTES;

    public ModelBundle {
        Objects.requireNonNull(tokenizer, "tokenizer");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(trainingDataSha256, "trainingDataSha256");
        if (tokenizer.vocabSize() != model.config().vocabSize()) {
            throw new IllegalArgumentException("Tokenizer and model vocabularies must match");
        }
        if (!trainingDataSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Training data SHA-256 must contain exactly 64 hexadecimal digits");
        }
        trainingDataSha256 = trainingDataSha256.toLowerCase(Locale.ROOT);
    }

    /**
     * Writes into a temporary sibling, then atomically replaces the target.
     * SHA-256 detects accidental corruption; it does not authenticate the author.
     * Existing files survive failed serialization or an unsupported atomic move.
     */
    public void save(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path target = path.toAbsolutePath().normalize();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".luma-bundle-", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                OutputStream raw = Channels.newOutputStream(channel);
                MessageDigest digest = sha256();
                OutputStream bounded = new BoundedOutputStream(raw, MAX_FILE_BYTES - DIGEST_BYTES);
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                        new DigestOutputStream(bounded, digest)));
                output.writeLong(MAGIC);
                output.writeInt(VERSION);
                output.write(HexFormat.of().parseHex(trainingDataSha256));
                tokenizer.write(output);
                writeConfig(output, model.config());
                model.writeWeights(output);
                output.flush();
                raw.write(digest.digest());
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    /**
     * Checks file bounds and the full digest before allocating model arrays.
     * Parsing checks the digest again to reject a file changed during loading.
     * Dimensions and the minimum weight byte count are checked before the model
     * is constructed; tensor names, shapes and finite weights are checked by it.
     */
    public static ModelBundle load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < MIN_FILE_BYTES || size > MAX_FILE_BYTES) {
                throw new IOException("Model bundle must be between " + MIN_FILE_BYTES
                        + " and " + MAX_FILE_BYTES + " bytes");
            }
            long bodyLength = size - DIGEST_BYTES;
            byte[] expectedDigest = verifyIntegrity(channel, bodyLength);
            channel.position(0);
            LimitedInputStream limited = new LimitedInputStream(
                    new BufferedInputStream(Channels.newInputStream(channel)), bodyLength);
            MessageDigest digest = sha256();
            DataInputStream input = new DataInputStream(new DigestInputStream(limited, digest));
            if (input.readLong() != MAGIC) throw new IOException("Invalid model bundle signature");
            if (input.readInt() != VERSION) throw new IOException("Unsupported model bundle version");
            byte[] provenance = new byte[DIGEST_BYTES];
            input.readFully(provenance);
            ByteTokenizer tokenizer = ByteTokenizer.read(input);
            ModelConfig config;
            try {
                config = new ModelConfig(input.readInt(), input.readInt(), input.readInt(),
                        input.readInt(), input.readInt(), input.readInt());
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid model bundle architecture", exception);
            }
            if (config.vocabSize() != tokenizer.vocabSize()) {
                throw new IOException("Tokenizer and model vocabularies do not match");
            }
            // Double weights alone require these bytes, before any tensor headers.
            // A tiny file claiming a large, otherwise valid model is rejected here.
            if (config.parameterCount() * Double.BYTES > limited.remaining) {
                throw new IOException("Truncated model bundle weights for declared architecture");
            }
            Transformer model = Transformer.readWeights(config, input);
            if (input.read() != -1) throw new IOException("Unexpected trailing model bundle data");
            if (channel.size() != size || !MessageDigest.isEqual(expectedDigest, digest.digest())) {
                throw new IOException("Model bundle changed while being read");
            }
            return new ModelBundle(tokenizer, model, HexFormat.of().formatHex(provenance));
        }
    }

    private static byte[] verifyIntegrity(FileChannel channel, long bodyLength) throws IOException {
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        long remaining = bodyLength;
        while (remaining > 0) {
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), remaining));
            int count = channel.read(buffer);
            if (count < 0) throw new IOException("Truncated model bundle body");
            if (count == 0) continue;
            digest.update(buffer.array(), 0, count);
            remaining -= count;
        }
        byte[] expected = new byte[DIGEST_BYTES];
        ByteBuffer footer = ByteBuffer.wrap(expected);
        while (footer.hasRemaining()) {
            if (channel.read(footer) < 0) throw new IOException("Truncated model bundle checksum");
        }
        if (!MessageDigest.isEqual(expected, digest.digest())) {
            throw new IOException("Model bundle SHA-256 mismatch");
        }
        return expected;
    }

    private static void writeConfig(DataOutputStream output, ModelConfig config) throws IOException {
        output.writeInt(config.vocabSize());
        output.writeInt(config.contextLength());
        output.writeInt(config.embeddingSize());
        output.writeInt(config.heads());
        output.writeInt(config.layers());
        output.writeInt(config.feedForwardSize());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("Java must provide SHA-256", impossible);
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) return -1;
            int result = in.read();
            if (result < 0) throw new IOException("Truncated model bundle");
            remaining--;
            return result;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int count = in.read(bytes, offset, (int) Math.min(length, remaining));
            if (count < 0) throw new IOException("Truncated model bundle");
            remaining -= count;
            return count;
        }
    }

    private static final class BoundedOutputStream extends FilterOutputStream {
        private long remaining;

        private BoundedOutputStream(OutputStream output, long limit) {
            super(output);
            remaining = limit;
        }

        @Override
        public void write(int value) throws IOException {
            require(1);
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            require(length);
            out.write(bytes, offset, length);
        }

        private void require(int length) throws IOException {
            if (length > remaining) throw new IOException("Model bundle exceeds 128 MiB");
            remaining -= length;
        }
    }
}
