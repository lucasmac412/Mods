package br.com.luma.nativeai;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Original lexical retrieval over authored training examples; never a language model. */
public final class LocalMemory {
    public static final double DEFAULT_THRESHOLD = 0.25;
    public static final String UNKNOWN = "Ainda não tenho informação confiável sobre essa pergunta. Peça mais detalhes ao jogador.";
    private static final Set<String> STOP = Set.of("a", "ao", "aos", "as", "o", "os", "um", "uma", "uns", "umas",
            "de", "da", "das", "do", "dos", "e", "em", "no", "na", "nos", "nas", "com", "para", "por",
            "que", "qual", "quais", "como", "quando", "onde", "porque", "porquanto", "se", "ou", "eu", "me",
            "meu", "minha", "meus", "minhas", "voce", "seu", "sua", "seus", "suas", "aqui", "ali", "isso",
            "isto", "esse", "essa", "este", "esta", "luma", "ajuda", "pode", "posso", "ser", "eai", "pra");
    private final List<Entry> entries;
    private final Map<String, Double> inverseFrequency;
    private final double unknownIdf;
    private final double threshold;
    private final double answerWeight;

    public record Match(String sourcePrompt, String information, double score, boolean found) { }
    private record Entry(DataSet.Example example, Map<String, Double> vector, double norm) { }

    private LocalMemory(List<DataSet.Example> examples, double threshold, double answerWeight) {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("Memory threshold must be between 0 and 1");
        if (!Double.isFinite(answerWeight) || answerWeight < 0 || answerWeight > 1)
            throw new IllegalArgumentException("Answer weight must be between 0 and 1");
        if (examples.isEmpty() || examples.size() > 10_000)
            throw new IllegalArgumentException("Memory needs 1 to 10000 training examples");
        this.threshold = threshold;
        this.answerWeight = answerWeight;
        List<Map<String, Double>> features = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (DataSet.Example example : examples) {
            Map<String, Double> row = features(example.prompt());
            if (answerWeight > 0) {
                // Contributions are linear after square-root TF: an answer-only term
                // receives exactly answerWeight times the same question term's TF.
                Map<String, Double> answer = features(example.reply());
                for (var feature : answer.entrySet()) {
                    double combined = Math.sqrt(row.getOrDefault(feature.getKey(), 0.0))
                            + answerWeight * Math.sqrt(feature.getValue());
                    row.put(feature.getKey(), combined * combined);
                }
            }
            features.add(row);
            row.keySet().forEach(feature -> documentFrequency.merge(feature, 1, Integer::sum));
        }
        inverseFrequency = new HashMap<>();
        documentFrequency.forEach((feature, frequency) -> inverseFrequency.put(feature,
                1 + Math.log((examples.size() + 1.0) / (frequency + 1.0))));
        unknownIdf = 1 + Math.log(examples.size() + 1.0);
        List<Entry> built = new ArrayList<>();
        for (int i = 0; i < examples.size(); i++) {
            Map<String, Double> vector = weight(features.get(i));
            built.add(new Entry(examples.get(i), Map.copyOf(vector), norm(vector)));
        }
        entries = List.copyOf(built);
    }

    public static LocalMemory load(Path trainingFile) throws IOException {
        return load(trainingFile, DEFAULT_THRESHOLD);
    }

    public static LocalMemory load(Path trainingFile, double threshold) throws IOException {
        return load(trainingFile, threshold, 0);
    }

    /** Answer features are drawn only from this authored training knowledge file. */
    public static LocalMemory load(Path trainingFile, double threshold, double answerWeight) throws IOException {
        return new LocalMemory(DataSet.read(trainingFile), threshold, answerWeight);
    }

    public static LocalMemory fromExamples(List<DataSet.Example> examples, double threshold) {
        return fromExamples(examples, threshold, 0);
    }

    public static LocalMemory fromExamples(List<DataSet.Example> examples, double threshold, double answerWeight) {
        return new LocalMemory(List.copyOf(examples), threshold, answerWeight);
    }

    public double threshold() { return threshold; }
    public double answerWeight() { return answerWeight; }
    public int size() { return entries.size(); }

    /** Returns only a lexical note. The caller must still generate and label any neural output. */
    public Match lookup(String question) {
        Objects.requireNonNull(question, "question");
        Map<String, Double> query = weight(features(question));
        double queryNorm = norm(query);
        double best = 0;
        Entry selected = null;
        if (queryNorm > 0) for (Entry entry : entries) {
            if (entry.norm == 0) continue;
            double dot = 0;
            for (var feature : query.entrySet()) dot += feature.getValue() * entry.vector.getOrDefault(feature.getKey(), 0.0);
            double similarity = Math.max(0, Math.min(1, dot / (queryNorm * entry.norm)));
            // Stable input order resolves equal scores, including augmented duplicates.
            if (similarity > best) { best = similarity; selected = entry; }
        }
        if (selected == null || best < threshold)
            return new Match("", UNKNOWN, best, false);
        return new Match(selected.example.prompt(), selected.example.reply(), best, true);
    }

    /** Single-line bounded prompt shared by preparation and the game runtime. */
    public static String format(String question, String information) {
        return "Pergunta: " + clean(question, 500) + " | Informação: " + clean(information, 400);
    }

    private static String clean(String text, int maximum) {
        Objects.requireNonNull(text, "text");
        String result = text.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        if (result.length() <= maximum) return result;
        int end = maximum;
        if (Character.isHighSurrogate(result.charAt(end - 1))) end--;
        return result.substring(0, end);
    }

    private static Map<String, Double> features(String text) {
        String normalized = Normalizer.normalize(clean(text, 500), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        Map<String, Double> result = new HashMap<>();
        for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty() || STOP.contains(word)) continue;
            result.merge("w:" + word, 1.0, Double::sum);
            if (word.length() < 3) continue;
            String bounded = "^" + word + "$";
            for (int n = 3; n <= 4; n++) for (int start = 0; start + n <= bounded.length(); start++)
                result.merge("c:" + bounded.substring(start, start + n), 0.25, Double::sum);
        }
        return result;
    }

    private Map<String, Double> weight(Map<String, Double> features) {
        Map<String, Double> result = new HashMap<>();
        features.forEach((feature, count) -> result.put(feature,
                Math.sqrt(count) * inverseFrequency.getOrDefault(feature, unknownIdf)));
        return result;
    }

    private static double norm(Map<String, Double> vector) {
        double squares = 0;
        for (double value : vector.values()) squares += value * value;
        return Math.sqrt(squares);
    }
}
