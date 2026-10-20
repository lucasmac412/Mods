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
