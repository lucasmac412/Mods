package br.com.luma.nativeai;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A byte-level BPE tokenizer learned only from the supplied text.
 *
 * <p>No case folding or Unicode normalization is performed. Every UTF-8 byte has
 * a permanent token, so unseen Portuguese words, emoji and punctuation can be
 * encoded without an unknown-token fallback. Special tokens are supplied by the
 * caller rather than inserted into encoded text.</p>
 */
public final class ByteTokenizer {
    public static final int PAD = 0;
    public static final int BOS = 1;
    public static final int EOS = 2;

    private static final int BYTE_OFFSET = 3;
    private static final int BASE_VOCAB_SIZE = BYTE_OFFSET + 256;
    private static final int MAGIC = 0x4c425045; // LBPE
    private static final int VERSION = 1;
    private static final int MAX_MERGES = 8192;
    // Bound expansion of malicious checkpoints, including repeated doubling.
    private static final int MAX_TOKEN_BYTES = 65_536;
    private static final int MAX_VOCAB_BYTES = 16 * 1024 * 1024;

    private final int[] left;
    private final int[] right;
    private final byte[][] pieces;

    private ByteTokenizer(int[] left, int[] right, byte[][] pieces) {
        this.left = left;
        this.right = right;
        this.pieces = pieces;
    }

    /**
     * Learns frequent adjacent byte pairs, independently inside each text.
     * Equal counts are resolved by ascending left ID, then ascending right ID.
     * A pair seen only once is not added, so the result may be smaller than the
     * requested vocabulary. The minimum possible vocabulary contains 259 IDs.
     */
    public static ByteTokenizer train(List<String> texts, int maxVocabSize) {
        Objects.requireNonNull(texts, "texts");
        if (maxVocabSize < BASE_VOCAB_SIZE
                || maxVocabSize > BASE_VOCAB_SIZE + MAX_MERGES) {
            throw new IllegalArgumentException("Vocabulary must be between "
                    + BASE_VOCAB_SIZE + " and " + (BASE_VOCAB_SIZE + MAX_MERGES));
        }
        List<int[]> sequences = new ArrayList<>(texts.size());
        for (String text : texts) {
            sequences.add(byteIds(Objects.requireNonNull(text, "training text")));
        }

        int capacity = maxVocabSize - BASE_VOCAB_SIZE;
        int[] left = new int[capacity];
        int[] right = new int[capacity];
        byte[][] pieces = initialPieces(maxVocabSize);
        int mergeCount = 0;
        int vocabularyBytes = 256;
        while (mergeCount < capacity) {
            Map<Long, Integer> counts = new HashMap<>();
            for (int[] sequence : sequences) {
                for (int i = 0; i + 1 < sequence.length; i++) {
                    counts.merge(pair(sequence[i], sequence[i + 1]), 1, Integer::sum);
                }
            }

            long bestPair = Long.MAX_VALUE;
            int bestCount = 1;
            for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                long candidate = entry.getKey();
                int a = first(candidate);
                int b = second(candidate);
                int length = pieces[a].length + pieces[b].length;
                if (length > MAX_TOKEN_BYTES || length > MAX_VOCAB_BYTES - vocabularyBytes) {
                    continue;
                }
                int count = entry.getValue();
                if (count > bestCount || (count == bestCount && count > 1 && candidate < bestPair)) {
                    bestPair = candidate;
                    bestCount = count;
                }
            }
            if (bestCount < 2) {
                break;
            }

            int a = first(bestPair);
            int b = second(bestPair);
            int newId = BASE_VOCAB_SIZE + mergeCount;
            left[mergeCount] = a;
            right[mergeCount] = b;
            pieces[newId] = concatenate(pieces[a], pieces[b]);
            vocabularyBytes += pieces[newId].length;
            for (int i = 0; i < sequences.size(); i++) {
                sequences.set(i, merge(sequences.get(i), a, b, newId));
            }
            mergeCount++;
        }
        return new ByteTokenizer(Arrays.copyOf(left, mergeCount),
                Arrays.copyOf(right, mergeCount),
                Arrays.copyOf(pieces, BASE_VOCAB_SIZE + mergeCount));
    }

    public int[] encode(String text) {
        int[] sequence = byteIds(Objects.requireNonNull(text, "text"));
        // Rules are ordered by rank. A later merge cannot introduce a pair
        // with an earlier rank: its newly produced ID did not exist then.
        for (int i = 0; i < left.length && sequence.length > 1; i++) {
            sequence = merge(sequence, left[i], right[i], BASE_VOCAB_SIZE + i);
        }
        return sequence;
    }

    /** Decodes UTF-8, ignoring PAD/BOS/EOS and rejecting all other invalid IDs. */
    public String decode(int[] tokens) {
        Objects.requireNonNull(tokens, "tokens");
        long length = 0;
        for (int token : tokens) {
            if (token < 0 || token >= pieces.length) {
                throw new IllegalArgumentException("Invalid token ID: " + token);
            }
            if (token >= BYTE_OFFSET) {
                length += pieces[token].length;
                if (length > Integer.MAX_VALUE - 8) {
                    throw new IllegalArgumentException("Decoded text is too large");
                }
            }
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        for (int token : tokens) {
            if (token >= BYTE_OFFSET) {
                byte[] piece = pieces[token];
                System.arraycopy(piece, 0, bytes, offset, piece.length);
                offset += piece.length;
            }
        }
        // Generation may end inside a multibyte character; Java replaces an
        // incomplete sequence, while complete encoded strings round-trip.
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int vocabSize() {
        return pieces.length;
    }

    /** Writes the complete learned rules; no external vocabulary is needed. */
    public void write(DataOutput output) throws IOException {
        Objects.requireNonNull(output, "output");
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeInt(left.length);
        for (int i = 0; i < left.length; i++) {
            output.writeInt(left[i]);
            output.writeInt(right[i]);
        }
    }

    public static ByteTokenizer read(DataInput input) throws IOException {
        Objects.requireNonNull(input, "input");
        if (input.readInt() != MAGIC) {
            throw new IOException("Invalid tokenizer magic");
        }
        if (input.readInt() != VERSION) {
            throw new IOException("Unsupported tokenizer version");
        }
        int count = input.readInt();
        if (count < 0 || count > MAX_MERGES) {
            throw new IOException("Invalid tokenizer merge count: " + count);
        }
        int[] left = new int[count];
        int[] right = new int[count];
        byte[][] pieces = initialPieces(BASE_VOCAB_SIZE + count);
        Set<Long> seen = new HashSet<>();
        int vocabularyBytes = 256;
        for (int i = 0; i < count; i++) {
            int a = input.readInt();
            int b = input.readInt();
            int nextId = BASE_VOCAB_SIZE + i;
            if (a < BYTE_OFFSET || b < BYTE_OFFSET || a >= nextId || b >= nextId) {
                throw new IOException("Merge " + i + " refers to a special or unavailable token");
            }
            if (!seen.add(pair(a, b))) {
                throw new IOException("Duplicate tokenizer merge at " + i);
            }
            int length = pieces[a].length + pieces[b].length;
            if (length > MAX_TOKEN_BYTES || length > MAX_VOCAB_BYTES - vocabularyBytes) {
                throw new IOException("Tokenizer byte expansion exceeds its limit");
            }
            left[i] = a;
            right[i] = b;
            pieces[nextId] = concatenate(pieces[a], pieces[b]);
            vocabularyBytes += length;
        }
        return new ByteTokenizer(left, right, pieces);
    }

    private static byte[][] initialPieces(int size) {
        byte[][] pieces = new byte[size][];
        for (int i = 0; i < 256; i++) {
            pieces[BYTE_OFFSET + i] = new byte[] {(byte) i};
        }
        return pieces;
    }

    private static int[] byteIds(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int[] result = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = BYTE_OFFSET + Byte.toUnsignedInt(bytes[i]);
        }
        return result;
    }

    private static int[] merge(int[] sequence, int a, int b, int resultId) {
        int[] output = null;
        int offset = 0;
        for (int i = 0; i < sequence.length; i++) {
            if (i + 1 < sequence.length && sequence[i] == a && sequence[i + 1] == b) {
                if (output == null) {
                    output = new int[sequence.length - 1];
                    System.arraycopy(sequence, 0, output, 0, i);
                    offset = i;
                }
                output[offset++] = resultId;
                i++;
            } else if (output != null) {
                output[offset++] = sequence[i];
            }
        }
        return output == null ? sequence : Arrays.copyOf(output, offset);
    }

    private static byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static long pair(int a, int b) {
        return ((long) a << 32) | (b & 0xffff_ffffL);
    }

    private static int first(long pair) {
        return (int) (pair >>> 32);
    }

    private static int second(long pair) {
        return (int) pair;
    }
}
