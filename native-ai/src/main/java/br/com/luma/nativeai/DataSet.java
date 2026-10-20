package br.com.luma.nativeai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Authored TSV conversations; no network access and no imported model or corpus. */
public final class DataSet {
    private DataSet() {}
    public record Example(String prompt, String reply) {
        public Example {
            if (prompt == null || reply == null || prompt.isBlank() || reply.isBlank()
                    || prompt.length() > 1000 || reply.length() > 1000
                    || prompt.matches("(?s).*[\\p{Cntrl}].*") || reply.matches("(?s).*[\\p{Cntrl}].*")) {
                throw new IllegalArgumentException("Cada exemplo precisa de pergunta e resposta curtas em uma linha.");
            }
        }
        public String text() { return prefix(prompt) + reply + "\n"; }
        public Encoded encode(ByteTokenizer tokenizer) {
            int[] question = tokenizer.encode(prefix(prompt)), answer = tokenizer.encode(reply);
            int[] tokens = new int[question.length + answer.length + 2];
            tokens[0] = ByteTokenizer.BOS;
            System.arraycopy(question, 0, tokens, 1, question.length);
            System.arraycopy(answer, 0, tokens, 1 + question.length, answer.length);
            tokens[tokens.length - 1] = ByteTokenizer.EOS;
            return new Encoded(tokens, 1 + question.length);
        }
    }
    public record Window(int[] input, int[] targets, int scoredTokens, boolean truncated) {}
    public record Encoded(int[] tokens, int responseStart) {
        public Window window(int contextLength) {
            int start = Math.max(0, tokens.length - contextLength - 1);
            int length = tokens.length - start - 1;
            int[] input = Arrays.copyOfRange(tokens, start, start + length);
            int[] targets = new int[length];
            int count = 0;
            for (int i = 0; i < length; i++) {
                int targetIndex = start + i + 1;
                targets[i] = targetIndex >= responseStart ? tokens[targetIndex] : -1;
                if (targets[i] >= 0) count++;
            }
            return new Window(input, targets, count, start > 0);
        }
    }
    public static String prefix(String prompt) { return "Jogador: " + prompt + "\nLuma: "; }
    public static List<Example> read(Path file) throws IOException {
        if (Files.size(file) > 20 * 1024 * 1024) throw new IOException("Corpus maior que o limite de 20 MiB do protótipo.");
        List<Example> examples = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length != 2) throw new IOException("TSV inválido em " + file.getFileName() + ":" + lineNumber);
            Example example = new Example(parts[0].strip(), parts[1].strip());
            if (!seen.add(key(example.prompt()))) throw new IOException("Pergunta duplicada em " + file.getFileName() + ":" + lineNumber);
            examples.add(example);
            if (examples.size() > 100_000) throw new IOException("Exemplos demais para este protótipo em memória.");
        }
        if (examples.isEmpty()) throw new IOException("Corpus sem exemplos: " + file.getFileName());
        return List.copyOf(examples);
    }
    public static void requireDisjoint(List<Example> first, List<Example> second) {
        Set<String> keys = new HashSet<>();
        first.forEach(e -> keys.add(key(e.prompt())));
        for (Example example : second) if (keys.contains(key(example.prompt()))) {
            throw new IllegalArgumentException("As divisões de dados contêm uma mesma pergunta normalizada.");
        }
    }
    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
