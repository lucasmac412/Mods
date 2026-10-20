package br.com.luma.nativeai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** CPU research CLI. Training uses random initialization, authored data and no external AI service. */
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception failure) {
            System.err.println("Falha: " + failure.getMessage());
            System.exit(2);
        }
    }
    static void run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help")) {
            System.out.println("""
                    IA generativa Luma — protótipo CPU, sem pesos de terceiros.
                    train <data-dir> <output-dir> [--steps 1000 --seconds 180 --context 128 --dim 32
                          --heads 4 --layers 1 --ff 64 --vocab 384 --batch 2 --seed 412 --lr 0.002]
                    evaluate <model.luma> <data-dir> <report.jsonl> [--tokens 48 --temperature 0.7 --top-k 20]
                    samples <model.luma> <prompts.tsv> <report.jsonl> [--tokens 48 --temperature 0.7 --top-k 20]
                    chat <model.luma> <pergunta entre aspas>
                    Este laboratório não demonstra capacidade comparável a modelos grandes.
                    """);
            return;
        }
        switch (args[0]) {
            case "train" -> train(args);
            case "evaluate" -> evaluate(args);
            case "samples" -> samples(args);
            case "chat" -> {
                if (args.length != 3) throw new IllegalArgumentException("Uso: chat <model.luma> <pergunta entre aspas>");
                System.out.println(TextGenerator.generate(ModelBundle.load(Path.of(args[1])), args[2], 64, 0.7, 20, 412));
            }
            default -> throw new IllegalArgumentException("Comando desconhecido. Use help.");
        }
    }
    private static void train(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Uso: train <data-dir> <output-dir> [opções]");
        Map<String, String> options = options(args, 3, List.of("steps", "seconds", "context", "dim", "heads", "layers", "ff", "vocab", "batch", "seed", "lr", "decay", "clip"));
        int steps = integer(options, "steps", 1000, 1, 100_000);
        int seconds = integer(options, "seconds", 180, 1, 86_400);
        int context = integer(options, "context", 128, 8, 512);
        int batch = integer(options, "batch", 2, 1, 32);
        long seed = Long.parseLong(options.getOrDefault("seed", "412"));
        Path data = Path.of(args[1]), output = Path.of(args[2]);
        List<DataSet.Example> train = DataSet.read(data.resolve("train.tsv"));
        List<DataSet.Example> validation = DataSet.read(data.resolve("validation.tsv"));
        List<DataSet.Example> test = DataSet.read(data.resolve("test.tsv"));
        DataSet.requireDisjoint(train, validation); DataSet.requireDisjoint(train, test); DataSet.requireDisjoint(validation, test);
        ByteTokenizer tokenizer = ByteTokenizer.train(train.stream().map(DataSet.Example::text).toList(), integer(options, "vocab", 384, 259, 8192));
        ModelConfig config = new ModelConfig(tokenizer.vocabSize(), context,
                integer(options, "dim", 32, 8, 512), integer(options, "heads", 4, 1, 16),
                integer(options, "layers", 1, 1, 8), integer(options, "ff", 64, 4, 2048));
        Transformer model = new Transformer(config, seed);
        double learningRate = decimal(options, "lr", 0.002), decay = decimal(options, "decay", 0.01), clip = decimal(options, "clip", 1.0);
        AdamW optimizer = new AdamW(learningRate, decay, clip);
        List<DataSet.Window> trainWindows = encode(train, tokenizer, context), valWindows = encode(validation, tokenizer, context);
        Metric initialTrain = score(model, trainWindows), initialValidation = score(model, valWindows);
        String sha = sha256(data.resolve("train.tsv"));
        Files.createDirectories(output);
        Path checkpoint = output.resolve("model.luma");
        Random random = new Random(seed + 1);
        long start = System.nanoTime();
        double bestLoss = Double.POSITIVE_INFINITY;
        int bestStep = 0, completed = 0;
        List<String> history = new ArrayList<>();
        for (int step = 1; step <= steps; step++) {
            int[][] inputs = new int[batch][], targets = new int[batch][];
            for (int i = 0; i < batch; i++) {
                DataSet.Window window = trainWindows.get(random.nextInt(trainWindows.size()));
                inputs[i] = window.input(); targets[i] = window.targets();
            }
            double loss = model.trainBatch(inputs, targets, optimizer);
            if (!Double.isFinite(loss)) throw new IOException("Loss não finita; o treinamento não foi validado.");
            completed = step;
            boolean timedOut = (System.nanoTime() - start) / 1e9 >= seconds;
            if (step == 1 || step % 100 == 0 || step == steps || timedOut) {
                Metric current = score(model, valWindows);
                if (current.loss < bestLoss) {
                    bestLoss = current.loss; bestStep = step;
                    new ModelBundle(tokenizer, model, sha).save(checkpoint);
                }
                String log = "{\"step\":" + step + ",\"batch_loss\":" + loss + ",\"validation_loss\":" + current.loss
                        + ",\"elapsed_seconds\":" + ((System.nanoTime() - start) / 1e9) + "}";
                history.add(log); System.out.println(log);
            }
            if (timedOut) break;
        }
        Files.write(output.resolve("history.jsonl"), history);
        ModelBundle selected = ModelBundle.load(checkpoint);
        Metric trainedTrain = score(selected.model(), trainWindows), trainedValidation = score(selected.model(), valWindows);
        String report = "{\n  \"experimental\": true,\n  \"comparable_to_large_llm\": false,\n  \"backend\": \"original-java-cpu\",\n"
                + "  \"gpu_used\": false,\n  \"seed\": " + seed + ",\n  \"parameters\": " + model.parameterCount()
                + ",\n  \"context\": " + context + ",\n  \"embedding\": " + config.embeddingSize() + ",\n  \"layers\": " + config.layers()
                + ",\n  \"vocab\": " + config.vocabSize() + ",\n  \"heads\": " + config.heads() + ",\n  \"feed_forward\": " + config.feedForwardSize()
                + ",\n  \"batch_size\": " + batch + ",\n  \"learning_rate\": " + learningRate + ",\n  \"weight_decay\": " + decay
                + ",\n  \"gradient_clip\": " + clip + ",\n  \"math_threads\": 1,\n  \"available_processors\": " + Runtime.getRuntime().availableProcessors()
                + ",\n  \"steps_requested\": " + steps + ",\n  \"steps_completed\": " + completed
                + ",\n  \"best_validation_step\": " + bestStep + ",\n  \"time_budget_seconds\": " + seconds
                + ",\n  \"elapsed_seconds\": " + ((System.nanoTime() - start) / 1e9) + ",\n  \"training_data_sha256\": " + quote(sha)
                + ",\n  \"validation_data_sha256\": " + quote(sha256(data.resolve("validation.tsv")))
                + ",\n  \"test_data_sha256\": " + quote(sha256(data.resolve("test.tsv")))
                + ",\n  \"initial_train\": " + initialTrain.json() + ",\n  \"initial_validation\": " + initialValidation.json()
                + ",\n  \"selected_train\": " + trainedTrain.json() + ",\n  \"selected_validation\": " + trainedValidation.json()
                + ",\n  \"test_evaluated\": false\n}\n";
        Files.writeString(output.resolve("training.json"), report);
        System.out.println("Checkpoint próprio salvo em " + checkpoint + "; relatório: " + output.resolve("training.json"));
    }
    private static void evaluate(String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("Uso: evaluate <model.luma> <data-dir> <report.jsonl> [opções]");
        Map<String, String> options = options(args, 4, List.of("tokens", "temperature", "top-k", "seed"));
        ModelBundle bundle = ModelBundle.load(Path.of(args[1]));
        Path data = Path.of(args[2]), report = Path.of(args[3]);
        List<DataSet.Example> train = DataSet.read(data.resolve("train.tsv")), test = DataSet.read(data.resolve("test.tsv"));
        DataSet.requireDisjoint(train, test);
        String actualSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(data.resolve("train.tsv"))));
        if (!actualSha.equals(bundle.trainingDataSha256())) throw new IOException("Os dados de treino não correspondem à proveniência do checkpoint.");
        int tokens = integer(options, "tokens", 48, 1, 128), topK = integer(options, "top-k", 20, 1, bundle.tokenizer().vocabSize());
        double temperature = decimal(options, "temperature", 0.7);
        long seed = Long.parseLong(options.getOrDefault("seed", "2026"));
        List<String> lines = new ArrayList<>();
        lines.add("{\"type\":\"metrics\",\"test\":" + score(bundle.model(), encode(test, bundle.tokenizer(), bundle.model().config().contextLength())).json()
                + ",\"max_new_tokens\":" + tokens + ",\"temperature\":" + temperature + ",\"top_k\":" + topK + ",\"seed\":" + seed + "}");
        for (int i = 0; i < test.size(); i++) {
            DataSet.Example example = test.get(i);
            String generated = TextGenerator.generate(bundle, example.prompt(), tokens, temperature, topK, seed + i);
            lines.add("{\"type\":\"generation\",\"prompt\":" + quote(example.prompt()) + ",\"reference\":" + quote(example.reply()) + ",\"generated\":" + quote(generated) + "}");
        }
        Path parent = report.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
        Files.write(report, lines);
        System.out.println("Todas as " + test.size() + " gerações de teste foram registradas em " + report);
    }
    private static List<DataSet.Window> encode(List<DataSet.Example> examples, ByteTokenizer tokenizer, int context) {
        return examples.stream().map(e -> e.encode(tokenizer).window(context)).toList();
    }
    private static void samples(String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("Uso: samples <model.luma> <prompts.tsv> <report.jsonl> [opções]");
        Map<String, String> options = options(args, 4, List.of("tokens", "temperature", "top-k", "seed"));
        ModelBundle bundle = ModelBundle.load(Path.of(args[1]));
        List<DataSet.Example> prompts = DataSet.read(Path.of(args[2]));
        int tokens = integer(options, "tokens", 48, 1, 128);
        int topK = integer(options, "top-k", 20, 1, bundle.tokenizer().vocabSize());
        double temperature = decimal(options, "temperature", 0.7);
        if (temperature < 0) throw new IllegalArgumentException("Temperatura deve ser não negativa.");
        long seed = Long.parseLong(options.getOrDefault("seed", "2026"));
        List<String> lines = new ArrayList<>();
        lines.add("{\"type\":\"sampling\",\"model_sha256\":" + quote(sha256(Path.of(args[1])))
                + ",\"prompts_sha256\":" + quote(sha256(Path.of(args[2]))) + ",\"max_new_tokens\":" + tokens
                + ",\"temperature\":" + temperature + ",\"top_k\":" + topK + ",\"seed\":" + seed + "}");
        for (int i = 0; i < prompts.size(); i++) {
            DataSet.Example row = prompts.get(i);
            String generated = TextGenerator.generate(bundle, row.reply(), tokens, temperature, topK, seed + i);
            lines.add("{\"type\":\"generation\",\"id\":" + quote(row.prompt()) + ",\"prompt\":" + quote(row.reply())
                    + ",\"generated\":" + quote(generated) + "}");
        }
        Path report = Path.of(args[3]);
        Path parent = report.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
        Files.write(report, lines);
        System.out.println("Todas as " + prompts.size() + " amostras foram registradas em " + report);
    }
    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private record Metric(double loss, double perplexity, int examples, long scoredTokens, int truncatedExamples) {
        String json() { return "{\"loss\":" + loss + ",\"perplexity\":" + perplexity + ",\"examples\":" + examples
                + ",\"scored_response_tokens\":" + scoredTokens + ",\"truncated_examples\":" + truncatedExamples + "}"; }
    }
    private static Metric score(Transformer model, List<DataSet.Window> windows) {
        double total = 0; long tokens = 0; int truncated = 0;
        for (DataSet.Window window : windows) {
            total += model.loss(new int[][]{window.input()}, new int[][]{window.targets()}) * window.scoredTokens();
            tokens += window.scoredTokens(); if (window.truncated()) truncated++;
        }
        double loss = total / tokens;
        if (!Double.isFinite(loss)) throw new IllegalStateException("Avaliação não finita.");
        return new Metric(loss, Math.exp(loss), windows.size(), tokens, truncated);
    }
    private static Map<String, String> options(String[] args, int start, List<String> allowed) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = start; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("Opção incompleta.");
            String key = args[i].substring(2);
            if (!allowed.contains(key) || values.put(key, args[i + 1]) != null) throw new IllegalArgumentException("Opção inválida ou repetida: " + key);
        }
        return values;
    }
    private static int integer(Map<String, String> values, String key, int fallback, int min, int max) {
        int number = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        if (number < min || number > max) throw new IllegalArgumentException("Valor fora do intervalo: " + key);
        return number;
    }
    private static double decimal(Map<String, String> values, String key, double fallback) {
        double value = Double.parseDouble(values.getOrDefault(key, Double.toString(fallback)));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Valor não finito: " + key);
        return value;
    }
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
