package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteTokenizerTest {
    private static final List<String> CORPUS = List.of(
            "Olá, Luma! Vamos construir uma casa? 🏠\nSim, vamos construir!",
            "Olá, Luma! Você pode me seguir?\nSim, posso seguir você.",
            "Minere carvão e madeira. Prepare uma picareta de pedra.",
            "Vamos construir uma casa de madeira para nós dois.");

    @Test
    void roundTripsPortugueseEmojiCaseWhitespaceAndUnseenUnicode() {
        ByteTokenizer tokenizer = ByteTokenizer.train(CORPUS, 384);
        for (String text : List.of("", "Olá! AÇÃO ação\n\r\t  fim ",
                "🧑🏽‍🚀🏠💙 你好 नमस्ते", "e\u0301 é E\u0301 É", "\u0000\u0001\u007f")) {
            assertEquals(text, tokenizer.decode(tokenizer.encode(text)));
        }
        assertTrue(tokenizer.vocabSize() > 259);
        assertTrue(tokenizer.vocabSize() <= 384);
    }

    @Test
    void baseVocabularyKeepsAllByteIdsAndDoesNotInsertSpecialTokens() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of(), 259);
        String text = "Olá 🏠";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        int[] tokens = tokenizer.encode(text);
        assertEquals(utf8.length, tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            assertEquals(3 + Byte.toUnsignedInt(utf8[i]), tokens[i]);
        }
        assertEquals(259, tokenizer.vocabSize());
        assertArrayEquals(new int[0], tokenizer.encode(""));
    }

    @Test
    void resolvesEqualFrequencyPairsDeterministically() throws IOException {
        ByteTokenizer first = ByteTokenizer.train(List.of("zzabzzab", "xyxy"), 260);
        ByteTokenizer reordered = ByteTokenizer.train(List.of("xyxy", "zzabzzab"), 260);
        // All common pairs have count two; 'ab' has the smallest left ID.
        assertArrayEquals(new int[] {259}, first.encode("ab"));
        assertArrayEquals(serialize(first), serialize(reordered));
        assertArrayEquals(serialize(ByteTokenizer.train(CORPUS, 384)),
                serialize(ByteTokenizer.train(CORPUS, 384)));
    }

    @Test
    void appliesOverlappingMergesLeftToRightAndHonorsTheirRank() throws IOException {
        ByteTokenizer tokenizer = read(raw(2, 'a' + 3, 'a' + 3, 259, 'a' + 3));
        assertArrayEquals(new int[] {260}, tokenizer.encode("aaa"));
        assertArrayEquals(new int[] {259, 260}, tokenizer.encode("aaaaa"));
        assertEquals("aaaaa", tokenizer.decode(tokenizer.encode("aaaaa")));
    }

    @Test
    void roundTripsSerializedVocabularyAndEncoding() throws IOException {
        ByteTokenizer original = ByteTokenizer.train(CORPUS, 512);
        ByteTokenizer restored = read(serialize(original));
        assertEquals(original.vocabSize(), restored.vocabSize());
        assertArrayEquals(serialize(original), serialize(restored));
        for (String text : CORPUS) {
            assertArrayEquals(original.encode(text), restored.encode(text));
            assertEquals(text, restored.decode(restored.encode(text)));
        }
    }

    @Test
    void ignoresOnlyValidSpecialTokensAndRejectsUnknownIds() {
        ByteTokenizer tokenizer = ByteTokenizer.train(CORPUS, 384);
        int[] word = tokenizer.encode("Luma");
        int[] withSpecials = new int[word.length + 3];
        withSpecials[0] = ByteTokenizer.BOS;
        System.arraycopy(word, 0, withSpecials, 1, word.length);
        withSpecials[word.length + 1] = ByteTokenizer.EOS;
        withSpecials[word.length + 2] = ByteTokenizer.PAD;
        assertEquals("Luma", tokenizer.decode(withSpecials));
        assertThrows(IllegalArgumentException.class, () -> tokenizer.decode(new int[] {-1}));
        assertThrows(IllegalArgumentException.class,
                () -> tokenizer.decode(new int[] {tokenizer.vocabSize()}));
    }

    @Test
    void replacesIncompleteGeneratedUtf8RatherThanFailing() {
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of(), 259);
        assertEquals("�", tokenizer.decode(new int[] {3 + 0xc3}));
    }

    @Test
    void rejectsEveryTruncatedSerialization() throws IOException {
        byte[] encoded = serialize(ByteTokenizer.train(CORPUS, 384));
        for (int length = 0; length < encoded.length; length++) {
            byte[] partial = Arrays.copyOf(encoded, length);
            assertThrows(IOException.class, () -> read(partial), "length=" + length);
        }
    }

    @Test
    void rejectsBadHeaderNegativeCountAndExcessiveMergeCount() throws IOException {
        byte[] badMagic = raw(0);
        badMagic[0] ^= 1;
        assertThrows(IOException.class, () -> read(badMagic));
        byte[] badVersion = raw(0);
        badVersion[7] = 2;
        assertThrows(IOException.class, () -> read(badVersion));
        assertThrows(IOException.class, () -> read(raw(-1)));
        assertThrows(IOException.class, () -> read(raw(8193)));
    }

    @Test
    void rejectsCyclesForwardReferencesSpecialsAndDuplicateRules() throws IOException {
        for (int unavailable : new int[] {-1, 0, 1, 2, 259, 260, Integer.MAX_VALUE}) {
            byte[] left = raw(1, unavailable, 100);
            byte[] right = raw(1, 100, unavailable);
            assertThrows(IOException.class, () -> read(left));
            assertThrows(IOException.class, () -> read(right));
        }
        assertThrows(IOException.class, () -> read(raw(2, 100, 101, 100, 101)));
    }

    @Test
    void rejectsExponentialByteExpansionBeforeAllocatingLargePieces() throws IOException {
        List<Integer> records = new ArrayList<>();
        int previous = 'a' + 3;
        for (int i = 0; i < 17; i++) {
            records.add(previous);
            records.add(previous);
            previous = 259 + i;
        }
        int[] pairs = records.stream().mapToInt(Integer::intValue).toArray();
        assertThrows(IOException.class, () -> read(raw(17, pairs)));
    }

    @Test
    void doesNotInventMergesForEmptyOrUniqueTrainingText() {
        assertEquals(259, ByteTokenizer.train(List.of("", ""), 384).vocabSize());
        assertEquals(259, ByteTokenizer.train(List.of("abc"), 384).vocabSize());
        assertThrows(IllegalArgumentException.class, () -> ByteTokenizer.train(CORPUS, 258));
        assertThrows(IllegalArgumentException.class, () -> ByteTokenizer.train(CORPUS, 8452));
    }

    private static byte[] serialize(ByteTokenizer tokenizer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tokenizer.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static ByteTokenizer read(byte[] bytes) throws IOException {
        return ByteTokenizer.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    private static byte[] raw(int count, int... pairs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x4c425045);
        output.writeInt(1);
        output.writeInt(count);
        for (int value : pairs) {
            output.writeInt(value);
        }
        return bytes.toByteArray();
    }
}
