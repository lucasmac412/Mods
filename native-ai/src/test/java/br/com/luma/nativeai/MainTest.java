package br.com.luma.nativeai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path temporary;

    @Test
    void completeTrainingAndEvaluationAreReproducibleAndHeldOutRepliesCannotTrainWeights() throws Exception {
        Path data = dataDirectory();
        Path first = temporary.resolve("first-run"), second = temporary.resolve("second-run");
        trainTwoSteps(data, first);
        byte[] checkpoint = Files.readAllBytes(first.resolve("model.luma"));
        ModelBundle original = ModelBundle.load(first.resolve("model.luma"));
        assertEquals(sha(data.resolve("train.tsv")), original.trainingDataSha256());
        String manifest = Files.readString(first.resolve("training.json"));
        assertTrue(manifest.contains("\"steps_completed\": 2"));
        assertTrue(manifest.contains("\"gpu_used\": false"));
        assertTrue(manifest.contains("\"comparable_to_large_llm\": false"));
        assertTrue(manifest.contains("\"test_evaluated\": false"),
                "Training must leave the held-out test untouched until a model has been selected");
        assertTrue(manifest.contains("\"schedule\": \"constant\""));
        assertTrue(manifest.contains("\"warmup_steps\": 0"));
        assertTrue(manifest.contains("\"snapshot_interval\": 0"));
        assertTrue(manifest.contains("\"objective\": \"response\""));
        assertTrue(manifest.contains("\"initialization\": \"random\""));
        assertTrue(manifest.contains("\"initial_checkpoint_sha256\": null"));
        assertTrue(manifest.contains("\"inherited_training_data_sha256\": null"));
        assertTrue(manifest.contains("\"optimizer_state_restored\": false"));
        assertTrue(Files.isRegularFile(first.resolve("last.luma")));
        assertEquals(2, Files.readAllLines(first.resolve("history.jsonl")).size());

        // The same test prompt remains disjoint, while its reference answer changes completely.
        Files.writeString(data.resolve("test.tsv"), "O que é redstone?\tUma referência totalmente diferente para a avaliação.\n");
        trainTwoSteps(data, second);
        assertArrayEquals(checkpoint, Files.readAllBytes(second.resolve("model.luma")),
                "Changing only held-out reference replies must not affect tokenizer, learned weights or checkpoint selection");

        Path evaluation = temporary.resolve("reports/evaluation.jsonl");
        Main.run(new String[]{"evaluate", first.resolve("model.luma").toString(), data.toString(),
                evaluation.toString(), "--tokens", "4", "--temperature", "0", "--top-k", "1"});
        List<String> report = Files.readAllLines(evaluation);
        assertEquals(2, report.size(), "Metrics plus every held-out prompt must be written");
        assertTrue(report.getFirst().contains("\"type\":\"metrics\""));
        assertTrue(report.get(1).contains("\"prompt\":\"O que é redstone?\""));
        assertTrue(report.get(1).contains("Uma referência totalmente diferente para a avaliação."));
        assertTrue(report.get(1).contains("\"generated\":"));
    }

    @Test
    void invalidOrRepeatedCliOptionsDoNotCreateAnOutputDirectory() throws IOException {
        Path output = temporary.resolve("must-not-be-created");
        Path absentData = temporary.resolve("absent-data");
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"train", absentData.toString(),
                output.toString(), "--unknown", "1"}));
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"train", absentData.toString(),
                output.toString(), "--steps", "1", "--steps", "2"}));
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"train", absentData.toString(),
                output.toString(), "--steps"}));
        assertFalse(Files.exists(output));
    }

    @Test
    void learningRateScheduleWarmsUpThenDecaysToItsDeclaredFloor() {
        assertEquals(0.002, Main.learningRateForStep(0.002, 1, 10, 0, "constant"));
        assertEquals(0.002, Main.learningRateForStep(0.002, 10, 10, 0, "constant"));
        assertEquals(0.001 / 3, Main.learningRateForStep(0.001, 1, 10, 3, "constant"), 1e-16);
        assertEquals(0.002 / 3, Main.learningRateForStep(0.001, 2, 10, 3, "cosine"), 1e-16);
        assertEquals(0.001, Main.learningRateForStep(0.001, 3, 10, 3, "cosine"), 1e-16);
        double previous = 0.001;
        for (int step = 4; step <= 10; step++) {
            double current = Main.learningRateForStep(0.001, step, 10, 3, "cosine");
            assertTrue(current < previous);
            previous = current;
        }
        assertEquals(0.0001, previous, 1e-16);
        assertEquals(0.001, Main.learningRateForStep(0.001, 1, 10, 0, "cosine"), 1e-16);
        assertEquals(0.0001, Main.learningRateForStep(0.001, 10, 10, 0, "cosine"), 1e-16);
    }

    @Test
    void scheduledTrainingWritesRealSnapshotsAndFinalWeightsWithFinalMetrics() throws Exception {
        Path data = dataDirectory(), output = temporary.resolve("scheduled");
        Main.run(new String[]{"train", data.toString(), output.toString(), "--steps", "4", "--seconds", "60",
                "--context", "96", "--dim", "8", "--heads", "2", "--layers", "1", "--ff", "16",
                "--vocab", "264", "--batch", "1", "--seed", "918", "--lr", "0.001",
                "--warmup", "1", "--schedule", "cosine", "--save-every", "2", "--validation-every", "3"});
        for (String name : List.of("model.luma", "last.luma", "step-000002.luma", "step-000004.luma")) {
            ModelBundle bundle = ModelBundle.load(output.resolve(name));
            assertEquals(sha(data.resolve("train.tsv")), bundle.trainingDataSha256());
        }
        assertFalse(Files.exists(output.resolve("step-000001.luma")));
        assertArrayEquals(Files.readAllBytes(output.resolve("step-000004.luma")), Files.readAllBytes(output.resolve("last.luma")));
        List<String> history = Files.readAllLines(output.resolve("history.jsonl"));
        assertEquals(4, history.size(), "Validation at 1,3,4 plus an independent snapshot at 2 must all be recorded");
        assertTrue(history.get(1).contains("\"type\":\"checkpoint\""));
        assertTrue(history.get(1).contains("step-000002.luma"));
        assertTrue(history.get(3).contains("\"best_validation_step\":"));
        String report = Files.readString(output.resolve("training.json"));
        assertTrue(report.contains("\"schedule\": \"cosine\""));
        assertTrue(report.contains("\"warmup_steps\": 1"));
        assertTrue(report.contains("\"final_learning_rate\": 1.0E-4"));
        assertTrue(report.contains("\"validation_interval\": 3"));
        assertTrue(report.contains("\"last_train\":"));
        assertTrue(report.contains("\"last_validation\":"));
        assertTrue(report.contains("\"test_evaluated\": false"));
    }

    @Test
    void invalidScheduleOptionsFailBeforeReadingDataOrCreatingOutputs() {
        Path data = temporary.resolve("absent"), output = temporary.resolve("invalid-schedule");
        for (String[] options : List.of(new String[]{"--schedule", "unknown"},
                new String[]{"--steps", "2", "--warmup", "3"},
                new String[]{"--steps", "2", "--warmup", "2", "--schedule", "cosine"},
                new String[]{"--validation-every", "0"}, new String[]{"--save-every", "-1"},
                new String[]{"--objective", "all-splits"})) {
            String[] args = new String[3 + options.length];
            args[0] = "train"; args[1] = data.toString(); args[2] = output.toString();
            System.arraycopy(options, 0, args, 3, options.length);
            assertThrows(IllegalArgumentException.class, () -> Main.run(args));
        }
        assertFalse(Files.exists(output));
    }

    @Test
    void fullObjectiveSupervisesPromptOnlyInTrainingAndKeepsReportedResponseMetricsComparable() throws Exception {
        Path data = dataDirectory(), response = temporary.resolve("response-objective"), full = temporary.resolve("full-objective");
        trainTwoSteps(data, response);
        Main.run(new String[]{"train", data.toString(), full.toString(), "--steps", "2", "--seconds", "60",
                "--context", "96", "--dim", "8", "--heads", "2", "--layers", "1", "--ff", "16",
                "--vocab", "264", "--batch", "1", "--seed", "918", "--lr", "0.001", "--objective", "full"});
        String responseReport = Files.readString(response.resolve("training.json"));
        String fullReport = Files.readString(full.resolve("training.json"));
        assertTrue(fullReport.contains("\"objective\": \"full\""));
        // Identical initial response scoring despite different targets used by the optimizer.
        String initialTrain = responseReport.lines().filter(line -> line.contains("\"initial_train\":")).findFirst().orElseThrow();
        String initialValidation = responseReport.lines().filter(line -> line.contains("\"initial_validation\":")).findFirst().orElseThrow();
        assertTrue(fullReport.contains(initialTrain));
        assertTrue(fullReport.contains(initialValidation));
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(response.resolve("last.luma")), Files.readAllBytes(full.resolve("last.luma"))));
        assertTrue(fullReport.contains("\"test_evaluated\": false"));
    }

    @Test
    void invalidArchitectureAndDataLeakageAreRejectedBeforeCheckpointCreation() throws IOException {
        Path data = dataDirectory(), output = temporary.resolve("rejected-model");
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"train", data.toString(), output.toString(),
                "--dim", "10", "--heads", "4", "--steps", "1"}));
        assertFalse(Files.exists(output));
        Files.writeString(data.resolve("validation.tsv"), "OLA LUMA!\tOutra resposta, ainda vazamento.\n");
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"train", data.toString(), output.toString(),
                "--steps", "1"}));
        assertFalse(Files.exists(output));
    }

    @Test
    void evaluationRefusesTrainingDataWithADifferentFingerprintWithoutWritingResults() throws Exception {
        Path data = dataDirectory();
        ByteTokenizer tokenizer = ByteTokenizer.train(DataSet.read(data.resolve("train.tsv")).stream()
                .map(DataSet.Example::text).toList(), 264);
        Transformer model = new Transformer(new ModelConfig(tokenizer.vocabSize(), 64, 8, 2, 1, 16), 51);
        Path checkpoint = temporary.resolve("model.luma"), report = temporary.resolve("should-not-exist.jsonl");
        new ModelBundle(tokenizer, model, sha(data.resolve("train.tsv"))).save(checkpoint);
        Files.writeString(data.resolve("train.tsv"), Files.readString(data.resolve("train.tsv")) + "# corpus modificado\n");
        IOException failure = assertThrows(IOException.class, () -> Main.run(new String[]{"evaluate", checkpoint.toString(),
                data.toString(), report.toString()}));
        assertTrue(failure.getMessage().contains("proveniência"));
        assertFalse(Files.exists(report));
    }

    @Test
    void validationEvaluationUsesOnlyTheChosenSplitWithoutOpeningTestData() throws Exception {
        Path data = dataDirectory();
        ByteTokenizer tokenizer = ByteTokenizer.train(DataSet.read(data.resolve("train.tsv")).stream()
                .map(DataSet.Example::text).toList(), 264);
        ModelBundle bundle = new ModelBundle(tokenizer, new Transformer(new ModelConfig(tokenizer.vocabSize(), 64, 8, 2, 1, 16), 51),
                sha(data.resolve("train.tsv")));
        Path checkpoint = temporary.resolve("validation-model.luma"), report = temporary.resolve("validation.jsonl");
        bundle.save(checkpoint);
        Files.delete(data.resolve("test.tsv"));
        Main.run(new String[]{"evaluate", checkpoint.toString(), data.toString(), report.toString(),
                "--split", "validation", "--tokens", "4", "--temperature", "0"});
        List<String> lines = Files.readAllLines(report);
        assertEquals(2, lines.size());
        assertTrue(lines.getFirst().contains("\"split\":\"validation\""));
        assertTrue(lines.getFirst().contains("\"validation\":{"));
        assertFalse(lines.getFirst().contains("\"test\":"));
        assertTrue(lines.get(1).contains("Como vamos?"));
        assertFalse(lines.get(1).contains("redstone"));
        assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{"evaluate", checkpoint.toString(),
                data.toString(), report.toString(), "--split", "train"}));
    }

    @Test
    void samplesUseEveryPromptInOrderAndMatchTheActualGenerator() throws Exception {
        Path prompts = temporary.resolve("prompts.tsv"), checkpoint = temporary.resolve("samples.luma");
        Path report = temporary.resolve("samples.jsonl");
        Files.writeString(prompts, "primeira\tO que faremos?\nsegunda\tComo funciona?\n");
        ByteTokenizer tokenizer = ByteTokenizer.train(List.of("Jogador: Oi!\\nLuma: Olá!"), 264);
        ModelBundle bundle = new ModelBundle(tokenizer, new Transformer(new ModelConfig(tokenizer.vocabSize(), 16, 8, 2, 1, 16), 51), "a".repeat(64));
        bundle.save(checkpoint);
        Main.run(new String[]{"samples", checkpoint.toString(), prompts.toString(), report.toString(), "--tokens", "4", "--seed", "99"});
        List<String> lines = Files.readAllLines(report);
        assertEquals(3, lines.size());
        assertTrue(lines.getFirst().contains(sha(checkpoint)));
        assertTrue(lines.get(1).contains("\"id\":\"primeira\""));
        assertTrue(lines.get(1).contains(Main.quote(TextGenerator.generate(bundle, "O que faremos?", 4, 0.7, 20, 99))));
        assertTrue(lines.get(2).contains("\"id\":\"segunda\""));
        assertTrue(lines.get(2).contains(Main.quote(TextGenerator.generate(bundle, "Como funciona?", 4, 0.7, 20, 100))));
    }

    @Test
    void checkpointInitializationKeepsTokenizerWeightsAndArchitectureButUsesANewOptimizerAndCorpus() throws Exception {
        Path data = dataDirectory(), checkpoint = temporary.resolve("initial.luma"), output = temporary.resolve("warm-start");
        ModelBundle original = trainedFixture(data, checkpoint);
        String inheritedSha = original.trainingDataSha256(), checkpointSha = sha(checkpoint);
        byte[] originalBytes = Files.readAllBytes(checkpoint);
        Files.writeString(data.resolve("train.tsv"), "Uma nova pergunta sobre plantas?\tVeja a luz e a água.\n");
        DataSet.Window window = DataSet.read(data.resolve("train.tsv")).getFirst()
                .encode(original.tokenizer()).window(original.model().config().contextLength());
        double expectedInitialLoss = original.model().loss(new int[][]{window.input()}, new int[][]{window.targets()});
        ModelBundle expected = ModelBundle.load(checkpoint);
        expected.model().trainBatch(new int[][]{window.input()}, new int[][]{window.targets()}, new AdamW(0.001, 0.01, 1));

        // Omitted architecture flags must inherit the checkpoint rather than CLI defaults.
        Main.run(new String[]{"train", data.toString(), output.toString(), "--init", checkpoint.toString(),
                "--steps", "1", "--seconds", "30", "--batch", "1", "--lr", "0.001", "--seed", "999"});
        ModelBundle actual = ModelBundle.load(output.resolve("last.luma"));
        assertEquals(original.model().config(), actual.model().config());
        assertArrayEquals(original.tokenizer().encode("Árvore, madeira e 😀"), actual.tokenizer().encode("Árvore, madeira e 😀"));
        assertArrayEquals(expected.model().nextLogits(new int[]{ByteTokenizer.BOS}),
                actual.model().nextLogits(new int[]{ByteTokenizer.BOS}),
                "One warm-start step must equal a direct update with freshly initialized AdamW moments");
        assertArrayEquals(originalBytes, Files.readAllBytes(checkpoint), "Initialization does not overwrite its source checkpoint");
        assertEquals(sha(data.resolve("train.tsv")), actual.trainingDataSha256());
        assertNotEquals(inheritedSha, actual.trainingDataSha256());
        String report = Files.readString(output.resolve("training.json"));
        assertTrue(report.contains("\"initialization\": \"checkpoint\""));
        assertTrue(report.contains("\"initial_checkpoint_sha256\": " + Main.quote(checkpointSha)));
        assertTrue(report.contains("\"inherited_training_data_sha256\": " + Main.quote(inheritedSha)));
        assertTrue(report.contains("\"optimizer_state_restored\": false"));
        String initialMetric = report.lines().filter(line -> line.contains("\"initial_train\":")).findFirst().orElseThrow();
        double actualInitialLoss = Double.parseDouble(initialMetric.split("\"loss\":", 2)[1].split(",", 2)[0]);
        assertEquals(expectedInitialLoss, actualInitialLoss, 1e-12,
                "Initial metrics must score the inherited weights before any update, using the new corpus");
        assertTrue(report.contains("\"test_evaluated\": false"));
    }

    @Test
    void initializationAcceptsExplicitMatchingDimensionsAndRejectsEveryDisagreementBeforeOutputs() throws Exception {
        Path data = dataDirectory(), checkpoint = temporary.resolve("dimension-initial.luma");
        ModelBundle original = trainedFixture(data, checkpoint);
        ModelConfig config = original.model().config();
        String[] names = {"context", "dim", "heads", "layers", "ff", "vocab"};
        int[] values = {config.contextLength(), config.embeddingSize(), config.heads(), config.layers(),
                config.feedForwardSize(), config.vocabSize()};
        List<String> matching = new java.util.ArrayList<>(List.of("train", data.toString(), temporary.resolve("matching").toString(),
                "--init", checkpoint.toString(), "--steps", "1", "--seconds", "30", "--batch", "1"));
        for (int i = 0; i < names.length; i++) {
            matching.add("--" + names[i]); matching.add(Integer.toString(values[i]));
            Path rejected = temporary.resolve("rejected-" + names[i]);
            String flag = "--" + names[i];
            int disagreement = values[i] + 1;
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> Main.run(new String[]{
                    "train", data.toString(), rejected.toString(), "--init", checkpoint.toString(), "--steps", "1",
                    flag, Integer.toString(disagreement)}));
            assertTrue(failure.getMessage().contains(flag));
            assertTrue(failure.getMessage().contains("checkpoint"));
            assertFalse(Files.exists(rejected));
        }
        Main.run(matching.toArray(String[]::new));
        assertEquals(config, ModelBundle.load(temporary.resolve("matching/last.luma")).model().config());
    }

    @Test
    void invalidInitializationCheckpointDoesNotCreateTrainingOutputs() throws Exception {
        Path data = dataDirectory(), checkpoint = temporary.resolve("invalid.luma"), output = temporary.resolve("invalid-init");
        Files.writeString(checkpoint, "This is not an original model bundle.");
        assertThrows(IOException.class, () -> Main.run(new String[]{"train", data.toString(), output.toString(),
                "--init", checkpoint.toString(), "--steps", "1"}));
        assertFalse(Files.exists(output));
    }

    private static ModelBundle trainedFixture(Path data, Path checkpoint) throws Exception {
        List<DataSet.Example> examples = DataSet.read(data.resolve("train.tsv"));
        ByteTokenizer tokenizer = ByteTokenizer.train(examples.stream().map(DataSet.Example::text).toList(), 270);
        Transformer model = new Transformer(new ModelConfig(tokenizer.vocabSize(), 64, 8, 2, 1, 16), 72);
        DataSet.Window window = examples.getFirst().encode(tokenizer).window(64);
        AdamW optimizer = new AdamW(0.01, 0, 1);
        for (int step = 0; step < 8; step++) model.trainBatch(new int[][]{window.input()}, new int[][]{window.targets()}, optimizer);
        ModelBundle bundle = new ModelBundle(tokenizer, model, sha(data.resolve("train.tsv")));
        bundle.save(checkpoint);
        return bundle;
    }

    private Path dataDirectory() throws IOException {
        Path data = Files.createTempDirectory(temporary, "data-");
        Files.writeString(data.resolve("train.tsv"), "Olá, Luma!\tOi!\nPreciso de madeira?\tUse um machado.\n");
        Files.writeString(data.resolve("validation.tsv"), "Como vamos?\tVamos juntos.\n");
        Files.writeString(data.resolve("test.tsv"), "O que é redstone?\tServe para circuitos.\n");
        return data;
    }

    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static void trainTwoSteps(Path data, Path output) throws Exception {
        Main.run(new String[]{"train", data.toString(), output.toString(), "--steps", "2", "--seconds", "60",
                "--context", "96", "--dim", "8", "--heads", "2", "--layers", "1", "--ff", "16",
                "--vocab", "264", "--batch", "1", "--seed", "918", "--lr", "0.001"});
    }
}
